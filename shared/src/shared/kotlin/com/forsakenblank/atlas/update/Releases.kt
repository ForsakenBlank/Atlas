package com.forsakenblank.atlas.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// one published version of atlas, with the download for each device
data class Release(
    val tag: String,
    val version: String,
    val date: String,
    val notes: String,
    val page: String,
    // file name -> download link
    val assets: Map<String, String>,
)

object Releases {
    const val REPO = "ForsakenBlank/Atlas"
    const val PAGE = "https://github.com/$REPO/releases"
    private const val API = "https://api.github.com/repos/$REPO/releases?per_page=40"

    // every version release (v1.0.0 and up), newest first. the branch builds are left out
    suspend fun fetch(): List<Release> = withContext(Dispatchers.IO) {
        val text = get(API)
        val list = Json.parseToJsonElement(text) as? JsonArray ?: return@withContext emptyList()
        list.mapNotNull { element ->
            val r = element as? JsonObject ?: return@mapNotNull null
            fun str(key: String) = (r[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val tag = str("tag_name")
            val draft = (r["draft"] as? JsonPrimitive)?.booleanOrNull == true
            if (draft || !tag.matches(Regex("v\\d+(\\.\\d+)*"))) return@mapNotNull null
            val assets = (r["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
                val o = a as? JsonObject ?: return@mapNotNull null
                val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val link = (o["browser_download_url"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                name to link
            }.toMap()
            Release(tag, tag.removePrefix("v"), str("published_at").take(10), str("body"), str("html_url"), assets)
        }.sortedWith { a, b -> compare(b.version, a.version) }
    }

    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.trim().removePrefix("v").split(".").map { p -> p.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun newer(version: String, than: String) = compare(version, than) > 0

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Atlas")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // downloads to a file, telling how far along it is
    fun download(url: String, to: File, progress: (String) -> Unit) {
        to.parentFile?.mkdirs()
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Atlas")
        c.instanceFollowRedirects = true
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        try {
            if (c.responseCode != 200) error("The download answered ${c.responseCode}")
            val total = c.contentLengthLong
            var got = 0L
            var shown = -1L
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        val step = if (total > 0) got * 10 / total else got / 5_000_000
                        if (step != shown) {
                            shown = step
                            progress(if (total > 0) "Downloading… ${got * 100 / total}%" else "Downloading… ${got / 1_000_000} MB")
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}

// what each device does to put a version in place
interface Updater {
    // the release file this device needs
    val assetName: String

    // android will not install an older version over a newer one
    val canDowngrade: Boolean get() = true

    // a few words for the list when going back is not possible here
    val downgradeNote: String? get() = null

    // downloads and installs, null when it worked (the app may restart or hand over to the system installer)
    suspend fun install(release: Release, progress: (String) -> Unit): String?

    fun openPage(url: String)
}
