package br.com.meusgastos

/**
 * Resultado da leitura de um gasto.
 * cents = valor de UMA parcela (ou o valor da compra, se for à vista).
 * parcelas = em quantas vezes (1 = à vista).
 * parcelaAtual > 0 quando o banco avisa "parcela 3/10" (lança só essa parcela).
 * firstExtra = centavos que sobram na divisão e entram na 1ª parcela.
 */
data class Parsed(
    val cents: Long,
    val merchant: String,
    val parcelas: Int = 1,
    val parcelaAtual: Int = 0,
    val firstExtra: Long = 0,
    val pay: String = "",
    val final4: String = "",
)

/** Resultado da leitura de uma entrada de dinheiro (Pix recebido, depósito, salário). */
data class ParsedIncome(
    val cents: Long,
    val desc: String,
    val kind: String,
    /** Quem enviou (nome) e CNPJ, quando a notificação informa. */
    val payerName: String = "",
    val cnpj: String = "",
) {
    /** Identificador da fonte: CNPJ (só números) ou o nome. */
    val payerKey: String get() = if (cnpj.isNotBlank()) cnpj.filter { it.isDigit() } else Categorizer.norm(payerName).trim()
}

/**
 * Lê notificações de bancos brasileiros de forma genérica.
 * Gastos:   "Compra de R$ 25,90 APROVADA em IFOOD"
 *           "Compra de R$ 300,00 em 3x em MAGAZINE LUIZA"   -> 3 parcelas de R$ 100,00
 *           "Compra em 10x de R$ 50,00 em LOJA X"           -> 10 parcelas de R$ 50,00
 *           "Parcela 3/10 de R$ 50,00 LOJA X"               -> só a parcela 3
 * Entradas: "Você recebeu um Pix de R$ 150,00 de MARIA"
 */
object Parser {

    // Aceita 1.234,56 · 25,90 · 25.90 · 5.000 · 25
    private const val MONEY = """[0-9]{1,3}(?:\.[0-9]{3})+(?:,[0-9]{2})?|[0-9]+[.,][0-9]{2}(?![0-9])|[0-9]+"""
    private val money = Regex("""R\$\s*($MONEY)""")

    /** Palavras que indicam que é um GASTO. */
    private val spendWords = listOf(
        "compra", "pagamento", "pix enviado", "enviou", "transferiu", "debito", "débito", "debitad", "saque",
        "transferencia enviada", "transferência enviada", "transferencia realizada", "transferência realizada",
        "ted enviad", "doc enviad", "pagou", "pix realizado", "pix feito", "parcela", "boleto pago", "conta paga",
        "recarga", "tarifa", "anuidade", "assinatura", "transação", "transacao", "gastou",
    )

    /** Avisos que NÃO são movimentação: propaganda, tentativa que falhou, lembrete, estorno, fatura. */
    private val noise = listOf(
        "fatura", "emprestimo", "empréstimo", "oferta", "agendad", "cancelad", "negad", "recusad",
        "não aprovad", "nao aprovad", "não autorizad", "nao autorizad", "não realizad", "nao realizad",
        "não concluíd", "nao concluid", "não foi", "nao foi", "cashback", "estorn", "devolu", "devolvid", "reembols",
        "vence ", "vence.", "a vencer", "vencendo", "lembrete", "código de verifica", "codigo de verifica", "token",
        "ganhe", "cupom", "simul", "pré-aprovad", "pre-aprovad", "aproveite", "contrate", "tentativa", "bloquead",
        "pendente", "aguardando", "saldo insuficiente", "aplicação", "aplicacao", "resgate", "investi",
    )

    /** Parcela de empréstimo/financiamento paga ("parcela 3/24 do empréstimo debitada"). */
    private val loanRx = Regex(
        """(?i)(?:(?:parcela|presta[çc][ãa]o).{0,50}(?:empr[ée]stimo|financiamento|consignado)|(?:empr[ée]stimo|financiamento|consignado).{0,50}(?:parcela|presta[çc][ãa]o))"""
    )
    private val loanPaidRx = Regex("""(?i)debitad|\bpag[ao]\b|quitad|realizad|descontad|efetuad""")

    /** Palavras de ENTRADA: se aparecerem, não é gasto. */
    private val incomingHints = listOf("recebeu", "recebid", "deposito", "depósito", "creditad")

    // --- parcelas ---
    private val parcelaRx = Regex("""(?i)parcela\s*(\d{1,2})\s*(?:/|de)\s*(\d{1,2})""")
    private val vezesDeRx = Regex("""(?i)(\d{1,2})\s*(?:x|vezes|parcelas)\s*de\s*R\$\s*($MONEY)""")
    private val emVezesRx = Regex("""(?i)(?:\bem\s*|\s)(\d{1,2})\s*(?:x\b|vezes\b|parcelas\b)""")

