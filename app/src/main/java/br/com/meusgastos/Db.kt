package br.com.meusgastos

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Um gasto. category == null significa "pendente" (ainda sem categoria).
 * Compras parceladas: cada parcela é um gasto no seu mês, com o mesmo planId (instNum de instTotal).
 */
data class Expense(
    val id: Long,
    val cents: Long,
    val merchant: String,
    val category: String?,
    val dateMs: Long,
    val source: String,
    val planId: Long = 0,
    val instNum: Int = 0,
    val instTotal: Int = 0,
    val pay: String = "",
    val fixedId: Long = 0,
    /** Cartão usado, ex.: "Banco do Brasil •2332" (vazio = não informado). */
    val card: String = "",
)

/** Gasto fixo: repete todo mês no dia [day]. lastYm = último mês já lançado (ano*12+mês). */
data class Recurring(
    val id: Long,
    val cents: Long,
    val merchant: String,
    val category: String?,
    val day: Int,
    val pay: String,
    val active: Boolean,
    val startMs: Long,
    val lastYm: Int,
    val source: String = "manual",
)

/** Uma entrada de dinheiro (salário, Pix recebido, depósito...). */
data class Income(
    val id: Long,
    val cents: Long,
    val desc: String,
    val kind: String,
    val dateMs: Long,
    val source: String,
    /** Fonte que enviou (CNPJ ou nome), para reconhecer o salário. */
    val payer: String = "",
    /** Mês que esta entrada paga (ano*12+mês). 0 = o próprio mês em que caiu. */
    val compYm: Int = 0,
)

/**
 * Fonte de entrada (quem envia dinheiro). status: -1 = perguntar se é salário, 0 = não é, 1 = é salário.
 * baseCents = salário líquido normal; o que vier acima disso vira "Extra".
 */
data class Payer(
    val key: String,
    val name: String,
    val cnpj: String,
    val status: Int,
    val baseCents: Long,
    val lastCents: Long,
    /** -1 = o salário paga o mês anterior (cai em novembro, paga outubro); 0 = paga o mesmo mês. */
    val refOffset: Int = -1,
)

/** Categoria (as padrão e as que você criar). color = ARGB, ex.: 0xFFF4A261 */
data class Category(val name: String, val emoji: String, val color: Long, val pos: Int)

/** Texto de notificação recebido (para conferência e melhoria do leitor). */
data class LogEntry(val id: Long, val pkg: String, val text: String, val dateMs: Long, val parsed: Boolean)

