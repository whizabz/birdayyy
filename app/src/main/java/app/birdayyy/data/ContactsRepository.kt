package app.birdayyy.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

/** One date from one raw contact, before people are grouped. */
data class Row(
    val contactId: Long,
    val rawId: Long,
    val lookup: String,
    val name: String,
    val photo: String?,
    val account: AccountRef,
    val date: DateEntry,
)

class ContactsRepository(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    fun canRead() = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    fun canWrite() = ctx.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun contactCount(): Int {
        if (!canRead()) return 0
        return cr.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            ?.use { it.count } ?: 0
    }

    /** Every contact on the device as a date-less Person, used to find people who have no events yet. */
    fun loadAllContacts(): List<Person> {
        if (!canRead()) return emptyList()
        val out = mutableListOf<Person>()
        cr.query(ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI),
            null, null, "${ContactsContract.Contacts.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                out += Person("n:" + (c.getString(1) ?: c.getLong(0)), name, c.getString(3), emptyList(), emptySet(), c.getLong(0), emptyList())
            }
        }
        return out
    }

    /** Distinct writable accounts present on the device, plus local phone storage. */
    fun accounts(): List<AccountRef> {
        if (!canRead()) return listOf(AccountRef(null, null))
        val out = linkedSetOf(AccountRef(null, null))
        cr.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(0)
                val name = c.getString(1)
                if (type != null && name != null && type != "com.android.local") out += AccountRef(type, name)
            }
        }
        return out.toList()
    }

    fun loadRows(): List<Row> {
        if (!canRead()) return emptyList()
        val rows = mutableListOf<Row>()
        val projection = arrayOf(
            Data.CONTACT_ID, Data.RAW_CONTACT_ID, Data.LOOKUP_KEY, Data.DISPLAY_NAME, Data.PHOTO_URI,
            Event.START_DATE, Event.TYPE, Event.LABEL, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME,
        )
        cr.query(
            Data.CONTENT_URI, projection,
            "${Data.MIMETYPE}=?", arrayOf(Event.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val date = parseDate(c.getString(5), c.getInt(6), c.getString(7)) ?: continue
                val acctType = c.getString(8)
                rows += Row(
                    contactId = c.getLong(0), rawId = c.getLong(1), lookup = c.getString(2) ?: c.getLong(0).toString(),
                    name = c.getString(3) ?: "Unknown", photo = c.getString(4),
                    account = AccountRef(if (acctType == "com.android.local") null else acctType, c.getString(9)),
                    date = date,
                )
            }
        }
        return rows
    }

    private fun parseDate(raw: String?, type: Int, label: String?): DateEntry? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        var year: Int? = null
        val month: Int
        val day: Int
        val full = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)
        val noYear = Regex("""^--(\d{1,2})-?(\d{1,2})""").find(s)
        val compact = Regex("""^(\d{4})(\d{2})(\d{2})""").find(s)
        when {
            full != null -> { year = full.groupValues[1].toInt(); month = full.groupValues[2].toInt(); day = full.groupValues[3].toInt() }
            noYear != null -> { month = noYear.groupValues[1].toInt(); day = noYear.groupValues[2].toInt() }
            compact != null -> { year = compact.groupValues[1].toInt(); month = compact.groupValues[2].toInt(); day = compact.groupValues[3].toInt() }
            else -> return null
        }
        if (month !in 1..12 || day !in 1..31) return null
        if (year != null && year < 1700) year = null
        val kind = when (type) {
            Event.TYPE_BIRTHDAY -> EventKind.Birthday
            Event.TYPE_ANNIVERSARY -> EventKind.Anniversary
            else -> EventKind.Other
        }
        return DateEntry(kind, if (kind == EventKind.Other) (label ?: "") else "", month, day, year)
    }

    private fun format(d: DateEntry) =
        if (d.year == null) "--%02d-%02d".format(d.month, d.day) else "%04d-%02d-%02d".format(d.year, d.month, d.day)

    private fun eventOp(d: DateEntry, backRef: Int?, rawId: Long?): ContentProviderOperation {
        val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
        if (backRef != null) b.withValueBackReference(Data.RAW_CONTACT_ID, backRef) else b.withValue(Data.RAW_CONTACT_ID, rawId)
        b.withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
        b.withValue(Event.START_DATE, format(d))
        when (d.kind) {
            EventKind.Birthday -> b.withValue(Event.TYPE, Event.TYPE_BIRTHDAY)
            EventKind.Anniversary -> b.withValue(Event.TYPE, Event.TYPE_ANNIVERSARY)
            EventKind.Other ->
                if (d.label.isBlank()) b.withValue(Event.TYPE, Event.TYPE_OTHER)
                else b.withValue(Event.TYPE, Event.TYPE_CUSTOM).withValue(Event.LABEL, d.label)
        }
        return b.build()
    }

    /** Creates a new contact in [target] with the given dates. */
    fun addPerson(name: String, dates: List<DateEntry>, target: AccountRef, photo: ByteArray? = null): Boolean {
        if (!canWrite()) return false
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, target.type)
            .withValue(RawContacts.ACCOUNT_NAME, target.name).build()
        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name).build()
        dates.forEach { ops += eventOp(it, 0, null) }
        if (photo != null) ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photo).build()
        return runCatching { cr.applyBatch(ContactsContract.AUTHORITY, ops) }.isSuccess
    }

    fun deleteRaws(rawIds: List<Long>): Boolean {
        if (!canWrite() || rawIds.isEmpty()) return false
        val ops = ArrayList(rawIds.map { ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, it)).build() })
        return runCatching { cr.applyBatch(ContactsContract.AUTHORITY, ops) }.isSuccess
    }

    /** Adds a date to an existing contact (attached to its first writable raw contact). */
    fun addDate(contactId: Long, date: DateEntry): Boolean {
        if (!canWrite()) return false
        val raw = cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return false
        return runCatching { cr.applyBatch(ContactsContract.AUTHORITY, arrayListOf(eventOp(date, null, raw))) }.isSuccess
    }

    fun deleteDate(rawIds: List<Long>, date: DateEntry): Boolean {
        if (!canWrite() || rawIds.isEmpty()) return false
        var ok = true
        rawIds.forEach { raw ->
            cr.query(Data.CONTENT_URI, arrayOf(Data._ID, Event.START_DATE, Event.TYPE, Event.LABEL),
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(raw.toString(), Event.CONTENT_ITEM_TYPE), null)?.use { c ->
                while (c.moveToNext()) {
                    val d = parseDate(c.getString(1), c.getInt(2), c.getString(3))
                    if (d?.key == date.key) {
                        ok = ok && runCatching {
                            cr.delete(ContentUris.withAppendedId(Data.CONTENT_URI, c.getLong(0)), null, null)
                        }.isSuccess
                    }
                }
            }
        }
        return ok
    }

    /** Copies (or moves) the raw contacts of [person] into [target], keeping names, phones, emails and dates. */
    fun moveOrCopy(person: Person, target: AccountRef, move: Boolean): Boolean {
        if (!canWrite() || person.rawIds.isEmpty()) return false
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, target.type)
            .withValue(RawContacts.ACCOUNT_NAME, target.name).build()
        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, person.name).build()
        person.dates.forEach { ops += eventOp(it, 0, null) }
        person.rawIds.forEach { raw ->
            cr.query(Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2),
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
                arrayOf(raw.toString(), Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE), null)?.use { c ->
                while (c.moveToNext()) {
                    ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, c.getString(0))
                        .withValue(Data.DATA1, c.getString(1))
                        .withValue(Data.DATA2, c.getInt(2)).build()
                }
            }
        }
        if (move) person.rawIds.forEach { ops += ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, it)).build() }
        return runCatching { cr.applyBatch(ContactsContract.AUTHORITY, ops) }.isSuccess
    }
}
