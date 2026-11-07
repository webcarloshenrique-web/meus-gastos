package br.com.meusgastos

/**
 * Contas do mês: quanto entrou, quanto saiu, quanto sobra e a previsão.
 * Se você ainda não registrou entradas no mês, usa a média dos 3 meses anteriores (renda estimada).
 */
data class Saldo(
    val entrou: Long,
    val saiu: Long,
    val renda: Long,
    val estimada: Boolean,
    val projecaoGasto: Long?,
    val porDia: Long?,
) {
    val sobra: Long get() = renda - saiu
    val sobraPrevista: Long? get() = projecaoGasto?.let { renda - it }
}

object Budget {
    fun saldo(db: Db, month: Int): Saldo {
        val r = Dates.range(month)
        val entrou = db.sumIncome(r.first, r.second)
        val saiu = db.sumBetween(r.first, r.second)
        val anteriores = (1..3).map { val x = Dates.range(month - it); db.sumIncome(x.first, x.second) }.filter { it > 0 }
        val media = if (anteriores.isEmpty()) 0L else anteriores.sum() / anteriores.size
        val renda = if (entrou > 0) entrou else media
        val hoje = Dates.today()
        val dim = Dates.daysIn(month)
        val projecao = if (month == 0 && hoje >= 3 && saiu > 0) saiu * dim / hoje else null
        val porDia = if (month == 0 && renda > 0) (renda - saiu) / (dim - hoje + 1).coerceAtLeast(1) else null
        return Saldo(entrou, saiu, renda, entrou == 0L && media > 0, projecao, porDia)
    }
}
