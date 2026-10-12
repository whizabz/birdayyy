@file:OptIn(ExperimentalMaterial3Api::class)

package app.birdayyy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birdayyy.data.buildPersons
import app.birdayyy.data.upcoming
import java.time.LocalDate

@Composable
fun BirdayyyRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val data by vm.data.collectAsState()
    val today = remember { LocalDate.now() }
    val persons = remember(data, settings) { buildPersons(data.rows, data.icloud, settings) }
    val events = remember(persons, today) { upcoming(persons, today) }
    val snack = remember { SnackbarHostState() }

    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }

    LaunchedEffect(events, vm.pendingWish) {
        val p = vm.pendingWish ?: return@LaunchedEffect
        events.firstOrNull { it.person.id == p.first && (p.second == null || it.date.key == p.second) }?.let {
            vm.wishFor = it; vm.pendingWish = null
        }
    }
    LaunchedEffect(events, vm.debugDest) {
        val d = vm.debugDest ?: return@LaunchedEffect
        val first = events.firstOrNull { it.person.name.startsWith("Maya") } ?: events.firstOrNull()
        when (d) {
            "onboarding" -> vm.resetTo(Dest.Onboarding)
            "sources" -> vm.resetTo(Dest.Sources)
            "icloud" -> { vm.resetTo(Dest.Home); vm.push(Dest.ICloud) }
            "home" -> vm.resetTo(Dest.Home)
            "settings" -> { vm.resetTo(Dest.Home); vm.push(Dest.Settings) }
            "templates" -> { vm.resetTo(Dest.Home); vm.push(Dest.Templates) }
            "edit" -> { vm.resetTo(Dest.Home); vm.push(Dest.EditTemplate("t1")) }
            "addperson" -> { vm.resetTo(Dest.Home); vm.push(Dest.AddPerson) }
            "perm" -> { vm.debugPerm = true; vm.resetTo(Dest.Home) }
            "empty-dates", "empty-contacts" -> { vm.debugEmpty = d; vm.resetTo(Dest.Home) }
            "person", "reminder", "adddate", "source" -> if (first != null) {
                vm.resetTo(Dest.Home); vm.push(Dest.Person(first.person.id)); vm.debugSheet = d.takeIf { it != "person" }
            } else return@LaunchedEffect
            "wish" -> if (first != null) { vm.resetTo(Dest.Home); vm.wishFor = first } else return@LaunchedEffect
            "remind" -> if (first != null) { vm.resetTo(Dest.Home); vm.remindFor = listOf(first) } else return@LaunchedEffect
        }
        vm.debugDest = null
    }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    Box(Modifier.fillMaxSize()) {
        Crossfade(vm.current, label = "screen") { dest ->
            when (dest) {
                Dest.Onboarding -> OnboardingScreen(vm)
                Dest.Sources -> SourcesScreen(vm, settings, data)
                Dest.ICloud -> ICloudScreen(vm)
                Dest.Home -> HomeScreen(vm, persons, events, data, settings)
                Dest.Settings -> SettingsScreen(vm, settings)
                Dest.Templates -> TemplatesScreen(vm, settings)
                Dest.AddPerson -> AddPersonScreen(vm, data, persons)
                is Dest.Person -> PersonScreen(vm, dest.id, persons, events, settings, data)
                is Dest.EditTemplate -> EditTemplateScreen(vm, dest.id, settings)
            }
        }
        vm.wishFor?.let { WishSheet(vm, it, settings) }
        vm.remindFor?.let { RemindSheet(vm, it) }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
