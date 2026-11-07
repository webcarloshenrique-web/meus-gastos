package br.com.meusgastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Exporta gastos e entradas em CSV (abre no Excel/Planilhas) e restaura a partir dele.
 * Bloco 1 (gastos):   Data; Hora; Categoria; Estabelecimento; Parcela; Valor (R$)
 * Bloco 2 (RESUMO):   total, quantidade e % por categoria
 * Bloco 3 (ENTRADAS): Data; Hora; Tipo; Descrição; Valor (R$)
 */
object Csv {
    private const val HEADER = "Data;Hora;Categoria;Estabelecimento;Parcela;Valor (R$)"
    private const val HEADER_IN = "Data;Hora;Tipo;Descrição;Valor (R$)"

    private fun num(cents: Long) = String.format(ptBR, "%.2f", cents / 100.0)
    private fun clean(s: String) = s.replace(";", ",").replace("\n", " ").trim()

    fun build(list: List<Expense>, entradas: List<Income>, periodo: String): String {
        val dF = SimpleDateFormat("dd/MM/yyyy", ptBR)
        val hF = SimpleDateFormat("HH:mm", ptBR)
        val sb = StringBuilder("﻿")
        sb.append(HEADER).append('\n')
        list.sortedBy { it.dateMs }.forEach {
            val d = Date(it.dateMs)
            sb.append(dF.format(d)).append(';')
                .append(hF.format(d)).append(';')
                .append(clean(it.category ?: "Pendente")).append(';')
                .append(clean(it.merchant)).append(';')
                .append(if (it.instTotal > 1) "${it.instNum}/${it.instTotal}" else "").append(';')
                .append(num(it.cents)).append('\n')
        }
        val total = list.sumOf { it.cents }
        val totalIn = entradas.sumOf { it.cents }
        sb.append('\n')
        sb.append("RESUMO\n")
        sb.append("Período;").append(periodo).append('\n')
        sb.append("Total gasto;").append(num(total)).append('\n')
        sb.append("Quantidade de gastos;").append(list.size).append('\n')
        if (entradas.isNotEmpty()) {
            sb.append("Total de entradas;").append(num(totalIn)).append('\n')
            sb.append("Sobra (entradas - gastos);").append(num(totalIn - total)).append('\n')
        }
        sb.append('\n')
        sb.append("Categoria;Qtd;Total (R$);% do total\n")
        list.groupBy { it.category ?: "Pendente" }
            .map { (c, l) -> Triple(c, l.size, l.sumOf { it.cents }) }
            .sortedByDescending { it.third }
            .forEach { (c, n, v) ->
                val pct = if (total > 0) (v * 100.0 / total).roundToInt() else 0
                sb.append(clean(c)).append(';').append(n).append(';').append(num(v)).append(';').append(pct).append("%\n")
            }
        if (entradas.isNotEmpty()) {
            sb.append('\n')
            sb.append("ENTRADAS\n")
            sb.append(HEADER_IN).append('\n')
            entradas.sortedBy { it.dateMs }.forEach {
                val d = Date(it.dateMs)
                sb.append(dF.format(d)).append(';').append(hF.format(d)).append(';')
                    .append(clean(it.kind)).append(';').append(clean(it.desc)).append(';')
                    .append(num(it.cents)).append('\n')
            }
        }
        return sb.toString()
    }

    /** kind: 0 = mês selecionado, 1 = últimos 5 meses, 2 = tudo. Retorna false se não houver nada. */
    fun export(ctx: Context, db: Db, kind: Int, month: Int): Boolean {
        val (de, ate, periodo, nome) = when (kind) {
            0 -> {
                val r = Dates.range(month)
                Quad(r.first, r.second, Dates.full(month), "gastos_${Dates.year(month)}_${Dates.abbr(month)}")
            }
            1 -> Quad(
                Dates.range(month - 4).first, Dates.range(month).second,
                "${Dates.abbr(month - 4)}/${Dates.year(month - 4)} a ${Dates.abbr(month)}/${Dates.year(month)}",
                "gastos_5meses_ate_${Dates.abbr(month)}_${Dates.year(month)}",
            )
            else -> Quad(0L, Long.MAX_VALUE, "Todos os registros", "gastos_todos")
        }
        val lista = db.expenses(de, ate)
        val entradas = db.incomes(de, ate)
        if (lista.isEmpty() && entradas.isEmpty()) return false
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "$nome.csv")
        f.writeText(build(lista, entradas, periodo), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Meus Gastos — $periodo")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Enviar ou salvar o relatório"))
        return true
    }

    private data class Quad(val a: Long, val b: Long, val c: String, val d: String)

    /** Restaura gastos e entradas de um CSV exportado por este app. Retorna quantos foram adicionados, ou -1. */
    fun restore(ctx: Context, db: Db, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return 0
        val lines = text.removePrefix("﻿").lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("Data;Hora")) return -1
        val comParcela = lines[0].contains("Parcela")
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBR)
        fun data(d: String, h: String): Long? = try {
            fmt.parse("${d.trim()} ${h.trim()}")?.time
        } catch (_: Exception) {
            null
        }

        var added = 0
        // Gastos (até a primeira linha em branco)
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val p = line.split(";")
            val vIdx = if (comParcela) 5 else 4
            if (p.size <= vIdx) continue
            val date = data(p[0], p[1]) ?: continue
            val cents = parseCents(p[vIdx]) ?: continue
            val cat = p[2].trim().takeIf { it.isNotEmpty() && it != "Pendente" }
            val merchant = p[3].trim().ifEmpty { "Gasto" }
            var num = 0
            var tot = 0
            if (comParcela) {
                val pp = p[4].trim().split("/")
                if (pp.size == 2) { num = pp[0].toIntOrNull() ?: 0; tot = pp[1].toIntOrNull() ?: 0 }
            }
            if (db.exists(cents, merchant, date)) continue
            db.insertExpense(Expense(0, cents, merchant, cat, date, "backup", 0, num, tot))
            added++
        }
        // Entradas (depois da linha "ENTRADAS")
        val ini = lines.indexOfFirst { it.trim() == "ENTRADAS" }
        if (ini >= 0) {
            for (line in lines.drop(ini + 2)) {
                if (line.isBlank()) break
                val p = line.split(";")
                if (p.size < 5) continue
                val date = data(p[0], p[1]) ?: continue
                val cents = parseCents(p[4]) ?: continue
                val desc = p[3].trim().ifEmpty { p[2].trim() }
                if (db.existsIncome(cents, desc, date)) continue
                db.insertIncome(Income(0, cents, desc, p[2].trim(), date, "backup"))
                added++
            }
        }
        return added
    }
}