    // --- estabelecimento ---
    private val afterRx = Regex(
        """(?i)\b(?:em|no|na|para|p/|a favor de|ao|a)\s+(?!dia\b|data\b|\d|cart|seu\b|sua\b|vista\b|prazo\b|partir\b|mais\b|menos\b|cada\b)(.+)"""
    )
    /** "Compra aprovada: PADARIA X R$ 20,00" */
    private val colonRx = Regex("""[:\-–]\s*([^:\-–\n]{2,40}?)\s*[:\-–]?\s*$""")
    /** "Débito automático de R$ 99,90 realizado - CLARO S.A." */
    private val trailingRx = Regex("""[-–:]\s+(\p{Lu}[\p{L}\p{N} .&*']{1,38}?)\.?\s*$""")
    private val genericRx = Regex("""(?i)compra|valor|cart[ãa]o|aprovad|pagamento|pix|d[ée]bito|cr[ée]dito|transa|realizad""")
    private val beforeRx = Regex(
        """(?is)^.*\b(?:em|no|na|para)\s+(?!valor\b|o valor\b|cart|\d)([^.,;\n]+?)(?:\s+(?:no valor|com valor|valor|de|por))*\s*$"""
    )
    private val leadingRx = Regex("""^\s*[:\-]?\s*(\p{Lu}.+)""")
    private val cutRx = Regex(
        """(?i)\s*(?:\.(?=\s|$)|[;,\n]| às | as | - | — | no dia| na data| no cart| no cr[eé]dito| no d[eé]bito| cart[ãa]o| final | aprovad| em \d| dia \d| via | no valor| com valor| \d{1,2}\s*x\b| parcelad| de R\$).*$""",
        RegexOption.DOT_MATCHES_ALL
    )

    // --- final do cartão: "final 2332", "cartão final: 2332", "terminado em 2332", "****2332", "cartão 2332"
    private val finalRx = Regex(
        """(?i)(?:\bfinal|terminad[oa] em|cart[ãa]o(?:\s+de\s+cr[ée]dito|\s+de\s+d[ée]bito)?)\s*:?\s*(?:n[º°o.]*\s*)?[*•xX]*\s*(\d{4})(?!\d)"""
    )
    private val maskRx = Regex("""(?:\*{2,}|•{2,}|[xX]{4,})\s*(\d{4})(?!\d)""")

    /** Últimos 4 números do cartão citados na notificação (ou ""). */
    fun finalCartao(text: String): String =
        finalRx.find(text)?.groupValues?.get(1) ?: maskRx.find(text)?.groupValues?.get(1) ?: ""

    private val decRx = Regex("""[.,]([0-9]{2})$""")

    private fun cents(s: String): Long? {
        val t = s.trim()
        val dec = decRx.find(t)
        return if (dec != null) (t.dropLast(3).replace(".", "").replace(",", "") + dec.groupValues[1]).toLongOrNull()
        else t.replace(".", "").toLongOrNull()?.times(100)
    }

    fun parse(title: String, text: String): Parsed? {
        val full = "$title $text".trim()
        val low = full.lowercase()
        val emprestimo = loanRx.containsMatchIn(full) && loanPaidRx.containsMatchIn(full)
        val ruido = if (emprestimo) noise.filter { !it.startsWith("empr") } else noise
        if (ruido.any { low.contains(it) }) return null
        if (incomingHints.any { low.contains(it) }) return null
        if (spendWords.none { low.contains(it) }) return null

        val m = money.find(full) ?: return null
        val first = cents(m.groupValues[1]) ?: return null
        if (first <= 0) return null

        // Parcelas
        var valor = first
        var parcelas = 1
        var atual = 0
        var extra = 0L
        val pr = parcelaRx.find(full)
        val vd = vezesDeRx.find(full)
        val ev = emVezesRx.find(full)
        when {
            pr != null -> {
                atual = pr.groupValues[1].toInt()
                parcelas = pr.groupValues[2].toInt()
            }
            vd != null -> {
                parcelas = vd.groupValues[1].toInt()
                valor = cents(vd.groupValues[2]) ?: first
            }
            ev != null -> {
                parcelas = ev.groupValues[1].toInt()
                if (parcelas >= 2) {
                    valor = first / parcelas
                    extra = first - valor * parcelas
                }
            }
        }
        if (parcelas < 2 || parcelas > 48 || atual > parcelas) {
            parcelas = 1; atual = 0; valor = first; extra = 0
        }

        val after = full.substring(m.range.last + 1)
        val before = full.substring(0, m.range.first)
        val raw = afterRx.find(after)?.groupValues?.get(1)
            ?: beforeRx.find(before)?.groupValues?.get(1)
            ?: leadingRx.find(after)?.groupValues?.get(1)
            ?: colonRx.find(before)?.groupValues?.get(1)?.takeIf { !genericRx.containsMatchIn(it) }
            ?: trailingRx.find(after)?.groupValues?.get(1)?.takeIf { !genericRx.containsMatchIn(it) }
        val cleaned = raw?.replace(cutRx, "")?.trim()
        val merchant: String = cleaned?.takeIf { it.length >= 2 && !(emprestimo && genericRx.containsMatchIn(it)) }
            ?: when {
                emprestimo -> if (title.isBlank()) "Empréstimo" else "Empréstimo ${title.trim()}"
                low.contains("boleto") -> "Boleto"
                else -> title.ifBlank { "Gasto" }
            }
        val pay = when {
            emprestimo -> "Empréstimo"
            parcelas > 1 || low.contains("crédito") || low.contains("credito") -> "Crédito"
            low.contains("débito") || low.contains("debito") -> "Débito"
            low.contains("pix") -> "Pix"
            low.contains("boleto") -> "Boleto"
            low.contains("saque") -> "Dinheiro"
            else -> ""
        }
        return Parsed(valor, pretty(merchant), parcelas, atual, extra, pay, finalCartao(full))
    }

