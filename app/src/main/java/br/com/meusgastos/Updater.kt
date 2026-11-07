package br.com.meusgastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização automática: procura a versão mais nova publicada no GitHub (aba "Releases"),
 * baixa o APK e abre o instalador do Android. Os gastos continuam salvos.
 */
object Updater {
    data class Info(val version: Int, val url: String, val notes: String)

    private const val UA = "MeusGastos-App"

    /** Rode fora da tela principal (Dispatchers.IO). Retorna null se não conseguir verificar. */
    fun check(): Info? = try {
        val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", UA)
        if (c.responseCode != 200) {
            null
        } else {
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val v = j.optString("tag_name").removePrefix("v").toIntOrNull()
            val assets = j.optJSONArray("assets")
            var url: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
                }
            }
            if (v == null || url == null) null else Info(v, url, j.optString("body").takeIf { it != "null" } ?: "")
        }
    } catch (_: Exception) {
        null
    }

    private fun open(address: String, hops: Int = 0): HttpURLConnection {
        val c = URL(address).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        val code = c.responseCode
        if (code in 300..399 && hops < 5) {
            val loc = c.getHeaderField("Location") ?: throw IOException("Redirecionamento sem destino")
            c.disconnect()
            return open(URL(URL(address), loc).toString(), hops + 1)
        }
        if (code != 200) throw IOException("HTTP $code")
        return c
    }

    /** Rode em Dispatchers.IO. */
    fun download(ctx: Context, info: Info): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "meus-gastos.apk")
        val c = open(info.url)
        c.inputStream.use { inp -> f.outputStream().use { out -> inp.copyTo(out) } }
        c.disconnect()
        return f
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
        )
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
