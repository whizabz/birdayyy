package app.birdayyy.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
    fun inYear(y: Int): LocalDate {
        val d = if (month == 2 && day == 29 && !java.time.Year.isLeap(y.toLong())) 28 else day
        return LocalDate.of(y, month, d.coerceAtMost(java.time.YearMonth.of(y, month).lengthOfMonth()))
    }
    val thisYear = inYear(today.year)
    return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
}

/** Most recent occurrence strictly before [today]. */
fun previousOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
    fun inYear(y: Int): LocalDate {
        val d = if (month == 2 && day == 29 && !java.time.Year.isLeap(y.toLong())) 28 else day
        return LocalDate.of(y, month, d.coerceAtMost(java.time.YearMonth.of(y, month).lengthOfMonth()))
    }
    val thisYear = inYear(today.year)
    return if (thisYear.isBefore(today)) thisYear else inYear(today.year - 1)
}

fun buildPersons(rows: List<Row>, icloud: List<Person>, s: Settings): List<Person> {
    fun enabled(src: SourceType) = when (src) {
        SourceType.Phone -> s.phoneOn
        SourceType.Google -> s.googleOn
        SourceType.ICloud -> s.icloudOn
    }
    val usable = rows.filter { enabled(it.account.source) }
    val deviceMode = if (s.mode == ListMode.Combined) {
        usable.groupBy { it.contactId }.values.flatMap { group ->
            val first = group.first()
            if (first.lookup in s.splits && group.map { it.account.source }.toSet().size > 1) {
                group.groupBy { it.account.source }.map { (src, g) ->
                    Person(
                        id = "r:" + src.name + ":" + first.lookup, name = first.name, photo = g.firstNotNullOfOrNull { it.photo },
                        dates = g.map { it.date }.distinctBy { it.key }, sources = setOf(src),
                        contactId = first.contactId, rawIds = g.map { it.rawId }.distinct(),
                    )
                }
            } else listOf(Person(
                id = "c:" + first.lookup, name = first.name, photo = group.firstNotNullOfOrNull { it.photo },
                dates = group.map { it.date }.distinctBy { it.key },
                sources = group.map { it.account.source }.toSet(),
                contactId = first.contactId, rawIds = group.map { it.rawId }.distinct(),
            ))
        }
    } else {
        usable.groupBy { it.account.source to it.contactId }.values.map { group ->
            val first = group.first()
            val src = first.account.source
            Person(
                id = "r:" + src.name + ":" + first.lookup, name = first.name, photo = group.firstNotNullOfOrNull { it.photo },
                dates = group.map { it.date }.distinctBy { it.key }, sources = setOf(src),
                contactId = first.contactId, rawIds = group.map { it.rawId }.distinct(),
            )
        }
    }
    val cloud = if (s.icloudOn) icloud else emptyList()
    var all = deviceMode + cloud
    if (s.mode == ListMode.Combined && s.links.isNotEmpty()) {
        val byId = all.associateBy { it.id }.toMutableMap()
        s.links.forEach { (child, parent) ->
            val c = byId[child]; val p = byId[parent]
            if (c != null && p != null && c.id != p.id) {
                byId[p.id] = p.copy(
                    dates = (p.dates + c.dates).distinctBy { it.key },
                    sources = p.sources + c.sources,
                    photo = p.photo ?: c.photo,
                    rawIds = p.rawIds + c.rawIds,
                )
                byId.remove(c.id)
            }
        }
        all = byId.values.toList()
    }
    return all.sortedBy { it.name.lowercase() }
}

fun upcoming(persons: List<Person>, today: LocalDate): List<UpcomingEvent> =
    persons.flatMap { p ->
        p.dates.map { d ->
            val on = nextOccurrence(d.month, d.day, today)
            val turning = d.year?.let { on.year - it }?.takeIf { it > 0 }
            UpcomingEvent(p, d, on, ChronoUnit.DAYS.between(today, on).toInt(), turning)
        }
    }.sortedWith(compareBy({ it.daysUntil }, { it.person.name.lowercase() }))

/** Events that already happened in the last [days] days, closest first (negative [UpcomingEvent.daysUntil]). */
fun previous(persons: List<Person>, today: LocalDate, days: Int = 364): List<UpcomingEvent> =
    persons.flatMap { p ->
        p.dates.mapNotNull { d ->
            val on = previousOccurrence(d.month, d.day, today)
            val ago = ChronoUnit.DAYS.between(on, today).toInt()
            if (ago !in 1..days) return@mapNotNull null
            val turned = d.year?.let { on.year - it }?.takeIf { it > 0 }
            UpcomingEvent(p, d, on, -ago, turned)
        }
    }.sortedWith(compareByDescending<UpcomingEvent> { it.daysUntil }.thenBy { it.person.name.lowercase() })

fun UpcomingEvent.subtitle(): String = when (date.kind) {
    EventKind.Birthday -> turning?.let { if (daysUntil < 0) "Turned $it" else "Turns $it" } ?: "Birthday"
    EventKind.Anniversary -> turning?.let { "${ordinal(it)} anniversary" } ?: "Anniversary"
    EventKind.Other -> turning?.let { "${date.title} · $it years" } ?: date.title
}

fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

fun UpcomingEvent.relative(): String = when {
    daysUntil < -60 -> "${-daysUntil / 30} months ago"
    daysUntil == -1 -> "Yesterday"
    daysUntil < 0 -> "${-daysUntil} days ago"
    daysUntil == 0 -> "Today"
    daysUntil == 1 -> "Tomorrow"
    else -> "in $daysUntil days"
}
