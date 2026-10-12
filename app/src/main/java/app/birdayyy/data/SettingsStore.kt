package app.birdayyy.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Small synchronous settings store (also readable from broadcast receivers). */
class SettingsStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Settings> get() = state
    val value: Settings get() = state.value

    fun update(f: (Settings) -> Settings) {
        val next = f(state.value)
        state.value = next
        sp.edit().putString("json", encode(next).toString()).apply()
    }

    private fun load(): Settings {
        val raw = sp.getString("json", null) ?: return Settings()
        return runCatching { decode(JSONObject(raw)) }.getOrDefault(Settings())
    }

    private fun encode(s: Settings) = JSONObject().apply {
        put("hour", s.hour); put("minute", s.minute)
        put("birthdays", s.birthdays); put("anniversaries", s.anniversaries); put("others", s.others)
        put("defaultStyle", s.defaultStyle.name); put("mode", s.mode.name)
        put("phoneOn", s.phoneOn); put("googleOn", s.googleOn); put("icloudOn", s.icloudOn)
        put("dynamicColor", s.dynamicColor); put("theme", s.theme.name); put("onboarded", s.onboarded)
        put("icloudEmail", s.icloudEmail ?: JSONObject.NULL)
        put("styles", JSONObject().also { o -> s.styles.forEach { (k, v) -> o.put(k, v.name) } })
        put("categories", JSONObject().also { o -> s.categories.forEach { (k, v) -> o.put(k, v.name) } })
        put("links", JSONObject().also { o -> s.links.forEach { (k, v) -> o.put(k, v) } })
        put("splits", JSONArray().also { a -> s.splits.forEach { a.put(it) } })
        put("dateMeta", JSONObject().also { o -> s.dateMeta.forEach { (k, v) -> o.put(k, "${v.icon},${v.color}") } })
        put("templates", JSONArray().also { a ->
            s.templates.forEach { t ->
                a.put(JSONObject().put("id", t.id).put("text", t.text).put("category", t.category.name)
                    .put("kind", t.forKind.name).put("default", t.isDefault))
            }
        })
    }

    private fun decode(o: JSONObject): Settings {
        val d = Settings()
        fun <T> map(key: String, conv: (String) -> T?): Map<String, T> {
            val obj = o.optJSONObject(key) ?: return emptyMap()
            return obj.keys().asSequence().mapNotNull { k -> conv(obj.getString(k))?.let { k to it } }.toMap()
        }
        val templates = o.optJSONArray("templates")?.let { a ->
            (0 until a.length()).map { i ->
                val t = a.getJSONObject(i)
                Template(t.getString("id"), t.getString("text"), Category.valueOf(t.getString("category")),
                    TemplateKind.valueOf(t.getString("kind")), t.getBoolean("default"))
            }
        } ?: d.templates
        return Settings(
            hour = o.optInt("hour", 7), minute = o.optInt("minute", 0),
            birthdays = o.optBoolean("birthdays", true), anniversaries = o.optBoolean("anniversaries", true),
            others = o.optBoolean("others", true),
            defaultStyle = runCatching { ReminderStyle.valueOf(o.getString("defaultStyle")) }.getOrDefault(d.defaultStyle),
            mode = runCatching { ListMode.valueOf(o.getString("mode")) }.getOrDefault(d.mode),
            phoneOn = o.optBoolean("phoneOn", true), googleOn = o.optBoolean("googleOn", true),
            icloudOn = o.optBoolean("icloudOn", true),
            dynamicColor = o.optBoolean("dynamicColor", false),
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(d.theme),
            onboarded = o.optBoolean("onboarded", false),
            icloudEmail = if (o.isNull("icloudEmail")) null else o.optString("icloudEmail"),
            styles = map("styles") { runCatching { ReminderStyle.valueOf(it) }.getOrNull() },
            categories = map("categories") { runCatching { Category.valueOf(it) }.getOrNull() },
            links = map("links") { it },
            splits = o.optJSONArray("splits")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            dateMeta = map("dateMeta") { v -> v.split(",").takeIf { it.size == 2 }?.let { DateMeta(it[0].toInt(), it[1].toInt()) } },
            templates = templates,
        )
    }
}
