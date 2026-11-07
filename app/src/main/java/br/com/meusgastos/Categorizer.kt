package br.com.meusgastos

import java.text.Normalizer

/** Decide a categoria de um gasto pelo nome do estabelecimento, e aprende com você. */
object Categorizer {

    private val defaults: Map<String, List<String>> = mapOf(
        "Alimentação" to listOf(
            "ifood", "rappi", "restaurante", "lanchonete", "padaria", "pizza", "burger", "mcdonald",
            "bk ", "subway", "habibs", "cafe", "cafeteria", "sorveteria", "acai", "churrasc", "bar ", "aiqfome",
        ),
        "Mercado" to listOf(
            "mercado", "supermerc", "atacad", "carrefour", "assai", "pao de acucar", "hortifruti",
            "sacolao", "sams club", "makro", "mercearia", "acougue",
        ),
        "Transporte" to listOf(
            "uber", "99app", "99 tecnologia", "99pop", "posto", "ipiranga", "shell", "petrobras",
            "combust", "estacion", "pedagio", "sem parar", "conectcar", "veloe", "metro", "bilhete unico",
        ),
        "Saúde" to listOf(
            "farmacia", "drogaria", "droga raia", "drogasil", "pacheco", "pague menos", "panvel",
            "hospital", "clinica", "laboratorio", "dentista", "odonto", "otica",
        ),
        "Casa e contas" to listOf(
            "aluguel", "condominio", "enel", "cpfl", "light", "cemig", "copel", "sabesp", "cedae",
            "comgas", "energia", "saneamento", "claro", "vivo", "net servicos", "internet", "telefonica",
        ),
        "Compras" to listOf(
            "amazon", "mercado livre", "mercadolivre", "mercadopago*", "shopee", "magalu", "magazine luiza",
            "americanas", "shein", "aliexpress", "renner", "riachuelo", "c&a", "zara", "casas bahia", "netshoes",
        ),
        "Lazer" to listOf(
            "netflix", "spotify", "cinema", "ingresso", "steam", "playstation", "xbox", "disney",
            "hbo", "max.com", "prime video", "youtube", "deezer", "globoplay", "cinemark",
        ),
        "Educação" to listOf(
            "escola", "colegio", "faculdade", "universidade", "curso", "udemy", "alura", "livraria",
        ),
    )

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Retorna a categoria ou null quando não sabe (vira "pendente"). Só devolve categorias que existem. */
    fun categorize(db: Db, merchant: String): String? {
        val existentes = db.categoryNames()
        val m = norm(merchant) + " "
        db.rules().firstOrNull { m.contains(it.first) && it.second in existentes }?.let { return it.second }
        for ((cat, words) in defaults) {
            if (cat in existentes && words.any { m.contains(it) }) return cat
        }
        return null
    }

    /** Você escolheu a categoria: guarda a regra e aplica nos outros pendentes iguais (e nas outras parcelas). */
    fun learn(db: Db, e: Expense, category: String) {
        val key = norm(e.merchant).trim()
        db.setCategory(e.id, category)
        db.setCategoryPlan(e.planId, category)
        if (key.length < 3) return
        db.addRule(key, category)
        db.pending().filter { norm(it.merchant).contains(key) }.forEach { db.setCategory(it.id, category) }
    }
}
