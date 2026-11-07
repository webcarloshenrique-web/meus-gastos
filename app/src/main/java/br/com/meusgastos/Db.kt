package br.com.meusgastos

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Um gasto. category == null significa "pendente" (ainda sem categoria). */
data class Expense(
    val id: Long,
    val cents: Long,
    val merchant: String,
    val category: String?,
    val dateMs: Long,
    val source: String,
)

/** Texto de notificação recebido (para conferência e melhoria do leitor). */
data class LogEntry(val id: Long, val pkg: String, val text: String, val dateMs: Long, val parsed: Boolean)

/**
 * Banco de dados local (fica só no celular).
 * Regra de segurança: só apaga um gasto quando VOCÊ toca em "Apagar".
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "gastos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, cents INTEGER NOT NULL, " +
                "merchant TEXT NOT NULL, category TEXT, date_ms INTEGER NOT NULL, source TEXT NOT NULL)"
        )
        db.execSQL("CREATE TABLE rules (keyword TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE raw_log (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, " +
                "text TEXT NOT NULL, date_ms INTEGER NOT NULL, parsed INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---------- Gastos ----------

    private fun values(e: Expense): ContentValues {
        val cv = ContentValues()
        cv.put("cents", e.cents)
        cv.put("merchant", e.merchant)
        cv.put("category", e.category)
        cv.put("date_ms", e.dateMs)
        cv.put("source", e.source)
        return cv
    }

    fun insertExpense(e: Expense): Long = writableDatabase.insert("expenses", null, values(e))

    fun update(e: Expense) {
        writableDatabase.update("expenses", values(e), "id = ?", arrayOf(e.id.toString()))
    }

    private fun readExpenses(c: Cursor): List<Expense> {
        val out = ArrayList<Expense>()
        c.use {
            while (it.moveToNext()) {
                out.add(
                    Expense(
                        id = it.getLong(0),
                        cents = it.getLong(1),
                        merchant = it.getString(2),
                        category = if (it.isNull(3)) null else it.getString(3),
                        dateMs = it.getLong(4),
                        source = it.getString(5),
                    )
                )
            }
        }
        return out
    }

    private val cols = "id, cents, merchant, category, date_ms, source"

    fun expenses(fromMs: Long, toMs: Long): List<Expense> = readExpenses(
        readableDatabase.rawQuery(
            "SELECT $cols FROM expenses WHERE date_ms >= ? AND date_ms < ? ORDER BY date_ms DESC",
            arrayOf(fromMs.toString(), toMs.toString())
        )
    )

    fun all(): List<Expense> = readExpenses(
        readableDatabase.rawQuery("SELECT $cols FROM expenses ORDER BY date_ms DESC", null)
    )

    fun pending(): List<Expense> = readExpenses(
        readableDatabase.rawQuery(
            "SELECT $cols FROM expenses WHERE category IS NULL ORDER BY date_ms DESC", null
        )
    )

    fun pendingCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM expenses WHERE category IS NULL", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun sumBetween(fromMs: Long, toMs: Long): Long {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(cents), 0) FROM expenses WHERE date_ms >= ? AND date_ms < ?",
            arrayOf(fromMs.toString(), toMs.toString())
        ).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun setCategory(id: Long, category: String) {
        writableDatabase.execSQL("UPDATE expenses SET category = ? WHERE id = ?", arrayOf<Any>(category, id))
    }

    fun delete(id: Long) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    /** Evita contar duas vezes a mesma notificação (alguns bancos repetem). */
    fun hasRecent(cents: Long, source: String, sinceMs: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM expenses WHERE cents = ? AND source = ? AND date_ms >= ?",
            arrayOf(cents.toString(), source, sinceMs.toString())
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    /** Usado ao restaurar backup: não duplica o que já existe. */
    fun exists(cents: Long, merchant: String, dateMs: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM expenses WHERE cents = ? AND merchant = ? AND date_ms >= ? AND date_ms < ?",
            arrayOf(cents.toString(), merchant, (dateMs - 60_000).toString(), (dateMs + 60_000).toString())
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    // ---------- Regras aprendidas (estabelecimento -> categoria) ----------

    fun addRule(keyword: String, category: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO rules (keyword, category) VALUES (?, ?)",
            arrayOf<Any>(keyword, category)
        )
    }

    fun rules(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT keyword, category FROM rules ORDER BY LENGTH(keyword) DESC", null).use {
            while (it.moveToNext()) out.add(it.getString(0) to it.getString(1))
        }
        return out
    }

    // ---------- Textos recebidos ----------

    fun insertLog(pkg: String, text: String, dateMs: Long, parsed: Boolean) {
        val cv = ContentValues()
        cv.put("pkg", pkg)
        cv.put("text", text)
        cv.put("date_ms", dateMs)
        cv.put("parsed", if (parsed) 1 else 0)
        writableDatabase.insert("raw_log", null, cv)
        writableDatabase.execSQL(
            "DELETE FROM raw_log WHERE id NOT IN (SELECT id FROM raw_log ORDER BY id DESC LIMIT 200)"
        )
    }

    fun logs(limit: Int): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        readableDatabase.rawQuery(
            "SELECT id, pkg, text, date_ms, parsed FROM raw_log ORDER BY id DESC LIMIT $limit", null
        ).use {
            while (it.moveToNext()) {
                out.add(LogEntry(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3), it.getInt(4) == 1))
            }
        }
        return out
    }

    fun clearLogs() {
        writableDatabase.execSQL("DELETE FROM raw_log")
    }

    companion object {
        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}
