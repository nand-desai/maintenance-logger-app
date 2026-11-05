package com.garage.tracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garage.tracker.data.*
import com.garage.tracker.notifications.ReminderScheduler
import com.garage.tracker.ui.screens.*
import com.garage.tracker.ui.theme.Cream
import com.garage.tracker.ui.theme.GarageTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: GarageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.createChannel(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            GarageTheme(settings.themeMode) { GarageApp(vm) }
        }
    }
}

private fun shareBackup(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Save backup to…"))
}

/** Sends the open notes as plain text, ready to show or message to a mechanic. */
private fun shareNotes(context: Context, car: String, odometer: Int, open: List<CarNote>) {
    val body = buildString {
        appendLine("Things to mention about my $car (${"%,d".format(odometer)} km):")
        open.sortedBy { it.date }.forEach { n ->
            appendLine("• ${if (n.tag == NoteTags.GENERAL) "" else "[${n.tag}] "}${n.text} (${n.date}, ${"%,d".format(n.odometer)} km)")
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Notes for my mechanic")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, "Share notes"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarageApp(vm: GarageViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicleList by vm.vehicles.collectAsStateWithLifecycle()
    val vehicle by vm.activeVehicle.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val tireSets by vm.tireSets.collectAsStateWithLifecycle()
    val tireEvents by vm.tireEvents.collectAsStateWithLifecycle()
    val brakeEntries by vm.brakeEntries.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val pendingPhoto by vm.pendingPhoto.collectAsStateWithLifecycle()
    val insurancePlan by vm.insurancePlan.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()

    // home, maintenance, expenses, tires, brakes, wash, reminders, settings
    var screen by rememberSaveable { mutableStateOf("home") }
    var dialog by remember { mutableStateOf<String?>(null) }

    // What the open dialog is editing (null = adding something new)
    var editingSchedule by remember { mutableStateOf<MaintenanceSchedule?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseLog?>(null) }
    var editingWash by remember { mutableStateOf<ExpenseLog?>(null) }
    var editingSet by remember { mutableStateOf<TireSet?>(null) }
    var editingEvent by remember { mutableStateOf<TireEvent?>(null) }
    var editingNote by remember { mutableStateOf<CarNote?>(null) }
    var editingBrake by remember { mutableStateOf<BrakeEntry?>(null) }
    var setTarget by remember { mutableStateOf<TireSet?>(null) }
    var eventPreset by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    var brakePreset by remember { mutableStateOf<String?>(null) }
    var prefill by remember { mutableStateOf("" to "") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    // Monthly payments that came due while the app was closed are added when it opens.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.refreshRecurring() }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }
    fun keepAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
    val backupFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { keepAccess(it); vm.setBackupFolder(it) }
    }
    val restoreFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { keepAccess(it); vm.restoreFromFolder(it) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.say("Notifications are blocked, so reminders won't show. Enable them in system settings.")
    }
    fun pickPhoto() = photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun openReminderDialog(title: String = "", note: String = "") {
        prefill = title to note
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        dialog = "reminder"
    }

    val list = vehicleList
    val active = vehicle
    when {
        list == null -> Box(Modifier.fillMaxSize().background(Cream))

        list.isEmpty() || active == null -> Scaffold(
            containerColor = Cream,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { pad ->
            LandingScreen(
                onAdd = { dialog = "addVehicle" },
                onImport = { importPicker.launch(arrayOf("*/*")) },
                onRestoreFolder = { restoreFolderPicker.launch(null) },
                modifier = Modifier.padding(pad),
            )
        }

        else -> {
            val titles = mapOf(
                "home" to "My Garage", "maintenance" to "Maintenance", "expenses" to "Expenses", "tires" to "Tires",
                "brakes" to "Brakes", "wash" to "Car wash", "notes" to "Notes", "reminders" to "Reminders", "settings" to "Settings",
            )
            val isRoot = screen == "home" || screen == "reminders" || screen == "settings"
            BackHandler(enabled = screen != "home") { screen = "home" }

            Scaffold(
                containerColor = Cream,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = { Text(titles[screen] ?: "") },
                        navigationIcon = {
                            if (!isRoot) IconButton(onClick = { screen = "home" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        NavigationBarItem(
                            screen != "reminders" && screen != "settings", { screen = "home" },
                            { Icon(Icons.Default.Home, null) }, label = { Text("Home") },
                        )
                        NavigationBarItem(screen == "reminders", { screen = "reminders" }, { Icon(Icons.Default.Notifications, null) }, label = { Text("Reminders") })
                        NavigationBarItem(screen == "settings", { screen = "settings" }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                    }
                },
                floatingActionButton = {
                    when (screen) {
                        "maintenance" -> ExtendedFloatingActionButton(
                            onClick = { editingSchedule = null; dialog = "schedule" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add service") },
                        )
                        "expenses" -> ExtendedFloatingActionButton(
                            onClick = { editingExpense = null; dialog = "entry" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add entry") },
                        )
                        "tires" -> ExtendedFloatingActionButton(
                            onClick = { editingEvent = null; eventPreset = null to null; dialog = "tireEvent" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Log tire event") },
                        )
                        "brakes" -> ExtendedFloatingActionButton(
                            onClick = { editingBrake = null; brakePreset = null; dialog = "brake" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Log brake pads") },
                        )
                        "notes" -> ExtendedFloatingActionButton(
                            onClick = { editingNote = null; dialog = "note" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add note") },
                        )
                        "wash" -> ExtendedFloatingActionButton(
                            onClick = { editingWash = null; dialog = "wash" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Log wash") },
                        )
                        "reminders" -> ExtendedFloatingActionButton(
                            onClick = { openReminderDialog() },
                            icon = { Icon(Icons.Default.AddAlert, null) }, text = { Text("Add reminder") },
                        )
                    }
                },
            ) { pad ->
                val photo = vm.photoFile(active.vehicleId)
                val washes = logs.filter { it.category == Categories.CAR_WASH }.sortedByDescending { it.date }
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.padding(pad),
                    transitionSpec = {
                        // Going back to Home slides in from the left; opening a section from the right.
                        val dir = if (targetState == "home") -1 else 1
                        (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { dir * it / 14 }) togetherWith fadeOut(tween(140))
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        "home" -> HomeScreen(
                            vehicle = active, vehicles = list, schedules = schedules, logs = logs,
                            tireSets = tireSets, tireEvents = tireEvents, brakes = brakeEntries, reminders = reminders, notes = notes,
                            dueSoonKm = settings.dueSoonKm, photoFile = photo, photoVersion = photoVersion,
                            onSelectVehicle = vm::select,
                            onAddVehicle = { dialog = "addVehicle" },
                            onEditOdometer = { dialog = "odometer" },
                            onPickPhoto = ::pickPhoto,
                            onCreateWithAi = { dialog = "aiImage" },
                            onOpen = { screen = it },
                            backupOn = settings.backupUri != null,
                            onSetupBackup = { screen = "settings" },
                        )
                        "maintenance" -> MaintenanceScreen(
                            vehicle = active, schedules = schedules, dueSoonKm = settings.dueSoonKm,
                            onAddSchedule = { editingSchedule = null; dialog = "schedule" },
                            onEditSchedule = { editingSchedule = it; dialog = "schedule" },
                            onMarkDone = vm::markDone,
                            onRemind = { sch ->
                                val left = sch.kmRemaining(active.currentOdometer)
                                openReminderDialog(
                                    "${sch.serviceName} due",
                                    if (left < 0) "Overdue by ${"%,d".format(-left)} km"
                                    else "About ${"%,d".format(left)} km left (due at ${"%,d".format(sch.lastDoneOdometer + sch.intervalKm)} km)",
                                )
                            },
                            onDeleteSchedule = vm::deleteSchedule,
                        )
                        "expenses" -> AnalyticsScreen(
                            active, logs, insurance = insurancePlan, onInsurance = { dialog = "insurance" },
                            onEdit = { editingExpense = it; dialog = "entry" }, onDelete = vm::deleteExpense,
                        )
                        "tires" -> TiresScreen(
                            vehicle = active, sets = tireSets, events = tireEvents,
                            onAddSet = { editingSet = null; dialog = "tireSet" },
                            onEditSet = { editingSet = it; dialog = "tireSet" },
                            onDeleteSet = { setTarget = it; dialog = "deleteSet" },
                            onMountSet = { setTarget = it; dialog = "swap" },
                            onSwap = { setTarget = null; dialog = "swap" },
                            onLogEvent = { type, wheel -> editingEvent = null; eventPreset = type to wheel; dialog = "tireEvent" },
                            onEditEvent = { editingEvent = it; eventPreset = null to null; dialog = "tireEvent" },
                            onDeleteEvent = vm::deleteTireEvent,
                        )
                        "brakes" -> BrakesScreen(
                            vehicle = active, entries = brakeEntries,
                            onLog = { type -> editingBrake = null; brakePreset = type; dialog = "brake" },
                            onEdit = { editingBrake = it; brakePreset = null; dialog = "brake" },
                            onDelete = vm::deleteBrakeEntry,
                        )
                        "wash" -> WashScreen(
                            washes = washes,
                            onLog = { editingWash = null; dialog = "wash" },
                            onEdit = { editingWash = it; dialog = "wash" },
                            onDelete = vm::deleteExpense,
                        )
                        "notes" -> NotesScreen(
                            notes = notes,
                            onAdd = { editingNote = null; dialog = "note" },
                            onEdit = { editingNote = it; dialog = "note" },
                            onToggle = vm::toggleNoteDone,
                            onDelete = vm::deleteNote,
                            onShare = { shareNotes(context, "${active.make} ${active.model}", active.currentOdometer, notes.filter { !it.done }) },
                        )
                        "reminders" -> RemindersScreen(reminders, list, vm::deleteReminder)
                        else -> SettingsScreen(
                            vehicle = active, settings = settings, photoFile = photo, photoVersion = photoVersion,
                            onDueSoonChange = vm::setDueSoonKm, onReminderHourChange = vm::setReminderHour,
                            onBackup = { scope.launch { shareBackup(context, vm.createBackupFile()) } },
                            onImport = { importPicker.launch(arrayOf("*/*")) },
                            onThemeChange = vm::setThemeMode,
                            onChooseBackupFolder = { backupFolderPicker.launch(null) },
                            onBackupNow = vm::backupNow,
                            onStopAutoBackup = vm::stopAutoBackup,
                            onAddVehicle = { dialog = "addVehicle" },
                            onEditVehicle = { dialog = "editVehicle" },
                            onSetOdometer = { dialog = "odometer" },
                            onPickPhoto = ::pickPhoto,
                            onCreateWithAi = { dialog = "aiImage" },
                            onRemovePhoto = vm::removePhoto,
                            insurance = insurancePlan,
                            onInsurance = { dialog = "insurance" },
                            onDeleteVehicle = { dialog = "deleteVehicle" },
                        )
                    }
                }
            }
        }
    }

    pendingPhoto?.let { p -> PhotoPrepareDialog(p.bitmap, onUse = vm::commitPhoto, onCancel = vm::cancelPhoto) }

    when (dialog) {
        "addVehicle" -> VehicleDialog(null, settings.savedColors, vm::addSavedColor, vm::removeSavedColor, { dialog = null }, vm::addVehicle)
        "editVehicle" -> active?.let { v ->
            VehicleDialog(v, settings.savedColors, vm::addSavedColor, vm::removeSavedColor, { dialog = null }) { make, model, year, odo, color ->
                vm.updateVehicle(v.copy(make = make, model = model, year = year, currentOdometer = odo, colorHex = color))
            }
        }
        "odometer" -> active?.let { v -> SetOdometerDialog(v.currentOdometer, { dialog = null }, vm::setOdometer) }

        "entry" -> active?.let { v ->
            val editing = editingExpense
            AddEntryDialog(
                initial = editing,
                defaultOdometer = v.currentOdometer,
                previousFuelOdometer = logs.filter { it.category == Categories.FUEL && it.logId != editing?.logId }.maxOfOrNull { it.odometer },
                onDismiss = { dialog = null },
            ) { date, odo, category, cost, litres, note ->
                if (editing != null) vm.updateExpense(editing.copy(date = date, odometer = odo, category = category, cost = cost, fuelLiters = litres, note = note))
                else vm.addExpense(date, odo, category, cost, litres, note)
            }
        }
        "wash" -> {
            val editing = editingWash
            LogWashDialog(editing, { dialog = null }) { date, cost ->
                if (editing != null) vm.updateExpense(editing.copy(date = date, cost = cost)) else vm.addWash(date, cost)
            }
        }
        "insurance" -> InsurancePlanDialog(insurancePlan, { dialog = null }, vm::saveInsurance, vm::removeInsurance)

        "schedule" -> active?.let { v ->
            val editing = editingSchedule
            ScheduleDialog(v.currentOdometer, editing, { dialog = null }) { name, interval, last ->
                if (editing != null) vm.updateSchedule(editing.copy(serviceName = name, intervalKm = interval, lastDoneOdometer = last))
                else vm.addSchedule(name, interval, last)
            }
        }

        "tireSet" -> TireSetDialog(editingSet, isFirst = tireSets.isEmpty(), onDismiss = { dialog = null }) { name, season, brand, size, km, mountNow ->
            val editing = editingSet
            if (editing != null) vm.updateTireSet(editing.copy(name = name, season = season, brand = brand, size = size, kmBefore = km))
            else vm.addTireSet(name, season, brand, size, km, mountNow)
        }
        "swap" -> active?.let { v ->
            val target = setTarget
            val candidates = if (target != null) listOf(target) else tireSets.filter { !it.mounted }
            val mountedSeason = tireSets.firstOrNull { it.mounted }?.season
            val opposite = when (mountedSeason) { Seasons.SUMMER -> Seasons.WINTER; Seasons.WINTER -> Seasons.SUMMER; else -> null }
            SwapDialog(
                candidates = candidates,
                preselected = target ?: candidates.firstOrNull { it.season == opposite },
                currentOdometer = v.currentOdometer,
                onDismiss = { dialog = null },
                onConfirm = vm::swapTires,
            )
        }
        "deleteSet" -> setTarget?.let { t ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete ${t.name}?") },
                text = { Text("The set is removed from your list. Its history entries stay.") },
                confirmButton = { TextButton({ vm.deleteTireSet(t); dialog = null }) { Text("Delete") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
            )
        }
        "tireEvent" -> active?.let { v ->
            val editing = editingEvent
            TireEventDialog(editing, eventPreset.first, eventPreset.second, v.currentOdometer, { dialog = null }) { type, date, odo, wheel, note, cost, fl, fr, rl, rr ->
                vm.saveTireEvent(editing, type, date, odo, wheel, note, cost, fl, fr, rl, rr)
            }
        }

        "brake" -> active?.let { v ->
            val editing = editingBrake
            BrakeDialog(editing, brakePreset, v.currentOdometer, { dialog = null }) { type, date, odo, front, rear, cost, note ->
                vm.saveBrakeEntry(editing, type, date, odo, front, rear, cost, note)
            }
        }

        "note" -> active?.let { v ->
            val editing = editingNote
            NoteDialog(editing, v.currentOdometer, { dialog = null }) { tag, text, date, odo -> vm.saveNote(editing, tag, text, date, odo) }
        }
        "aiImage" -> active?.let { v -> AiImageDialog(v, { dialog = null }) { dialog = null; pickPhoto() } }
        "reminder" -> AddReminderDialog(settings.reminderHour, prefill.first, prefill.second, { dialog = null }, vm::addReminder)
        "deleteVehicle" -> active?.let { v ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete ${v.make} ${v.model}?") },
                text = { Text("This also deletes its services, expenses, tires, brakes, reminders and photo. It can't be undone, so make a backup first if you're unsure.") },
                confirmButton = { TextButton({ vm.deleteVehicle(v); dialog = null; screen = "home" }) { Text("Delete") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
            )
        }
    }
}
