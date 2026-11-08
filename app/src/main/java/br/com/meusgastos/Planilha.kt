package br.com.meusgastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.core.content.FileProvider
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/** Valor em dinheiro para a planilha (sai formatado como 1.234,56 no Excel). */
data class Dinheiro(val cents: Long)

// ====================================================================== Excel (.xlsx) sem bibliotecas

object Xlsx {
    class Sheet(val name: String, val rows: List<List<Any?>>, val widths: List<Int> = emptyList())

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    fun write(out: OutputStream, sheets: List<Sheet>) {
        val z = ZipOutputStream(out)
        fun put(name: String, content: String) {
            z.putNextEntry(ZipEntry(name))
            z.write(content.toByteArray(Charsets.UTF_8))
            z.closeEntry()
        }
        val head = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
        val ns = "http://schemas.openxmlformats.org"

        put(
            "[Content_Types].xml",
            head + """<Types xmlns="$ns/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                sheets.indices.joinToString("") {
                    """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                } + "</Types>"
        )
        put(
            "_rels/.rels",
            head + """<Relationships xmlns="$ns/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="$ns/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>"
        )
        put(
            "xl/workbook.xml",
            head + """<workbook xmlns="$ns/spreadsheetml/2006/main" xmlns:r="$ns/officeDocument/2006/relationships"><sheets>""" +
                sheets.mapIndexed { i, s ->
                    """<sheet name="${esc(s.name.take(31))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
                }.joinToString("") + "</sheets></workbook>"
        )
        put(
            "xl/_rels/workbook.xml.rels",
            head + """<Relationships xmlns="$ns/package/2006/relationships">""" +
                sheets.indices.joinToString("") {
                    """<Relationship Id="rId${it + 1}" Type="$ns/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>"""
                } +
                """<Relationship Id="rId${sheets.size + 1}" Type="$ns/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                "</Relationships>"
        )
        // Estilos: 0 normal, 1 negrito (cabeçalho), 2 dinheiro (#,##0.00)
        put(
            "xl/styles.xml",
            head + """<styleSheet xmlns="$ns/spreadsheetml/2006/main">""" +
                """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
                """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
                """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
                """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
                """<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
                """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
                """<xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>""" +
                "</styleSheet>"
        )
        sheets.forEachIndexed { si, s ->
            val sb = StringBuilder(head)
            sb.append("""<worksheet xmlns="$ns/spreadsheetml/2006/main">""")
            if (s.widths.isNotEmpty()) {
                sb.append("<cols>")
                s.widths.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
                sb.append("</cols>")
            }
            sb.append("<sheetData>")
            s.rows.forEachIndexed { ri, row ->
                val r = ri + 1
                sb.append("""<row r="$r">""")
                row.forEachIndexed { ci, v ->
                    val ref = "${col(ci)}$r"
                    val bold = if (ri == 0) """ s="1"""" else ""
                    when (v) {
                        null -> {}
                        is Dinheiro -> sb.append("""<c r="$ref" s="2"><v>${String.format(Locale.US, "%.2f", v.cents / 100.0)}</v></c>""")
                        is Number -> sb.append("""<c r="$ref"$bold><v>$v</v></c>""")
                        else -> {
                            val t = v.toString()
                            if (t.isNotEmpty()) {
                                sb.append("""<c r="$ref" t="inlineStr"$bold><is><t xml:space="preserve">${esc(t)}</t></is></c>""")
                            }
                        }
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            put("xl/worksheets/sheet${si + 1}.xml", sb.toString())
        }
        z.finish()
        z.flush()
    }

    /** Lê a primeira aba de um .xlsx como linhas de texto. */
    fun read(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (n == "xl/sharedStrings.xml" || n.startsWith("xl/worksheets/sheet")) files[n] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { readShared(it) } ?: emptyList()
        val sheetName = if (files.containsKey("xl/worksheets/sheet1.xml")) "xl/worksheets/sheet1.xml"
        else files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull() ?: return emptyList()
        return readSheet(files[sheetName]!!, shared)
    }

    private fun tag(p: XmlPullParser) = p.name.substringAfter(':')

    private fun readShared(b: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), "UTF-8")
        var cur: StringBuilder? = null
        var inT = false
        var inPh = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (tag(p)) {
                    "si" -> cur = StringBuilder()
                    "t" -> inT = true
                    "rPh" -> inPh = true
                }
                XmlPullParser.TEXT -> if (inT && !inPh) cur?.append(p.text)
                XmlPullParser.END_TAG -> when (tag(p)) {
                    "t" -> inT = false
                    "rPh" -> inPh = false
                    "si" -> { out.add(cur?.toString() ?: ""); cur = null }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    private fun readSheet(b: ByteArray, shared: List<String>): List<List<String>> {
        val rows = java.util.TreeMap<Int, java.util.TreeMap<Int, String>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), "UTF-8")
        var rowIdx = -1
        var colIdx = -1
        var type: String? = null
        var buf = StringBuilder()
        var inV = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (tag(p)) {
                    "row" -> {
                        rowIdx = p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1) ?: (rowIdx + 1)
                        colIdx = -1
                    }
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r")
                        colIdx = if (ref != null) colIndex(ref) else colIdx + 1
                        type = p.getAttributeValue(null, "t")
                        buf = StringBuilder()
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) buf.append(p.text)
                XmlPullParser.END_TAG -> when (tag(p)) {
                    "v", "t" -> inV = false
                    "c" -> {
                        val raw = buf.toString()
                        val v = when (type) {
                            "s" -> shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" }
                            "b" -> if (raw == "1") "Sim" else "Não"
                            else -> raw
                        }
                        if (rowIdx >= 0 && colIdx >= 0) rows.getOrPut(rowIdx) { java.util.TreeMap() }[colIdx] = v
                    }
                }
            }
            ev = p.next()
        }
        if (rows.isEmpty()) return emptyList()
        val out = ArrayList<List<String>>()
        for (r in 0..rows.lastKey()) {
            val m = rows[r]
            if (m == null || m.isEmpty()) { out.add(emptyList()); continue }
            out.add((0..m.lastKey()).map { m[it] ?: "" })
        }
        return out
    }
}

// ====================================================================== CSV

object CsvTable {
    fun write(rows: List<List<Any?>>): String {
        val sb = StringBuilder("﻿")
        rows.forEach { row ->
            sb.append(row.joinToString(";") { v ->
                when (v) {
                    null -> ""
                    is Dinheiro -> String.format(ptBR, "%.2f", v.cents / 100.0)
                    else -> v.toString().replace(";", ",").replace("\n", " ")
                }
            }).append('\n')
        }
        return sb.toString()
    }

    /** Lê CSV com ; ou , e aspas. Aceita UTF-8 ou o padrão do Excel no Windows. */
    fun read(bytes: ByteArray): List<List<String>> {
        val text = decode(bytes).removePrefix("﻿")
        val lines = text.split("\r\n", "\n", "\r")
        val firstReal = lines.firstOrNull { it.isNotBlank() } ?: return emptyList()
        val sep = if (firstReal.count { it == ';' } >= firstReal.count { it == ',' }) ';' else ','
        return lines.map { splitLine(it, sep) }
    }

    private fun decode(b: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b)).toString()
    } catch (_: Exception) {
        String(b, Charset.forName("windows-1252"))
    }

    private fun splitLine(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> q = !q
                c == sep && !q -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}

// ====================================================================== Exportar / importar

object Planilha {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_CSV = "text/csv"

    /** Os mesmos cabeçalhos servem para exportar e importar. */
    val CABECALHO = listOf(
        "Tipo", "Data", "Hora", "Descrição", "Categoria", "Valor (R$)",
        "Parcela atual", "Total de parcelas", "Criar próximas parcelas?", "Forma de pagamento", "Fixo?", "Cartão",
    )
    private val LARGURAS = listOf(10, 12, 8, 30, 18, 14, 13, 17, 24, 19, 8, 22)

    private val dF = SimpleDateFormat("dd/MM/yyyy", ptBR)
    private val hF = SimpleDateFormat("HH:mm", ptBR)

    // ------------------------------------------------------------ Exportar

    private fun linhas(db: Db, de: Long, ate: Long): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        rows.add(CABECALHO)
        db.expenses(de, ate).sortedBy { it.dateMs }.forEach {
            val d = Date(it.dateMs)
            val parc = it.instTotal > 1
            rows.add(
                listOf(
                    "Gasto", dF.format(d), hF.format(d), it.merchant, it.category ?: "", Dinheiro(it.cents),
                    if (parc) it.instNum else null, if (parc) it.instTotal else null, if (parc) "Não" else null,
                    it.pay, if (it.fixedId > 0) "Sim" else "Não", it.card,
                )
            )
        }
        db.incomes(de, ate).sortedBy { it.dateMs }.forEach {
            val d = Date(it.dateMs)
            rows.add(listOf("Entrada", dF.format(d), hF.format(d), it.desc, it.kind, Dinheiro(it.cents), null, null, null))
        }
        return rows
    }

    private fun resumo(db: Db, de: Long, ate: Long, periodo: String): List<List<Any?>> {
        val gastos = db.expenses(de, ate)
        val total = gastos.sumOf { it.cents }
        val entrou = db.incomes(de, ate).sumOf { it.cents }
        val rows = ArrayList<List<Any?>>()
        rows.add(listOf("Resumo", ""))
        rows.add(listOf("Período", periodo))
        rows.add(listOf("Total gasto", Dinheiro(total)))
        rows.add(listOf("Quantidade de gastos", gastos.size))
        rows.add(listOf("Total de entradas", Dinheiro(entrou)))
        rows.add(listOf("Sobra (entradas - gastos)", Dinheiro(entrou - total)))
        rows.add(listOf(""))
        rows.add(listOf("Categoria", "Qtd", "Total (R$)", "% do total"))
        gastos.groupBy { it.category ?: "Pendente" }
            .map { (c, l) -> Triple(c, l.size, l.sumOf { it.cents }) }
            .sortedByDescending { it.third }
            .forEach { (c, n, v) ->
                rows.add(listOf(c, n, Dinheiro(v), "${if (total > 0) (v * 100.0 / total).roundToInt() else 0}%"))
            }
        return rows
    }

    /** Gera o arquivo de exportação. Retorna null se não houver nada no período. */
    fun exportar(ctx: Context, db: Db, de: Long, ate: Long, periodo: String, nome: String, excel: Boolean): File? {
        val dados = linhas(db, de, ate)
        if (dados.size <= 1) return null
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        return if (excel) {
            val f = File(dir, "$nome.xlsx")
            f.outputStream().use {
                Xlsx.write(
                    it,
                    listOf(
                        Xlsx.Sheet("Lançamentos", dados, LARGURAS),
                        Xlsx.Sheet("Resumo", resumo(db, de, ate, periodo), listOf(28, 18, 14, 12)),
                    )
                )
            }
            f
        } else {
            val f = File(dir, "$nome.csv")
            f.writeText(CsvTable.write(dados + listOf(listOf<Any?>()) + resumo(db, de, ate, periodo)), Charsets.UTF_8)
            f
        }
    }

    /** Modelo para preencher e importar. */
    fun modelo(ctx: Context): File {
        val hoje = Calendar.getInstance()
        fun dia(offset: Int): String {
            val c = hoje.clone() as Calendar
            c.add(Calendar.DAY_OF_MONTH, -offset)
            return dF.format(c.time)
        }
        val exemplos = listOf(
            CABECALHO,
            listOf("Gasto", dia(2), "12:30", "Restaurante do Zé", "Alimentação", Dinheiro(3590), null, null, null, "Débito", "Não"),
            listOf("Gasto", dia(1), "", "Supermercado", "Mercado", Dinheiro(18740), null, null, null, "Pix", "Não"),
            listOf("Gasto", dia(0), "", "Geladeira (Casas Bahia)", "Casa e contas", Dinheiro(25000), 4, 10, "Sim", "Crédito", "Não", "Banco do Brasil 2332"),
            listOf("Gasto", dia(6), "", "Aluguel", "Casa e contas", Dinheiro(120000), null, null, null, "Boleto", "Sim"),
            listOf("Entrada", dia(5), "", "Salário da empresa", "Salário", Dinheiro(320000), null, null, null),
            listOf("Entrada", dia(3), "", "Pix da Maria", "Pix recebido", Dinheiro(5000), null, null, null),
        )
        val instrucoes = listOf(
            listOf("COMO PREENCHER", ""),
            listOf("Coluna", "O que colocar"),
            listOf("Tipo", "Gasto ou Entrada. Se deixar vazio, conta como Gasto."),
            listOf("Data", "Obrigatória. Ex.: 15/11/2026. Para compra parcelada: a data da COMPRA (1ª parcela). Para gasto fixo: um dia em que venceu (o app repete nesse dia todo mês)."),
            listOf("Hora", "Opcional. Ex.: 14:30"),
            listOf("Descrição", "Onde gastou ou de onde veio o dinheiro. Ex.: Padaria, Salário"),
            listOf("Categoria", "Opcional. Use uma categoria do app (ex.: Alimentação). Se não existir, o app cria. Vazio = o app tenta adivinhar."),
            listOf("Valor (R$)", "Obrigatório. Ex.: 25,90 (para parcelado, o valor de UMA parcela)"),
            listOf("Parcela atual", "Só para compra parcelada: a parcela deste mês. Ex.: comprou em 10 vezes em julho, em novembro é a 5."),
            listOf("Total de parcelas", "Só para compra parcelada: em quantas vezes. Ex.: 10"),
            listOf("Criar próximas parcelas?", "Sim = o app cria sozinho as parcelas que faltam nos próximos meses. Não = lança só esta linha."),
            listOf("Forma de pagamento", "Opcional: Crédito, Débito, Pix, Dinheiro ou Boleto."),
            listOf("Fixo?", "Sim = conta que repete todo mês (aluguel, internet…). O app passa a lançar sozinho no mesmo dia, todo mês."),
            listOf("Cartão", "Opcional: banco e final do cartão. Ex.: Banco do Brasil 2332 ou Nubank 7592."),
            listOf("", ""),
            listOf("Dicas", ""),
            listOf("1", "Apague as linhas de exemplo antes de importar (ou mantenha, se quiser testar)."),
            listOf("2", "Não mude os nomes da primeira linha (cabeçalho)."),
            listOf("3", "Pode salvar em Excel (.xlsx) ou CSV. Pelo Google Planilhas: Arquivo > Fazer download > Microsoft Excel."),
            listOf("4", "Importar de novo o mesmo arquivo não duplica: linhas iguais são ignoradas."),
            listOf("", ""),
            listOf("Categorias do app", Cats.list.joinToString(", ") { it.name }),
            listOf("Tipos de entrada", "Salário, Extra, Pix recebido, Depósito, Transferência, Outros"),
        )
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "modelo_meus_gastos.xlsx")
        f.outputStream().use {
            Xlsx.write(it, listOf(Xlsx.Sheet("Lançamentos", exemplos, LARGURAS), Xlsx.Sheet("Como preencher", instrucoes, listOf(26, 90))))
        }
        return f
    }

    fun compartilhar(ctx: Context, f: File, assunto: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val mime = if (f.name.endsWith(".xlsx")) MIME_XLSX else MIME_CSV
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, assunto)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Enviar para WhatsApp, e-mail, Drive…"))
    }

    fun salvarEm(ctx: Context, f: File, destino: Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(destino)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        true
    } catch (_: Exception) {
        false
    }

    // ------------------------------------------------------------ Importar

    data class Resultado(
        val gastos: Int,
        val entradas: Int,
        val repetidas: Int,
        val erros: List<String>,
        val reconhecido: Boolean,
        val removidos: Int = 0,
        val atualizadas: Int = 0,
    )

    private fun n(s: String) = Categorizer.norm(s).trim()

    private fun lerTabela(bytes: ByteArray): List<List<String>> =
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) Xlsx.read(bytes) else CsvTable.read(bytes)

    private fun ehCabecalho(cells: List<String>): Boolean {
        val c = cells.map { n(it) }
        return c.any { it.startsWith("data") } && c.any { it.startsWith("valor") }
    }

    private fun mapear(cells: List<String>): Map<String, Int> {
        val m = HashMap<String, Int>()
        cells.forEachIndexed { i, raw ->
            val h = n(raw)
            val k = when {
                h.startsWith("tipo") -> "tipo"
                h.startsWith("data") -> "data"
                h.startsWith("hora") -> "hora"
                h.startsWith("descri") || h.startsWith("estabelecimento") || h.startsWith("origem") -> "desc"
                h.startsWith("categoria") -> "cat"
                h.startsWith("valor") -> "valor"
                h.startsWith("parcela atual") || h == "parcela" -> "atual"
                h.startsWith("total de parcelas") || h == "parcelas" || h.startsWith("qtd") -> "total"
                h.startsWith("criar") || h.startsWith("gerar") -> "criar"
                h.startsWith("forma") || h.startsWith("pagamento") || h.startsWith("meio") -> "pay"
                h.startsWith("fixo") || h.startsWith("recorrente") -> "fixo"
                h.startsWith("cartao") -> "card"
                else -> null
            }
            if (k != null && !m.containsKey(k)) m[k] = i
        }
        return m
    }

    private val formatosData = listOf("dd/MM/yyyy", "dd/MM/yy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy", "d/M/yyyy")

    private fun lerData(data: String, hora: String): Long? {
        val d = data.trim()
        if (d.isEmpty()) return null
        val c = Calendar.getInstance()
        val serial = d.replace(",", ".").toDoubleOrNull()
        if (serial != null && serial > 20000 && serial < 80000) {
            // Data do Excel (número de dias desde 30/12/1899)
            c.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.DAY_OF_MONTH, serial.toInt())
            val frac = serial - serial.toInt()
            if (frac > 0) c.add(Calendar.MINUTE, (frac * 24 * 60).roundToInt()) else c.set(Calendar.HOUR_OF_DAY, 12)
        } else {
            val base = d.substringBefore(' ')
            var ok = false
            for (f in formatosData) {
                val fmt = SimpleDateFormat(f, ptBR).apply { isLenient = false }
                val t = try { fmt.parse(base) } catch (_: Exception) { null }
                if (t != null) { c.time = t; ok = true; break }
            }
            if (!ok) return null
            c.set(Calendar.HOUR_OF_DAY, 12)
            c.set(Calendar.MINUTE, 0)
        }
        // Hora
        val h = hora.trim()
        val hm = Regex("""^(\d{1,2})[:h](\d{2})""").find(h)
        val hFrac = h.replace(",", ".").toDoubleOrNull()
        when {
            hm != null -> {
                c.set(Calendar.HOUR_OF_DAY, hm.groupValues[1].toInt().coerceIn(0, 23))
                c.set(Calendar.MINUTE, hm.groupValues[2].toInt().coerceIn(0, 59))
            }
            hFrac != null && hFrac in 0.0..0.9999 -> {
                val mins = (hFrac * 24 * 60).roundToInt()
                c.set(Calendar.HOUR_OF_DAY, mins / 60)
                c.set(Calendar.MINUTE, mins % 60)
            }
        }
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun lerValor(s: String): Long? {
        val t = s.trim().replace("R$", "").replace(" ", "").replace(" ", "").removePrefix("-").removePrefix("−")
        if (t.isEmpty()) return null
        return parseCents(t)?.takeIf { it > 0 }
    }

    private fun tipoEntrada(s: String): String {
        val x = n(s)
        return when {
            x.startsWith("salar") -> "Salário"
            x.startsWith("extra") -> "Extra"
            x.startsWith("pix") -> "Pix recebido"
            x.startsWith("deposit") -> "Depósito"
            x.startsWith("transf") -> "Transferência"
            else -> "Outros"
        }
    }

    /** Confere se a planilha tem cabeçalho e pelo menos uma linha válida (antes de apagar qualquer coisa). */
    private fun temLinhaValida(rows: List<List<String>>): Boolean {
        var mapa: Map<String, Int>? = null
        for (row in rows) {
            val cells = row.map { it.trim() }
            if (cells.all { it.isEmpty() }) continue
            if (ehCabecalho(cells)) { mapa = mapear(cells); continue }
            val m = mapa ?: continue
            val d = m["data"]?.let { cells.getOrNull(it) } ?: ""
            val h = m["hora"]?.let { cells.getOrNull(it) } ?: ""
            val v = m["valor"]?.let { cells.getOrNull(it) } ?: ""
            if (lerData(d, h) != null && lerValor(v) != null) return true
        }
        return false
    }

    /**
     * @param fonte     marca dos registros criados ("importado" ou "backup")
     * @param substituir true = apaga antes tudo o que veio de importações anteriores com a mesma marca
     */
    fun importar(ctx: Context, db: Db, uri: Uri, fonte: String = "importado", substituir: Boolean = false): Resultado {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Resultado(0, 0, 0, listOf("Não consegui abrir o arquivo."), false)
        val rows = try {
            lerTabela(bytes)
        } catch (_: Exception) {
            return Resultado(0, 0, 0, listOf("O arquivo não é uma planilha válida (use .xlsx ou .csv)."), false)
        }

        val removidos = if (substituir && temLinhaValida(rows)) db.deleteBySource(fonte) else 0

        var mapa: Map<String, Int>? = null
        var tipoPadrao = "gasto"
        var reconhecido = false
        var gastos = 0
        var entradas = 0
        var repetidas = 0
        var atualizadas = 0
        val erros = ArrayList<String>()
        val categorias = db.categoryNames().associateBy { n(it) }.toMutableMap()
        val planos = HashMap<String, Long>()

        rows.forEachIndexed { idx, row ->
            val linha = idx + 1
            val cells = row.map { it.trim() }
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            val primeira = n(cells.firstOrNull() ?: "")
            if (primeira == "resumo") { mapa = null; return@forEachIndexed }
            if (primeira == "entradas") { tipoPadrao = "entrada"; mapa = null; return@forEachIndexed }
            if (ehCabecalho(cells)) {
                mapa = mapear(cells)
                reconhecido = true
                return@forEachIndexed
            }
            val m = mapa ?: return@forEachIndexed
            fun cel(k: String): String = m[k]?.let { cells.getOrNull(it) } ?: ""

            val data = lerData(cel("data"), cel("hora"))
            val valor = lerValor(cel("valor"))
            if (data == null) { erros.add("Linha $linha: data inválida (\"${cel("data")}\")"); return@forEachIndexed }
            if (valor == null) { erros.add("Linha $linha: valor inválido (\"${cel("valor")}\")"); return@forEachIndexed }

            val tipoTxt = n(cel("tipo"))
            val ehEntrada = when {
                tipoTxt.startsWith("entrada") || tipoTxt.startsWith("receita") -> true
                tipoTxt.startsWith("gasto") || tipoTxt.startsWith("despesa") || tipoTxt.startsWith("saida") -> false
                else -> tipoPadrao == "entrada"
            }

            if (ehEntrada) {
                val kind = tipoEntrada(cel("cat").ifBlank { cel("tipo") })
                val desc = cel("desc").ifBlank { kind }
                if (db.existsIncome(valor, desc, data)) { repetidas++; return@forEachIndexed }
                db.insertIncome(Income(0, valor, desc, kind, data, fonte))
                entradas++
                return@forEachIndexed
            }

            // Gasto
            val catTxt = cel("cat")
            val desc = cel("desc").ifBlank { catTxt.ifBlank { "Gasto" } }
            val categoria: String? = when {
                catTxt.isBlank() -> Categorizer.categorize(db, desc)
                n(catTxt) == "pendente" -> null
                categorias.containsKey(n(catTxt)) -> categorias[n(catTxt)]
                else -> {
                    db.saveCategory(Category(catTxt, "📦", 0xFF78858A, 0), null)
                    categorias[n(catTxt)] = catTxt
                    catTxt
                }
            }
            val pay = Pagamento.normalizar(cel("pay"))
            val card = cel("card").let { c ->
                val dig = Regex("""(\d{4})\s*$""").find(c)?.groupValues?.get(1)
                when {
                    c.isBlank() -> ""
                    c.contains('•') || dig == null -> c
                    else -> c.removeSuffix(dig).trim().removeSuffix("final").trim().let { b -> if (b.isBlank()) "•$dig" else "$b •$dig" }
                }
            }

            // Parcelas: aceita "4" e "10" em colunas separadas, ou "4/10" numa coluna só
            var atual = cel("atual").substringBefore('.').toIntOrNull() ?: 0
            var total = cel("total").substringBefore('.').toIntOrNull() ?: 0
            val barra = Regex("""(\d+)\s*/\s*(\d+)""").find(cel("atual"))
            if (barra != null) { atual = barra.groupValues[1].toInt(); total = barra.groupValues[2].toInt() }
            val criar = n(cel("criar")).startsWith("s")
            val ehFixo = n(cel("fixo")).startsWith("s")

            // Compra parcelada com "Criar próximas parcelas = Sim": substitui a versão antiga da mesma compra
            // (assim, corrigir a planilha e importar de novo sempre arruma os meses).
            if (!ehFixo && criar && total in 2..48) {
                atual = atual.coerceIn(1, total)
                if (db.deleteSamePurchase(desc, valor, total) > 0) atualizadas++
                // Data = data da COMPRA (1ª parcela): a parcela atual cai (atual - 1) meses depois.
                // Se isso der muito no futuro, entende a data como o pagamento da parcela atual.
                val naParcelaAtual = Calendar.getInstance().apply { timeInMillis = data; add(Calendar.MONTH, atual - 1) }.timeInMillis
                val base = if (naParcelaAtual <= System.currentTimeMillis() + 31L * Dates.DAY_MS) naParcelaAtual else data
                Installments.register(db, desc, categoria, base, fonte, valor, total, from = atual, pay = pay, card = card)
                gastos++
                return@forEachIndexed
            }

            if (db.exists(valor, desc, data)) { repetidas++; return@forEachIndexed }

            // Gasto fixo: liga a um fixo existente (mesmo nome e valor) ou cria um novo
            if (ehFixo) {
                val ym = Recorrentes.ym(data)
                val existente = db.recurring().firstOrNull { n(it.merchant) == n(desc) && it.cents == valor }
                if (existente == null) {
                    Recorrentes.criar(db, valor, desc, categoria, data, pay, fonte, card)
                } else {
                    db.insertExpense(Expense(0, valor, desc, categoria, data, fonte, pay = pay, fixedId = existente.id, card = card))
                    if (ym > existente.lastYm) db.updateRecurring(existente.copy(lastYm = ym))
                }
                gastos++
                return@forEachIndexed
            }

            if (total in 2..48) {
                atual = atual.coerceIn(1, total)
                run {
                    val chave = "$desc|$total|$valor"
                    val plano = planos.getOrPut(chave) { db.nextPlanId() }
                    db.insertExpense(Expense(0, valor, desc, categoria, data, fonte, plano, atual, total, pay = pay, card = card))
                }
            } else {
                db.insertExpense(Expense(0, valor, desc, categoria, data, fonte, pay = pay, card = card))
            }
            gastos++
        }
        return Resultado(gastos, entradas, repetidas, erros, reconhecido, removidos, atualizadas)
    }
}
