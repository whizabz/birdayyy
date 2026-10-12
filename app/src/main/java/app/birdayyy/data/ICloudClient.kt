package app.birdayyy.data

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

class ICloudException(message: String) : Exception(message)

/**
 * Minimal read/write CardDAV client for iCloud contacts, authenticated with an Apple ID email and an
 * app-specific password.
 */
class ICloudClient(private val ctx: Context) {
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val xml = "application/xml; charset=utf-8".toMediaType()
    private val prefs = ctx.applicationContext.getSharedPreferences("icloud", Context.MODE_PRIVATE)

    val isConnected: Boolean get() = prefs.getString("email", null) != null
    fun forget() {
        prefs.edit().clear().apply()
        File(ctx.filesDir, "icloud_cache.json").delete()
    }

    fun cached(): List<Person> {
        val f = File(ctx.filesDir, "icloud_cache.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val dates = o.getJSONArray("dates").let { d ->
                    (0 until d.length()).map { j ->
                        val x = d.getJSONObject(j)
                        DateEntry(EventKind.valueOf(x.getString("kind")), x.getString("label"), x.getInt("m"), x.getInt("d"),
                            if (x.isNull("y")) null else x.getInt("y"))
                    }
                }
                Person(o.getString("id"), o.getString("name"), o.optString("photo").ifBlank { null }, dates,
                    setOf(SourceType.ICloud), null, emptyList(), o.getString("uid"))
            }
        }.getOrDefault(emptyList())
    }

    /** Verifies credentials, stores them, and downloads contacts. */
    fun connect(email: String, appPassword: String): List<Person> {
        prefs.edit().putString("email", email).putString("pw", SecretStore.encrypt(appPassword)).remove("book").apply()
        return try { fetch() } catch (e: Exception) { forget(); throw e }
    }

    fun fetch(): List<Person> {
        val email = prefs.getString("email", null) ?: throw ICloudException("iCloud is not connected")
        val pw = prefs.getString("pw", null)?.let { SecretStore.decrypt(it) } ?: throw ICloudException("iCloud is not connected")
        val auth = okhttp3.Credentials.basic(email, pw)
        val book = prefs.getString("book", null) ?: discoverBook(auth).also { prefs.edit().putString("book", it).apply() }
        val body = """<?xml version="1.0" encoding="utf-8"?><card:addressbook-query xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><d:prop><card:address-data/></d:prop></card:addressbook-query>"""
        val res = request(auth, book, "REPORT", "1", body)
        val people = splitResponses(res).mapNotNull { block ->
            val data = Regex("""(?s)address-data[^>]*>(.*?)</[^>]*address-data>""").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            parseVCard(unescape(data))
        }
        File(ctx.filesDir, "icloud_cache.json").writeText(JSONArray().also { arr ->
            people.forEach { p ->
                arr.put(JSONObject().put("id", p.id).put("uid", p.icloudUid).put("name", p.name).put("photo", p.photo ?: "")
                    .put("dates", JSONArray().also { d -> p.dates.forEach { x ->
                        d.put(JSONObject().put("kind", x.kind.name).put("label", x.label).put("m", x.month).put("d", x.day)
                            .put("y", x.year ?: JSONObject.NULL)) } }))
            }
        }.toString())
        return people
    }

    fun addPerson(name: String, dates: List<DateEntry>, photo: ByteArray? = null): Boolean {
        val email = prefs.getString("email", null) ?: return false
        val pw = prefs.getString("pw", null)?.let { SecretStore.decrypt(it) } ?: return false
        val auth = okhttp3.Credentials.basic(email, pw)
        val book = prefs.getString("book", null) ?: discoverBook(auth)
        val uid = UUID.randomUUID().toString()
        val parts = name.trim().split(' ')
        val card = buildString {
            append("BEGIN:VCARD\r\nVERSION:3.0\r\nUID:$uid\r\nFN:$name\r\n")
            append("N:${parts.drop(1).joinToString(" ")};${parts.first()};;;\r\n")
            dates.forEachIndexed { i, d ->
                val v = if (d.year == null) "--%02d-%02d".format(d.month, d.day) else "%04d-%02d-%02d".format(d.year, d.month, d.day)
                if (d.kind == EventKind.Birthday) append("BDAY:$v\r\n")
                else {
                    append("item${i + 1}.X-ABDATE;type=pref:$v\r\n")
                    append("item${i + 1}.X-ABLabel:" + if (d.kind == EventKind.Anniversary) "_\$!<Anniversary>!\$_" else d.label.ifBlank { "Other" } + "\r\n")
                }
            }
            if (photo != null) append("PHOTO;ENCODING=b;TYPE=JPEG:" + Base64.encodeToString(photo, Base64.NO_WRAP) + "\r\n")
            append("END:VCARD\r\n")
        }
        val req = Request.Builder().url(URI(book).resolve("$uid.vcf").toString())
            .header("Authorization", auth).header("If-None-Match", "*")
            .put(card.toRequestBody("text/vcard; charset=utf-8".toMediaType())).build()
        return http.newCall(req).execute().use { it.isSuccessful }
    }

    private fun discoverBook(auth: String): String {
        val root = "https://contacts.icloud.com/"
        val principalXml = request(auth, root, "PROPFIND", "0",
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>""")
        val principal = Regex("""(?s)current-user-principal.*?href[^>]*>([^<]+)<""").find(principalXml)?.groupValues?.get(1)
            ?: throw ICloudException("Could not find your iCloud account")
        val principalUrl = URI(root).resolve(principal).toString()
        val homeXml = request(auth, principalUrl, "PROPFIND", "0",
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><d:prop><card:addressbook-home-set/></d:prop></d:propfind>""")
        val home = Regex("""(?s)addressbook-home-set.*?href[^>]*>([^<]+)<""").find(homeXml)?.groupValues?.get(1)
            ?: throw ICloudException("Could not find your iCloud address book")
        val homeUrl = URI(principalUrl).resolve(home).toString()
        val listXml = request(auth, homeUrl, "PROPFIND", "1",
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>""")
        val book = splitResponses(listXml).firstOrNull { Regex("""addressbook\s*/>""").containsMatchIn(it) }
            ?.let { Regex("""href[^>]*>([^<]+)<""").find(it)?.groupValues?.get(1) }
            ?: throw ICloudException("No iCloud address book found")
        return URI(homeUrl).resolve(book).toString()
    }

    private fun request(auth: String, url: String, method: String, depth: String, body: String, hops: Int = 0): String {
        val req = Request.Builder().url(url).header("Authorization", auth).header("Depth", depth)
            .method(method, body.toRequestBody(xml)).build()
        http.newCall(req).execute().use { r ->
            if (r.code in 300..399 && hops < 3) {
                val loc = r.header("Location") ?: throw ICloudException("Unexpected redirect")
                return request(auth, URI(url).resolve(loc).toString(), method, depth, body, hops + 1)
            }
            if (r.code == 401) throw ICloudException("Apple rejected the email or app-specific password")
            if (!r.isSuccessful && r.code != 207) throw ICloudException("iCloud returned ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    private fun splitResponses(xml: String): List<String> =
        Regex("""(?s)<(?:\w+:)?response[ >].*?</(?:\w+:)?response>""").findAll(xml).map { it.value }.toList()

    private fun unescape(s: String): String = s.removePrefix("<![CDATA[").removeSuffix("]]>")
        .replace("&#13;", "").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun parseVCard(text: String): Person? {
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        var uid: String? = null; var fn: String? = null; var n: String? = null; var photoB64: String? = null
        val dates = mutableListOf<DateEntry>()
        val groupDates = mutableMapOf<String, String>(); val groupLabels = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf(':'); if (idx < 0) continue
            val head = line.substring(0, idx); val value = line.substring(idx + 1)
            val group = if (head.contains('.')) head.substringBefore('.') else ""
            val prop = head.substringAfter('.').substringBefore(';').uppercase()
            when (prop) {
                "UID" -> uid = value
                "FN" -> fn = value
                "N" -> n = value
                "BDAY" -> parseVDate(value)?.let { (y, m, d) -> dates += DateEntry(EventKind.Birthday, "", m, d, y) }
                "X-ABDATE" -> groupDates[group] = value
                "X-ABLABEL" -> groupLabels[group] = value
                "PHOTO" -> if (head.uppercase().contains("ENCODING=B") || head.uppercase().contains("BASE64")) photoB64 = value
            }
        }
        groupDates.forEach { (g, v) ->
            val (y, m, d) = parseVDate(v) ?: return@forEach
            val raw = groupLabels[g].orEmpty()
            val label = raw.removePrefix("_\$!<").removeSuffix(">!\$_")
            dates += if (label.equals("Anniversary", true)) DateEntry(EventKind.Anniversary, "", m, d, y)
            else DateEntry(EventKind.Other, label, m, d, y)
        }
        val id = uid ?: return null
        if (dates.isEmpty()) return null
        val name = fn?.takeIf { it.isNotBlank() } ?: n?.split(';')?.let { "${it.getOrNull(1).orEmpty()} ${it.getOrNull(0).orEmpty()}".trim() } ?: return null
        val photoPath = photoB64?.let {
            runCatching {
                val dir = File(ctx.filesDir, "icloud_photos").apply { mkdirs() }
                val f = File(dir, id.replace(Regex("[^A-Za-z0-9]"), "_") + ".jpg")
                f.writeBytes(Base64.decode(it, Base64.DEFAULT)); f.absolutePath
            }.getOrNull()
        }
        return Person("i:$id", name, photoPath, dates.distinctBy { it.key }, setOf(SourceType.ICloud), null, emptyList(), id)
    }

    private fun parseVDate(v: String): Triple<Int?, Int, Int>? {
        val s = v.trim()
        Regex("""^(\d{4})-?(\d{2})-?(\d{2})""").find(s)?.let { m ->
            val y = m.groupValues[1].toInt()
            return Triple(if (y < 1700) null else y, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("""^--(\d{2})-?(\d{2})""").find(s)?.let { m -> return Triple(null, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return null
    }
}
