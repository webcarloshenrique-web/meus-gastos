package br.com.meusgastos

import java.util.Calendar

/** Ajudantes de datas. offset = 0 é o mês atual, -1 o mês passado, e assim por diante. */
object Dates {
    val ABBR = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
    val NAMES = arrayOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )
    val WEEK = arrayOf("D", "S", "T", "Q", "Q", "S", "S")
    const val DAY_MS = 86_400_000L

    fun start(offset: Int): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, offset)
        return c
    }

    fun range(offset: Int): Pair<Long, Long> {
        val c = start(offset)
        val s = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return s to c.timeInMillis
    }

    fun abbr(offset: Int): String = ABBR[start(offset).get(Calendar.MONTH)]
    fun name(offset: Int): String = NAMES[start(offset).get(Calendar.MONTH)]
    fun year(offset: Int): Int = start(offset).get(Calendar.YEAR)
    fun full(offset: Int): String = "${name(offset)} de ${year(offset)}"
    fun daysIn(offset: Int): Int = start(offset).getActualMaximum(Calendar.DAY_OF_MONTH)

    /** 0 = domingo ... 6 = sábado */
    fun firstWeekday(offset: Int): Int = start(offset).get(Calendar.DAY_OF_WEEK) - 1

    fun dayOfMonth(ms: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return c.get(Calendar.DAY_OF_MONTH)
    }

    fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }
}