/**
 * Banco de dados local (fica só no celular).
 * Regra de segurança: só apaga um gasto quando VOCÊ toca em "Apagar".
 */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "gastos.db", null, 7) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, cents INTEGER NOT NULL, " +
                "merchant TEXT NOT NULL, category TEXT, date_ms INTEGER NOT NULL, source TEXT NOT NULL, " +
                "plan_id INTEGER NOT NULL DEFAULT 0, inst_num INTEGER NOT NULL DEFAULT 0, inst_total INTEGER NOT NULL DEFAULT 0, " +
                "pay TEXT NOT NULL DEFAULT '', fixed_id INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE rules (keyword TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE raw_log (id INTEGER PRIMARY KEY AUTOINCREMENT, pkg TEXT NOT NULL, " +
                "text TEXT NOT NULL, date_ms INTEGER NOT NULL, parsed INTEGER NOT NULL)"
        )
        createV2(db)
        createV3(db)
        createV4(db)
        createV5(db)
        createV6(db)
        createV7(db)
    }

    /** Atualização da versão 1 para a 2: nada é apagado, só são adicionadas colunas e tabelas. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN plan_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expenses ADD COLUMN inst_num INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expenses ADD COLUMN inst_total INTEGER NOT NULL DEFAULT 0")
            createV2(db)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN pay TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expenses ADD COLUMN fixed_id INTEGER NOT NULL DEFAULT 0")
            createV3(db)
        }
        if (oldVersion < 4) createV4(db)
        if (oldVersion < 5) createV5(db)
        if (oldVersion < 6) createV6(db)
        if (oldVersion < 7) createV7(db)
    }

    /** Mês de referência das entradas (o salário de novembro pode pagar outubro). */
    private fun createV7(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE incomes ADD COLUMN comp_ym INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE incomes SET comp_ym = CAST(strftime('%Y', date_ms / 1000, 'unixepoch', 'localtime') AS INTEGER) * 12 + " +
                "CAST(strftime('%m', date_ms / 1000, 'unixepoch', 'localtime') AS INTEGER) - 1"
        )
        db.execSQL("ALTER TABLE payers ADD COLUMN ref_offset INTEGER NOT NULL DEFAULT -1")
    }

    private fun createV6(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE incomes ADD COLUMN payer TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS payers (pkey TEXT PRIMARY KEY, name TEXT NOT NULL, cnpj TEXT NOT NULL DEFAULT '', " +
                "status INTEGER NOT NULL DEFAULT -1, base_cents INTEGER NOT NULL DEFAULT 0, last_cents INTEGER NOT NULL DEFAULT 0)"
        )
    }

    private fun createV5(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN card TEXT NOT NULL DEFAULT ''")
    }

    /** Marca de onde veio cada gasto fixo (para poder substituir uma importação). */
    private fun createV4(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
    }

    private fun createV3(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recurring (id INTEGER PRIMARY KEY AUTOINCREMENT, cents INTEGER NOT NULL, " +
                "merchant TEXT NOT NULL, category TEXT, day INTEGER NOT NULL, pay TEXT NOT NULL DEFAULT '', " +
                "active INTEGER NOT NULL DEFAULT 1, start_ms INTEGER NOT NULL, last_ym INTEGER NOT NULL DEFAULT 0)"
        )
    }

    private fun createV2(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS incomes (id INTEGER PRIMARY KEY AUTOINCREMENT, cents INTEGER NOT NULL, " +
                "descr TEXT NOT NULL, kind TEXT NOT NULL, date_ms INTEGER NOT NULL, source TEXT NOT NULL)"
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS limits (category TEXT PRIMARY KEY, cents INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY, emoji TEXT NOT NULL, " +
                "color INTEGER NOT NULL, pos INTEGER NOT NULL)"
        )
        DEFAULT_CATEGORIES.forEachIndexed { i, (n, e, c) ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, emoji, color, pos) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(n, e, c, i)
            )
        }
    }

    // ---------- Gastos ----------

    private fun values(e: Expense): ContentValues {
        val cv = ContentValues()
        cv.put("cents", e.cents)
        cv.put("merchant", e.merchant)
        cv.put("category", e.category)
        cv.put("date_ms", e.dateMs)
        cv.put("source", e.source)
        cv.put("plan_id", e.planId)
        cv.put("inst_num", e.instNum)
        cv.put("inst_total", e.instTotal)
        cv.put("pay", e.pay)
        cv.put("fixed_id", e.fixedId)
        cv.put("card", e.card)
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
                        planId = it.getLong(6),
                        instNum = it.getInt(7),
                        instTotal = it.getInt(8),
                        pay = it.getString(9) ?: "",
                        fixedId = it.getLong(10),
                        card = it.getString(11) ?: "",
                    )
                )
            }
        }
        return out
    }

    private val cols = "id, cents, merchant, category, date_ms, source, plan_id, inst_num, inst_total, pay, fixed_id, card"

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
            "SELECT $cols FROM expenses WHERE category IS NULL ORDER BY date_ms ASC", null
        )
    )

    /** Conta compras pendentes (uma compra parcelada conta uma vez só). */
    fun pendingCount(): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT CASE WHEN plan_id > 0 THEN 'p' || plan_id ELSE 'e' || id END) " +
                "FROM expenses WHERE category IS NULL", null
        ).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun sumBetween(fromMs: Long, toMs: Long): Long = sum(
        "SELECT COALESCE(SUM(cents), 0) FROM expenses WHERE date_ms >= ? AND date_ms < ?",
        arrayOf(fromMs.toString(), toMs.toString())
    )

    fun sumCategory(fromMs: Long, toMs: Long, category: String): Long = sum(
        "SELECT COALESCE(SUM(cents), 0) FROM expenses WHERE date_ms >= ? AND date_ms < ? AND category = ?",
        arrayOf(fromMs.toString(), toMs.toString(), category)
    )

    /** Quanto de parcelas já está marcado para um período. */
    fun sumInstallments(fromMs: Long, toMs: Long): Long = sum(
        "SELECT COALESCE(SUM(cents), 0) FROM expenses WHERE plan_id > 0 AND date_ms >= ? AND date_ms < ?",
        arrayOf(fromMs.toString(), toMs.toString())
    )

    private fun sum(sql: String, args: Array<String>): Long {
        readableDatabase.rawQuery(sql, args).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun setCategory(id: Long, category: String) {
        writableDatabase.execSQL("UPDATE expenses SET category = ? WHERE id = ?", arrayOf<Any>(category, id))
    }

    fun setCategoryPlan(planId: Long, category: String) {
        if (planId <= 0) return
        writableDatabase.execSQL("UPDATE expenses SET category = ? WHERE plan_id = ?", arrayOf<Any>(category, planId))
    }

    fun delete(id: Long) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    fun deletePlan(planId: Long) {
        if (planId <= 0) return
        writableDatabase.delete("expenses", "plan_id = ?", arrayOf(planId.toString()))
    }

    /** Apaga as parcelas de uma mesma compra (mesmo nome, valor da parcela e total de parcelas). */
    fun deleteSamePurchase(merchant: String, cents: Long, total: Int): Int =
        writableDatabase.delete(
            "expenses", "merchant = ? AND cents = ? AND inst_total = ? AND inst_total > 1",
            arrayOf(merchant, cents.toString(), total.toString())
        )

    /** Cartões já usados (para escolher rápido). */
    fun cards(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT card, COUNT(*) n FROM expenses WHERE card <> '' GROUP BY card ORDER BY n DESC", null
        ).use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    /** Dá um apelido para um cartão (muda em todos os gastos dele). */
    fun renameCard(old: String, new: String) {
        writableDatabase.execSQL("UPDATE expenses SET card = ? WHERE card = ?", arrayOf<Any>(new, old))
    }

    fun nextPlanId(): Long = sum("SELECT COALESCE(MAX(plan_id), 0) + 1 FROM expenses", arrayOf())

    /** Evita contar duas vezes a mesma notificação (alguns bancos repetem). */
    fun hasRecent(cents: Long, source: String, sinceMs: Long): Boolean = sum(
        "SELECT COUNT(*) FROM expenses WHERE cents = ? AND source = ? AND date_ms >= ?",
        arrayOf(cents.toString(), source, sinceMs.toString())
    ) > 0

    /** Usado ao restaurar backup: não duplica o que já existe. */
    fun exists(cents: Long, merchant: String, dateMs: Long): Boolean = sum(
        "SELECT COUNT(*) FROM expenses WHERE cents = ? AND merchant = ? AND date_ms >= ? AND date_ms < ?",
        arrayOf(cents.toString(), merchant, (dateMs - 60_000).toString(), (dateMs + 60_000).toString())
    ) > 0

    // ---------- Gastos fixos ----------

    fun insertRecurring(r: Recurring): Long {
        val cv = ContentValues()
        cv.put("cents", r.cents)
        cv.put("merchant", r.merchant)
        cv.put("category", r.category)
        cv.put("day", r.day)
        cv.put("pay", r.pay)
        cv.put("active", if (r.active) 1 else 0)
        cv.put("start_ms", r.startMs)
        cv.put("last_ym", r.lastYm)
        cv.put("source", r.source)
        return writableDatabase.insert("recurring", null, cv)
    }

    fun updateRecurring(r: Recurring) {
        val cv = ContentValues()
        cv.put("cents", r.cents)
        cv.put("merchant", r.merchant)
        cv.put("category", r.category)
        cv.put("day", r.day)
        cv.put("pay", r.pay)
        cv.put("active", if (r.active) 1 else 0)
        cv.put("last_ym", r.lastYm)
        writableDatabase.update("recurring", cv, "id = ?", arrayOf(r.id.toString()))
    }

    fun recurring(): List<Recurring> {
        val out = ArrayList<Recurring>()
        readableDatabase.rawQuery(
            "SELECT id, cents, merchant, category, day, pay, active, start_ms, last_ym FROM recurring ORDER BY day, merchant", null
        ).use {
            while (it.moveToNext()) {
                out.add(
                    Recurring(
                        it.getLong(0), it.getLong(1), it.getString(2), if (it.isNull(3)) null else it.getString(3),
                        it.getInt(4), it.getString(5) ?: "", it.getInt(6) == 1, it.getLong(7), it.getInt(8),
                    )
                )
            }
        }
        return out
    }

    fun deleteRecurring(id: Long) {
        writableDatabase.delete("recurring", "id = ?", arrayOf(id.toString()))
        writableDatabase.execSQL("UPDATE expenses SET fixed_id = 0 WHERE fixed_id = ?", arrayOf<Any>(id))
    }

    fun hasFixedIn(fixedId: Long, fromMs: Long, toMs: Long): Boolean = sum(
        "SELECT COUNT(*) FROM expenses WHERE fixed_id = ? AND date_ms >= ? AND date_ms < ?",
        arrayOf(fixedId.toString(), fromMs.toString(), toMs.toString())
    ) > 0

    fun sumFixed(fromMs: Long, toMs: Long): Long = sum(
        "SELECT COALESCE(SUM(cents), 0) FROM expenses WHERE fixed_id > 0 AND date_ms >= ? AND date_ms < ?",
        arrayOf(fromMs.toString(), toMs.toString())
    )

    /** Gasto fixo lançado sozinho pelo app que ainda não foi confirmado por uma notificação do banco. */
    fun findAutoFixed(cents: Long, fromMs: Long, toMs: Long): Expense? = readExpenses(
        readableDatabase.rawQuery(
            "SELECT $cols FROM expenses WHERE fixed_id > 0 AND source = 'fixo' AND cents = ? AND date_ms >= ? AND date_ms < ? LIMIT 1",
            arrayOf(cents.toString(), fromMs.toString(), toMs.toString())
        )
    ).firstOrNull()

    /**
     * Apaga tudo o que veio de uma origem (ex.: "importado"): gastos, entradas e gastos fixos
     * criados por ela, com os lançamentos automáticos desses fixos. Retorna quantos registros saíram.
     */
    fun deleteBySource(source: String): Int {
        val w = writableDatabase
        var n = 0
        w.beginTransaction()
        try {
            val ids = ArrayList<Long>()
            w.rawQuery("SELECT id FROM recurring WHERE source = ?", arrayOf(source)).use {
                while (it.moveToNext()) ids.add(it.getLong(0))
            }
            for (id in ids) {
                n += w.delete("expenses", "fixed_id = ? AND source IN ('fixo', ?)", arrayOf(id.toString(), source))
                w.delete("recurring", "id = ?", arrayOf(id.toString()))
                w.execSQL("UPDATE expenses SET fixed_id = 0 WHERE fixed_id = ?", arrayOf<Any>(id))
            }
            n += w.delete("expenses", "source = ?", arrayOf(source))
            n += w.delete("incomes", "source = ?", arrayOf(source))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return n
    }

    fun countBySource(source: String): Int = (
        sum("SELECT COUNT(*) FROM expenses WHERE source = ?", arrayOf(source)) +
            sum("SELECT COUNT(*) FROM incomes WHERE source = ?", arrayOf(source))
        ).toInt()

    // ---------- Entradas (receitas) ----------

    fun insertIncome(i: Income): Long {
        val cv = ContentValues()
        cv.put("cents", i.cents)
        cv.put("descr", i.desc)
        cv.put("kind", i.kind)
        cv.put("date_ms", i.dateMs)
        cv.put("source", i.source)
        cv.put("payer", i.payer)
        cv.put("comp_ym", if (i.compYm != 0) i.compYm else Recorrentes.ym(i.dateMs))
        return writableDatabase.insert("incomes", null, cv)
    }

    fun updateIncome(i: Income) {
        val cv = ContentValues()
        cv.put("cents", i.cents)
        cv.put("descr", i.desc)
        cv.put("kind", i.kind)
        if (i.compYm != 0) cv.put("comp_ym", i.compYm)
        writableDatabase.update("incomes", cv, "id = ?", arrayOf(i.id.toString()))
    }

    fun incomesByPayer(key: String): List<Income> {
        val out = ArrayList<Income>()
        readableDatabase.rawQuery(
            "SELECT id, cents, descr, kind, date_ms, source, payer, comp_ym FROM incomes WHERE payer = ? ORDER BY date_ms",
            arrayOf(key)
        ).use {
            while (it.moveToNext()) out.add(Income(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5), it.getString(6), it.getInt(7)))
        }
        return out
    }

    /** Entradas que pagam o mês [ym] (pelo mês de referência, não pela data em que caíram). */
    fun incomesMonth(ym: Int): List<Income> = incomesWhere("comp_ym = ?", arrayOf(ym.toString()))

    fun sumIncomeMonth(ym: Int): Long = sum("SELECT COALESCE(SUM(cents), 0) FROM incomes WHERE comp_ym = ?", arrayOf(ym.toString()))

    /** Entradas que caíram no período mas pagam outro mês. */
    fun incomesForOtherMonth(fromMs: Long, toMs: Long, ym: Int): List<Income> =
        incomesWhere("date_ms >= ? AND date_ms < ? AND comp_ym <> ?", arrayOf(fromMs.toString(), toMs.toString(), ym.toString()))

    private fun incomesWhere(where: String, args: Array<String>): List<Income> {
        val out = ArrayList<Income>()
        readableDatabase.rawQuery(
            "SELECT id, cents, descr, kind, date_ms, source, payer, comp_ym FROM incomes WHERE $where ORDER BY date_ms DESC", args
        ).use {
            while (it.moveToNext()) out.add(Income(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5), it.getString(6) ?: "", it.getInt(7)))
        }
        return out
    }

    // ---------- Fontes de entrada (salário) ----------

    fun payer(key: String): Payer? = payers("WHERE pkey = ?", arrayOf(key)).firstOrNull()
    fun payersPending(): List<Payer> = payers("WHERE status = -1", arrayOf())
    fun salaryPayers(): List<Payer> = payers("WHERE status = 1", arrayOf())

    private fun payers(where: String, args: Array<String>): List<Payer> {
        val out = ArrayList<Payer>()
        readableDatabase.rawQuery("SELECT pkey, name, cnpj, status, base_cents, last_cents, ref_offset FROM payers $where ORDER BY name", args).use {
            while (it.moveToNext()) out.add(Payer(it.getString(0), it.getString(1), it.getString(2), it.getInt(3), it.getLong(4), it.getLong(5), it.getInt(6)))
        }
        return out
    }

    fun savePayer(p: Payer) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO payers (pkey, name, cnpj, status, base_cents, last_cents, ref_offset) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(p.key, p.name, p.cnpj, p.status, p.baseCents, p.lastCents, p.refOffset)
        )
    }

    fun incomes(fromMs: Long, toMs: Long): List<Income> {
        val out = ArrayList<Income>()
        readableDatabase.rawQuery(
            "SELECT id, cents, descr, kind, date_ms, source, payer, comp_ym FROM incomes WHERE date_ms >= ? AND date_ms < ? ORDER BY date_ms DESC",
            arrayOf(fromMs.toString(), toMs.toString())
        ).use {
            while (it.moveToNext()) {
                out.add(Income(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getLong(4), it.getString(5), it.getString(6) ?: "", it.getInt(7)))
            }
        }
        return out
    }

    fun allIncomes(): List<Income> = incomes(0, Long.MAX_VALUE)

    fun sumIncome(fromMs: Long, toMs: Long): Long = sum(
        "SELECT COALESCE(SUM(cents), 0) FROM incomes WHERE date_ms >= ? AND date_ms < ?",
        arrayOf(fromMs.toString(), toMs.toString())
    )

    fun deleteIncome(id: Long) {
        writableDatabase.delete("incomes", "id = ?", arrayOf(id.toString()))
    }

    fun hasRecentIncome(cents: Long, source: String, sinceMs: Long): Boolean = sum(
        "SELECT COUNT(*) FROM incomes WHERE cents = ? AND source = ? AND date_ms >= ?",
        arrayOf(cents.toString(), source, sinceMs.toString())
    ) > 0

    fun existsIncome(cents: Long, desc: String, dateMs: Long): Boolean = sum(
        "SELECT COUNT(*) FROM incomes WHERE cents = ? AND descr = ? AND date_ms >= ? AND date_ms < ?",
        arrayOf(cents.toString(), desc, (dateMs - 60_000).toString(), (dateMs + 60_000).toString())
    ) > 0

    // ---------- Limites por categoria ----------

    fun limits(): Map<String, Long> {
        val out = HashMap<String, Long>()
        readableDatabase.rawQuery("SELECT category, cents FROM limits", null).use {
            while (it.moveToNext()) out[it.getString(0)] = it.getLong(1)
        }
        return out
    }

    fun setLimit(category: String, cents: Long) {
        if (cents <= 0) {
            writableDatabase.delete("limits", "category = ?", arrayOf(category))
        } else {
            writableDatabase.execSQL(
                "INSERT OR REPLACE INTO limits (category, cents) VALUES (?, ?)", arrayOf<Any>(category, cents)
            )
        }
    }

    // ---------- Categorias ----------

    fun categories(): List<Category> {
        val out = ArrayList<Category>()
        readableDatabase.rawQuery("SELECT name, emoji, color, pos FROM categories ORDER BY pos, name", null).use {
            while (it.moveToNext()) out.add(Category(it.getString(0), it.getString(1), it.getLong(2), it.getInt(3)))
        }
        return out
    }

    fun categoryNames(): Set<String> = categories().map { it.name }.toSet()

    /** Cria ou altera uma categoria. Se mudou o nome, atualiza os gastos, regras e limites. */
    fun saveCategory(c: Category, oldName: String?) {
        val w = writableDatabase
        w.beginTransaction()
        try {
            if (oldName != null && oldName != c.name) {
                w.execSQL("UPDATE categories SET name = ? WHERE name = ?", arrayOf<Any>(c.name, oldName))
                w.execSQL("UPDATE expenses SET category = ? WHERE category = ?", arrayOf<Any>(c.name, oldName))
                w.execSQL("UPDATE rules SET category = ? WHERE category = ?", arrayOf<Any>(c.name, oldName))
                w.execSQL("UPDATE OR REPLACE limits SET category = ? WHERE category = ?", arrayOf<Any>(c.name, oldName))
            }
            val pos = if (oldName == null) {
                sum("SELECT COALESCE(MAX(pos), 0) + 1 FROM categories", arrayOf()).toInt()
            } else c.pos
            w.execSQL(
                "INSERT OR REPLACE INTO categories (name, emoji, color, pos) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(c.name, c.emoji, c.color, pos)
            )
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Apaga uma categoria. Os gastos dela vão para "Outros" (nada é perdido). */
    fun deleteCategory(name: String) {
        if (name == FALLBACK_CATEGORY) return
        val w = writableDatabase
        w.beginTransaction()
        try {
            w.execSQL("UPDATE expenses SET category = ? WHERE category = ?", arrayOf<Any>(FALLBACK_CATEGORY, name))
            w.execSQL("DELETE FROM rules WHERE category = ?", arrayOf<Any>(name))
            w.execSQL("DELETE FROM limits WHERE category = ?", arrayOf<Any>(name))
            w.execSQL("DELETE FROM categories WHERE name = ?", arrayOf<Any>(name))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
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
        const val FALLBACK_CATEGORY = "Outros"

        val DEFAULT_CATEGORIES: List<Triple<String, String, Long>> = listOf(
            Triple("Alimentação", "🍔", 0xFFF4A261),
            Triple("Mercado", "🛒", 0xFF2A9D8F),
            Triple("Transporte", "🚗", 0xFF457B9D),
            Triple("Saúde", "💊", 0xFFE76F51),
            Triple("Casa e contas", "🏠", 0xFF8D6E63),
            Triple("Compras", "🛍️", 0xFF9C4DCC),
            Triple("Lazer", "🎮", 0xFFE5487D),
            Triple("Educação", "📚", 0xFF3F51B5),
            Triple("Pessoas (Pix)", "👤", 0xFF00A5B8),
            Triple("Outros", "📦", 0xFF78858A),
        )

        @Volatile
        private var inst: Db? = null

        fun get(ctx: Context): Db =
            inst ?: synchronized(this) { inst ?: Db(ctx.applicationContext).also { inst = it } }
    }
}
