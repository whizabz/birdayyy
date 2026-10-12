package app.birdayyy.data

import java.time.LocalDate

enum class EventKind { Birthday, Anniversary, Other }

enum class ReminderStyle(val title: String, val hint: String) {
    Normal("Normal", "Notification with sound and vibration"),
    Priority("Notify on priority", "Pops up on screen"),
    Silent("Notify silently", "Shows in the digest, no sound or vibration"),
    Never("Never notify", "Hidden from notifications. Still visible in the app.")
}

enum class Category { Friends, Family, Colleagues }
enum class TemplateKind { Birthday, Anniversary, Any }
enum class SourceType(val label: String) { Phone("Phone"), Google("Google"), ICloud("iCloud") }
enum class ListMode { Combined, Separate }
enum class ThemeMode { System, Light, Dark }

data class AccountRef(val type: String?, val name: String?) {
    val source: SourceType
        get() = when (type) {
            "com.google" -> SourceType.Google
            else -> SourceType.Phone
        }
    val title: String get() = if (name.isNullOrBlank()) "Phone" else name
}

data class DateEntry(
    val kind: EventKind,
    val label: String,
    val month: Int,
    val day: Int,
    val year: Int?,
) {
    val key: String get() = "$kind|$label|$month|$day"
    val title: String
        get() = when (kind) {
            EventKind.Birthday -> "Birthday"
            EventKind.Anniversary -> "Anniversary"
            EventKind.Other -> label.ifBlank { "Other" }
        }
}

data class Person(
    val id: String,
    val name: String,
    val photo: String?,
    val dates: List<DateEntry>,
    val sources: Set<SourceType>,
    val contactId: Long?,
    val rawIds: List<Long>,
    val icloudUid: String? = null,
) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }
    val initials: String
        get() = name.trim().split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

data class UpcomingEvent(
    val person: Person,
    val date: DateEntry,
    val on: LocalDate,
    val daysUntil: Int,
    val turning: Int?,
) {
    val id: String get() = person.id + "#" + date.key
}

data class Template(
    val id: String,
    val text: String,
    val category: Category,
    val forKind: TemplateKind,
    val isDefault: Boolean,
)

data class DateMeta(val icon: Int, val color: Int)

data class Settings(
    val hour: Int = 7,
    val minute: Int = 0,
    val birthdays: Boolean = true,
    val anniversaries: Boolean = true,
    val others: Boolean = true,
    val defaultStyle: ReminderStyle = ReminderStyle.Normal,
    val mode: ListMode = ListMode.Combined,
    val phoneOn: Boolean = true,
    val googleOn: Boolean = true,
    val icloudOn: Boolean = true,
    val dynamicColor: Boolean = false,
    val theme: ThemeMode = ThemeMode.System,
    val onboarded: Boolean = false,
    val icloudEmail: String? = null,
    val styles: Map<String, ReminderStyle> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val links: Map<String, String> = emptyMap(),
    val splits: Set<String> = emptySet(),
    val dateMeta: Map<String, DateMeta> = emptyMap(),
    val templates: List<Template> = defaultTemplates(),
) {
    fun styleFor(personId: String, kind: EventKind? = null): ReminderStyle =
        (kind?.let { styles["$personId#$it"] }) ?: styles[personId] ?: defaultStyle
    fun categoryFor(personId: String): Category = categories[personId] ?: Category.Friends
    fun kindEnabled(kind: EventKind) = when (kind) {
        EventKind.Birthday -> birthdays
        EventKind.Anniversary -> anniversaries
        EventKind.Other -> others
    }
}

fun defaultTemplates(): List<Template> = listOf(
    Template("t1", "Happy birthday, {name}! Hope your day is full of cake and zero emails.", Category.Friends, TemplateKind.Birthday, true),
    Template("t2", "{name}! Another year, another reason to celebrate you. Dinner soon?", Category.Friends, TemplateKind.Birthday, false),
    Template("t3", "Happy {age}th, {name}. Wishing you a wonderful year ahead.", Category.Friends, TemplateKind.Birthday, false),
    Template("t4", "Happy anniversary, {name}! Here's to many more.", Category.Friends, TemplateKind.Anniversary, true),
    Template("t5", "Happy birthday, {name}! Sending you lots of love today.", Category.Family, TemplateKind.Birthday, true),
    Template("t6", "Happy anniversary, {name}! So glad we get to celebrate together.", Category.Family, TemplateKind.Anniversary, true),
    Template("t7", "Happy birthday, {name}! Hope you get a well-deserved break today.", Category.Colleagues, TemplateKind.Birthday, true),
    Template("t8", "Congratulations on another year with us, {name}!", Category.Colleagues, TemplateKind.Anniversary, true),
)

fun fillTemplate(text: String, person: Person, turning: Int?): String =
    text.replace("{name}", person.firstName)
        .replace("{nickname}", person.firstName)
        .replace("{age}", turning?.toString() ?: "")
