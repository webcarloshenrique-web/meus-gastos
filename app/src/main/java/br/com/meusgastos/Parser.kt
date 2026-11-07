package br.com.meusgastos

/** Resultado da leitura de uma notificação: valor (em centavos) e estabelecimento. */
data class Parsed(val cents: Long, val merchant: String)

/**
 * Lê notificações de bancos brasileiros de forma genérica.
 * Funciona com textos como:
 *  - "Compra de R$ 25,90 APROVADA em IFOOD"
 *  - "Compra aprovada no cartão final 1234 em PADARIA X no valor de R$ 12,00"
 *  - "Pix enviado: você enviou R$ 100,00 para João Silva"
 */
object Parser {

    private val money = Regex("""R\$\s*([0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{2}|[0-9]+,[0-9]{2})""")

    /** Palavras que indicam que é um GASTO. */
    private val spendWords = listOf(
        "compra", "pagamento", "pix enviado", "voce enviou", "você enviou", "enviou",
        "debito", "débito", "saque", "transferencia enviada", "transferência enviada",
        "pagou", "pix realizado", "pix feito", "transferencia realizada", "transferência realizada",
    )

    /** Palavras que indicam que NÃO é gasto (entrada de dinheiro, estorno, propaganda, fatura). */
    private val skipWords = listOf(
        "recebeu", "recebido", "recebida", "estorno", "estornad", "deposito", "depósito",
        "reembolso", "cashback", "fatura", "emprestimo", "empréstimo", "oferta", "cancelad",
        "negad", "recusad", "não aprovad", "nao aprovad",
    )

    /** "... R$ 10,00 em LOJA" */
    private val afterRx = Regex("""(?i)\b(?:em|no|na|para|p/|a favor de)\s+(?!dia\b|data\b|\d)(.+)""")

    /** "... em LOJA no valor de R$ 10,00" (pega a ÚLTIMA preposição antes do valor) */
    private val beforeRx = Regex(
        """(?is)^.*\b(?:em|no|na|para)\s+(?!valor\b|o valor\b|cart)([^.,;\n]+?)(?:\s+(?:no valor|com valor|valor|de|por))*\s*$"""
    )

    /** "... R$ 10,00 UBER *TRIP no dia 07/11" (nome logo depois do valor, começando com maiúscula) */
    private val leadingRx = Regex("""^\s*[:\-]?\s*(\p{Lu}.+)""")

    private val cutRx = Regex(
        """(?i)\s*(?:\.(?=\s|$)|[;,\n]| às | as | - | — | no dia| na data| no cart| no cr[eé]dito| no d[eé]bito| cart[ãa]o| final | aprovad| em \d| dia \d| via | no valor| com valor).*$""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parse(title: String, text: String): Parsed? {
        val full = "$title $text".trim()
        val low = full.lowercase()
        if (skipWords.any { low.contains(it) }) return null
        if (spendWords.none { low.contains(it) }) return null

        val m = money.find(full) ?: return null
        val cents = m.groupValues[1].replace(".", "").replace(",", "").toLongOrNull() ?: return null
        if (cents <= 0) return null

        val after = full.substring(m.range.last + 1)
        val before = full.substring(0, m.range.first)
        val raw = afterRx.find(after)?.groupValues?.get(1)
            ?: beforeRx.find(before)?.groupValues?.get(1)
            ?: leadingRx.find(after)?.groupValues?.get(1)
        val cleaned = raw?.replace(cutRx, "")?.trim()
        val merchant: String = cleaned?.takeIf { it.length >= 2 } ?: title.ifBlank { "Gasto" }
        return Parsed(cents, pretty(merchant))
    }

    /** "IFOOD   *RESTAURANTE" -> "Ifood *restaurante" (só quando vem tudo em maiúsculas). */
    private fun pretty(s: String): String {
        val t = s.replace(Regex("""\s+"""), " ").trim().take(40)
        if (t != t.uppercase()) return t
        return t.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
