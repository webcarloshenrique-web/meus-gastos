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
)

/** Resultado da leitura de uma entrada de dinheiro (Pix recebido, depósito, salário). */
data class ParsedIncome(val cents: Long, val desc: String, val kind: String)

/**
 * Lê notificações de bancos brasileiros de forma genérica.
 * Gastos:   "Compra de R$ 25,90 APROVADA em IFOOD"
 *           "Compra de R$ 300,00 em 3x em MAGAZINE LUIZA"   -> 3 parcelas de R$ 100,00
 *           "Compra em 10x de R$ 50,00 em LOJA X"           -> 10 parcelas de R$ 50,00
 *           "Parcela 3/10 de R$ 50,00 LOJA X"               -> só a parcela 3
 * Entradas: "Você recebeu um Pix de R$ 150,00 de MARIA"
 */
object Parser {

    private const val MONEY = """[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{2}|[0-9]+,[0-9]{2}"""
    private val money = Regex("""R\$\s*($MONEY)""")

    /** Palavras que indicam que é um GASTO. */
    private val spendWords = listOf(
        "compra", "pagamento", "pix enviado", "voce enviou", "você enviou", "enviou",
        "debito", "débito", "saque", "transferencia enviada", "transferência enviada",
        "pagou", "pix realizado", "pix feito", "transferencia realizada", "transferência realizada",
        "parcela",
    )

    /** Palavras que indicam que NÃO é gasto (entrada de dinheiro, estorno, propaganda, fatura). */
    private val skipWords = listOf(
        "recebeu", "recebido", "recebida", "estorno", "estornad", "deposito", "depósito",
        "reembolso", "cashback", "fatura", "emprestimo", "empréstimo", "oferta", "cancelad",
        "negad", "recusad", "não aprovad", "nao aprovad", "limite disponível", "limite disponivel",
    )

    // --- parcelas ---
    private val parcelaRx = Regex("""(?i)parcela\s*(\d{1,2})\s*(?:/|de)\s*(\d{1,2})""")
    private val vezesDeRx = Regex("""(?i)(\d{1,2})\s*(?:x|vezes|parcelas)\s*de\s*R\$\s*($MONEY)""")
    private val emVezesRx = Regex("""(?i)(?:\bem\s*|\s)(\d{1,2})\s*(?:x\b|vezes\b|parcelas\b)""")

    // --- estabelecimento ---
    private val afterRx = Regex("""(?i)\b(?:em|no|na|para|p/|a favor de)\s+(?!dia\b|data\b|\d|cart|seu\b|sua\b)(.+)""")
    private val beforeRx = Regex(
        """(?is)^.*\b(?:em|no|na|para)\s+(?!valor\b|o valor\b|cart|\d)([^.,;\n]+?)(?:\s+(?:no valor|com valor|valor|de|por))*\s*$"""
    )
    private val leadingRx = Regex("""^\s*[:\-]?\s*(\p{Lu}.+)""")
    private val cutRx = Regex(
        """(?i)\s*(?:\.(?=\s|$)|[;,\n]| às | as | - | — | no dia| na data| no cart| no cr[eé]dito| no d[eé]bito| cart[ãa]o| final | aprovad| em \d| dia \d| via | no valor| com valor| \d{1,2}\s*x\b| parcelad| de R\$).*$""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun cents(s: String): Long? = s.replace(".", "").replace(",", "").toLongOrNull()

    fun parse(title: String, text: String): Parsed? {
        val full = "$title $text".trim()
        val low = full.lowercase()
        if (skipWords.any { low.contains(it) }) return null
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
        val cleaned = raw?.replace(cutRx, "")?.trim()
        val merchant: String = cleaned?.takeIf { it.length >= 2 } ?: title.ifBlank { "Gasto" }
        val pay = when {
            parcelas > 1 || low.contains("crédito") || low.contains("credito") -> "Crédito"
            low.contains("débito") || low.contains("debito") -> "Débito"
            low.contains("pix") -> "Pix"
            low.contains("boleto") -> "Boleto"
            low.contains("saque") -> "Dinheiro"
            else -> ""
        }
        return Parsed(valor, pretty(merchant), parcelas, atual, extra, pay)
    }

    // ------------------------------------------------------------ Entradas

    private val incomeWords = listOf(
        "pix recebido", "você recebeu", "voce recebeu", "recebeu um pix", "recebeu uma transfer",
        "transferência recebida", "transferencia recebida", "depósito", "deposito", "salário", "salario",
        "crédito em conta", "credito em conta", "recebimento de",
    )
    private val incomeSkip = listOf(
        "estorno", "fatura", "limite", "empréstimo", "emprestimo", "oferta", "agendad", "cancelad",
        "devolvid", "não realizad", "nao realizad", "não concluíd", "nao concluid", "cashback",
    )
    private val fromRx = Regex("""(?i)\b(?:de|da|do|por)\s+(?!R\$)(.+)""")

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
        val quem = fromRx.find(after)?.groupValues?.get(1)?.replace(cutRx, "")?.trim()
        val desc = if (quem != null && quem.length >= 2) "$kind de ${pretty(quem)}" else kind
        return ParsedIncome(c, desc, kind)
    }

    /** "IFOOD   *RESTAURANTE" -> "Ifood *restaurante" (só quando vem tudo em maiúsculas). */
    private fun pretty(s: String): String {
        val t = s.replace(Regex("""\s+"""), " ").trim().take(40)
        if (t != t.uppercase()) return t
        return t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