    // ------------------------------------------------------------ Entradas

    private val incomeWords = listOf(
        "pix recebido", "você recebeu", "voce recebeu", "recebeu um pix", "recebeu uma transfer",
        "transferência recebida", "transferencia recebida", "ted recebid", "doc recebid", "pagamento recebido",
        "depósito", "deposito", "salário", "salario", "crédito em conta", "credito em conta", "creditad",
        "recebimento de", "te enviou", "enviou para você", "enviou para voce", "enviou a você", "enviou a voce",
        "te transferiu", "transferiu para você", "transferiu para voce", "transferiu pra você", "transferiu pra voce",
        "te pagou", "caiu na conta", "caiu na sua conta", "entrou na sua conta",
    )
    private val incomeSkip = noise + listOf("limite")

    /** "MARIA SOUZA te enviou R$ 50,00" */
    private val senderBeforeRx = Regex(
        """(?i)^(.*?)\s+(?:te enviou|enviou (?:para|a) voc[eê]|te transferiu|transferiu p(?:a)?ra voc[eê]|te pagou)"""
    )
    /** "Pix recebido de MARIA SOUZA no valor de R$ 50,00" */
    private val fromBeforeRx = Regex(
        """(?i)\b(?:recebid[oa]|recebeu(?: um pix| uma transfer[êe]ncia)?)\s+de\s+(?!R\$)([^,.;\n]+?)(?:\s+(?:no valor|de R\$|R\$).*)?$"""
    )
    private val fromRx = Regex("""(?i)\b(?:de|da|do|por)\s+(?!R\$)(.+)""")
    private val cnpjRx = Regex("""\b(\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2})\b""")
    private val docRx = Regex("""(?i)\s*[-–(]?\s*(?:cnpj|cpf)\s*:?\s*[\d.*/\-]*\)?|\s*\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}|\s*[*•]{3}[\d.*\-]+""")

    fun parseIncome(title: String, text: String): ParsedIncome? {
        val full = "$title $text".trim()
        val low = full.lowercase()
        if (incomeWords.none { low.contains(it) }) return null
        if (incomeSkip.any { low.contains(it) }) return null
        val m = money.find(full) ?: return null
        val c = cents(m.groupValues[1]) ?: return null
        if (c <= 0) return null
        val kind = when {
            low.contains("salário") || low.contains("salario") || low.contains("folha") -> "Salário"
            low.contains("pix") -> "Pix recebido"
            low.contains("depósito") || low.contains("deposito") -> "Depósito"
            low.contains("transfer") -> "Transferência"
            else -> "Outros"
        }
        val after = full.substring(m.range.last + 1)
        val cnpj = cnpjRx.find(full)?.groupValues?.get(1) ?: ""
        val before = full.substring(0, m.range.first)
        val quemBruto = fromRx.find(after)?.groupValues?.get(1)
            ?: fromBeforeRx.find(before.trim())?.groupValues?.get(1)
            ?: (senderBeforeRx.find(text.trim()) ?: senderBeforeRx.find(full))?.groupValues?.get(1)
                ?.split(Regex("""[:!.—–\-]\s""")).orEmpty().lastOrNull()
        val quem = quemBruto?.replace(docRx, "")?.replace(cutRx, "")?.trim()
        val nome = if (quem != null && quem.length >= 2) pretty(quem) else ""
        val desc = if (nome.isNotBlank()) "$kind de $nome" else kind
        return ParsedIncome(c, desc, kind, nome, cnpj)
    }

    /** "IFOOD   *RESTAURANTE" -> "Ifood *restaurante" (só quando vem tudo em maiúsculas). */
    private fun pretty(s: String): String {
        val t = s.replace(Regex("""\s+"""), " ").trim().take(40)
        if (t != t.uppercase()) return t
        return t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
