package app.birdayyy.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.birdayyy.MainActivity
import app.birdayyy.data.*
import app.birdayyy.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Dest {
    data object Onboarding : Dest
    data object Sources : Dest
    data object ICloud : Dest
    data object Home : Dest
    data object Settings : Dest
    data object Templates : Dest
    data object AddPerson : Dest
    data class Person(val id: String) : Dest
    data class EditTemplate(val id: String?) : Dest
}

data class DataState(
    val loaded: Boolean = false,
    val canRead: Boolean = false,
    val rows: List<Row> = emptyList(),
    val icloud: List<app.birdayyy.data.Person> = emptyList(),
    val contactCount: Int = 0,
    val accounts: List<AccountRef> = emptyList(),
    val allContacts: List<app.birdayyy.data.Person> = emptyList(),
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = SettingsStore(app)
    val repo = ContactsRepository(app)
    val icloud = ICloudClient(app)
    val settings: StateFlow<Settings> = store.flow

    private val _data = MutableStateFlow(DataState())
    val data: StateFlow<DataState> = _data

    val stack = mutableStateListOf<Dest>()
    var wishFor by mutableStateOf<UpcomingEvent?>(null)
    var remindFor by mutableStateOf<List<UpcomingEvent>?>(null)
    var sourceTab by mutableStateOf(SourceType.Phone)
    var message by mutableStateOf<String?>(null)
    var pendingWish by mutableStateOf<Pair<String, String?>?>(null)
    var debugDest by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false)
    var showEarlier by mutableStateOf(false)
    var addForContact by mutableStateOf<Pair<Long, String>?>(null)
    var debugSheet by mutableStateOf<String?>(null)
    var debugPerm by mutableStateOf(false)
    var debugEmpty by mutableStateOf<String?>(null)

    init {
        stack += if (store.value.onboarded) Dest.Home else Dest.Onboarding
        refresh()
    }

    val current: Dest get() = stack.last()
    fun push(d: Dest) { stack += d }
    fun pop(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }
    fun resetTo(d: Dest) { stack.clear(); stack += d }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        val debuggable = getApplication<Application>().applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) intent.getStringExtra("debug_dest")?.let { debugDest = it; intent.removeExtra("debug_dest") }
        if (intent.getBooleanExtra(MainActivity.EXTRA_OPEN_TODAY, false)) {
            intent.removeExtra(MainActivity.EXTRA_OPEN_TODAY)
            if (store.value.onboarded) resetTo(Dest.Home)
        }
        intent.getStringExtra(MainActivity.EXTRA_WISH_PERSON)?.let {
            pendingWish = it to intent.getStringExtra(MainActivity.EXTRA_WISH_DATE)
            intent.removeExtra(MainActivity.EXTRA_WISH_PERSON)
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshing = true
            val started = System.currentTimeMillis()
            try { reload() } finally {
                val left = 700 - (System.currentTimeMillis() - started)
                if (left > 0) kotlinx.coroutines.delay(left)
                refreshing = false
            }
        }
    }

    private fun reload() {
        run {
            val can = repo.canRead()
            val rows = if (can) repo.loadRows() else emptyList()
            _data.value = DataState(
                loaded = true, canRead = can, rows = rows,
                icloud = if (icloud.isConnected) icloud.cached() else emptyList(),
                contactCount = repo.contactCount(), accounts = repo.accounts(), allContacts = repo.loadAllContacts(),
            )
            if (icloud.isConnected) {
                runCatching { icloud.fetch() }.onSuccess { fresh -> _data.value = _data.value.copy(icloud = fresh) }
            }
        }
    }

    fun connectICloud(email: String, password: String, done: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val people = icloud.connect(email.trim(), password.trim())
                store.update { it.copy(icloudEmail = email.trim(), icloudOn = true) }
                _data.value = _data.value.copy(icloud = people)
                launch(Dispatchers.Main) { done(null) }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { done(e.message ?: "Could not connect to iCloud") }
            }
        }
    }

    fun disconnectICloud() {
        icloud.forget()
        store.update { it.copy(icloudEmail = null) }
        _data.value = _data.value.copy(icloud = emptyList())
    }

    private fun io(block: () -> Boolean, success: String, failure: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching(block).getOrDefault(false)
            launch(Dispatchers.Main) { message = if (ok) success else failure }
            refresh()
        }
    }

    fun setStyle(personId: String, style: ReminderStyle, kind: EventKind? = null) =
        store.update { st -> st.copy(styles = st.styles + ((if (kind == null) personId else "$personId#$kind") to style)) }
    fun setCategory(personId: String, c: Category) = store.update { it.copy(categories = it.categories + (personId to c)) }

    fun addPerson(name: String, dates: List<DateEntry>, target: AccountRef?, toICloud: Boolean, photo: ByteArray? = null, category: Category? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                if (toICloud) icloud.addPerson(name, dates, photo) else repo.addPerson(name, dates, target ?: AccountRef(null, null), photo)
            }.getOrDefault(false)
            if (ok && category != null) {
                val cloud = if (toICloud) runCatching { icloud.fetch() }.getOrDefault(icloud.cached()) else icloud.cached()
                buildPersons(repo.loadRows(), cloud, store.value.copy(mode = ListMode.Combined))
                    .firstOrNull { it.name == name }?.let { setCategory(it.id, category) }
            }
            launch(Dispatchers.Main) { message = if (ok) "Saved $name" else "Couldn't save. Check contacts permission." }
            refresh()
        }
    }

    fun addDate(person: Person, date: DateEntry, meta: DateMeta?) {
        meta?.let { m -> store.update { it.copy(dateMeta = it.dateMeta + ("${person.id}|${date.label}" to m)) } }
        io({ person.contactId?.let { repo.addDate(it, date) } ?: false }, "Date added", "Couldn't add the date to this contact.")
    }

    fun deleteDate(person: Person, date: DateEntry) = io({ repo.deleteDate(person.rawIds, date) }, "Date removed", "Couldn't remove the date.")

    fun moveOrCopy(person: Person, target: AccountRef?, toICloud: Boolean, move: Boolean) = io({
        if (toICloud) {
            val ok = icloud.addPerson(person.name, person.dates)
            if (ok && move) repo.deleteRaws(person.rawIds)
            ok
        } else repo.moveOrCopy(person, target ?: AccountRef(null, null), move)
    }, if (move) "Moved ${person.name}" else "Copied ${person.name}", "Couldn't change the source.")

    fun setSplit(personId: String, split: Boolean) {
        val lookup = personId.substringAfterLast(':')
        store.update { it.copy(splits = if (split) it.splits + lookup else it.splits - lookup) }
    }

    fun link(child: String, parent: String) = store.update { it.copy(links = it.links + (child to parent)) }
    fun unlink(child: String) = store.update { it.copy(links = it.links - child) }

    fun finishOnboarding() {
        store.update { it.copy(onboarded = true) }
        ReminderScheduler.scheduleDaily(getApplication())
        resetTo(Dest.Home)
    }

    fun setReminderTime(h: Int, m: Int) {
        store.update { it.copy(hour = h, minute = m) }
        ReminderScheduler.scheduleDaily(getApplication())
    }
}
