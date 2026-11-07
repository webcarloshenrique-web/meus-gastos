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
 * Exporta os gastos em CSV (abre no Excel/Planilhas) e restaura a partir dele.
 * Colunas: Data; Hora; Categoria; Estabelecimento; Valor (R$)
 * Depois da tabela vem um pequeno RESUMO por categoria.
 */
object Csv {
    private const val HEADER = "Data;Hora;Categoria;Estabelecimento;Valor (R$)"

    private fun num(cents: Long) = String.format(ptBR, "%.2f", cents / 100.0)
    private fun clean(s: String) = s.replace(";", ",").replace("\n", " ").trim()

    fun build(list: List<Expense>, periodo: String): String {
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
                .append(num(it.cents)).append('\n')
        }
        val total = list.sumOf { it.cents }
        sb.append('\n')
        sb.append("RESUMO\n")
        sb.append("Período;").append(periodo).append('\n')
        sb.append("Total gasto;").append(num(total)).append('\n')
        sb.append("Quantidade de gastos;").append(list.size).append('\n')
        sb.append('\n')
        sb.append("Categoria;Qtd;Total (R$);% do total\n")
        list.groupBy { it.category ?: "Pendente" }
            .map { (c, l) -> Triple(c, l.size, l.sumOf { it.cents }) }
            .sortedByDescending { it.third }
            .forEach { (c, n, v) ->
                val pct = if (total > 0) (v * 100.0 / total).roundToInt() else 0
                sb.append(clean(c)).append(';').append(n).append(';').append(num(v)).append(';').append(pct).append("%\n")
            }
        return sb.toString()
    }

    /** kind: 0 = mês selecionado, 1 = últimos 5 meses, 2 = tudo. Retorna false se não houver gastos. */
    fun export(ctx: Context, db: Db, kind: Int, month: Int): Boolean {
        val (lista, periodo, nome) = when (kind) {
            0 -> {
                val r = Dates.range(month)
                Triple(db.expenses(r.first, r.second), Dates.full(month), "gastos_${Dates.year(month)}_${Dates.abbr(month)}")
            }
            1 -> {
                val from = Dates.range(month - 4).first
                val to = Dates.range(month).second
                Triple(
                    db.expenses(from, to),
                    "${Dates.abbr(month - 4)}/${Dates.year(month - 4)} a ${Dates.abbr(month)}/${Dates.year(month)}",
                    "gastos_5meses_ate_${Dates.abbr(month)}_${Dates.year(month)}",
                )
            }
            else -> Triple(db.all(), "Todos os gastos", "gastos_todos")
        }
        if (lista.isEmpty()) return false
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "$nome.csv")
        f.writeText(build(lista, periodo), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Meus Gastos — $periodo")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Enviar ou salvar o relatório"))
        return true
    }

    /** Restaura gastos de um CSV exportado por este app. Retorna quantos foram adicionados. */
    fun restore(ctx: Context, db: Db, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return 0
        val lines = text.removePrefix("﻿").lines()
        if (lines.isEmpty() || !lines[0].trim().startsWith("Data;Hora")) return -1
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBR)
        var added = 0
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val p = line.split(";")
            if (p.size < 5) continue
            val parsedDate: Long? = try {
                fmt.parse("${p[0].trim()} ${p[1].trim()}")?.time
            } catch (_: Exception) {
                null
            }
            val date = parsedDate ?: continue
            val cents = parseCents(p[4]) ?: continue
            val cat = p[2].trim().takeIf { it.isNotEmpty() && it != "Pendente" }
            val merchant = p[3].trim().ifEmpty { "Gasto" }
            if (db.exists(cents, merchant, date)) continue
            db.insertExpense(Expense(0, cents, merchant, cat, date, "backup"))
            added++
        }
        return added
    }
}
