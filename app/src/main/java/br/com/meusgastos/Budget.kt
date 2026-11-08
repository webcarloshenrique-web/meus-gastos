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
        // Entradas contam pelo mês que pagam (ex.: salário que cai em novembro paga outubro)
        val ym = Recorrentes.ym(r.first)
        val entrou = db.sumIncomeMonth(ym)
        val saiu = db.sumBetween(r.first, r.second)
        val anteriores = (1..3).map { db.sumIncomeMonth(ym - it) }.filter { it > 0 }
        val media = if (anteriores.isEmpty()) 0L else anteriores.sum() / anteriores.size
        val renda = if (entrou > 0) entrou else media
        val hoje = Dates.today()
        val dim = Dates.daysIn(month)
        // Previsão: só os gastos do dia a dia seguem o ritmo; fixos e parcelas entram pelo valor cheio.
        val fixosLancados = db.sumFixed(r.first, r.second)
        val parcelas = db.sumInstallments(r.first, r.second)
        val fixosMes = Recorrentes.totalMensal(db)
        val variavel = (saiu - fixosLancados - parcelas).coerceAtLeast(0)
        val projecao = if (month == 0 && hoje >= 3 && saiu > 0) {
            variavel * dim / hoje + maxOf(fixosMes, fixosLancados) + parcelas
        } else null
        val porDia = if (month == 0 && renda > 0) (renda - saiu) / (dim - hoje + 1).coerceAtLeast(1) else null
        return Saldo(entrou, saiu, renda, entrou == 0L && media > 0, projecao, porDia)
    }
}
