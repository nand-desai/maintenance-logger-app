package com.garage.tracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
        setContent { GarageTheme { GarageApp(vm) } }
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
    val insurancePlan by vm.insurancePlan.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var wheelsPane by rememberSaveable { mutableIntStateOf(0) } // 0 = tires, 1 = brakes
    var dialog by remember { mutableStateOf<String?>(null) }

    // What the open dialog is editing (null = adding something new)
    var editingSchedule by remember { mutableStateOf<MaintenanceSchedule?>(null) }
    var editingExpense by remember { mutableStateOf<ExpenseLog?>(null) }
    var editingWash by remember { mutableStateOf<ExpenseLog?>(null) }
    var editingSet by remember { mutableStateOf<TireSet?>(null) }
    var editingEvent by remember { mutableStateOf<TireEvent?>(null) }
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
                modifier = Modifier.padding(pad),
            )
        }

        else -> {
            val titles = listOf("My Garage", "Expenses", "Tires & brakes", "Reminders", "Settings")
            Scaffold(
                containerColor = Cream,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = { Text(titles[tab]) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.DirectionsCar, null) }, label = { Text("Garage") })
                        NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DonutLarge, null) }, label = { Text("Expenses") })
                        NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.TireRepair, null) }, label = { Text("Wheels") })
                        NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Notifications, null) }, label = { Text("Reminders") })
                        NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                    }
                },
                floatingActionButton = {
                    when (tab) {
                        2 -> if (wheelsPane == 0) ExtendedFloatingActionButton(
                            onClick = { editingEvent = null; eventPreset = null to null; dialog = "tireEvent" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Log tire event") },
                        ) else ExtendedFloatingActionButton(
                            onClick = { editingBrake = null; brakePreset = null; dialog = "brake" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Log brake pads") },
                        )
                        3 -> ExtendedFloatingActionButton(
                            onClick = { openReminderDialog() },
                            icon = { Icon(Icons.Default.AddAlert, null) }, text = { Text("Add reminder") },
                        )
                        0, 1 -> ExtendedFloatingActionButton(
                            onClick = { editingExpense = null; dialog = "entry" },
                            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add entry") },
                        )
                    }
                },
            ) { pad ->
                val m = Modifier.padding(pad)
                val photo = vm.photoFile(active.vehicleId)
                when (tab) {
                    0 -> DashboardScreen(
                        vehicle = active, vehicles = list, schedules = schedules,
                        washes = logs.filter { it.category == Categories.CAR_WASH }.sortedByDescending { it.date },
                        dueSoonKm = settings.dueSoonKm, photoFile = photo, photoVersion = photoVersion,
                        onSelectVehicle = vm::select,
                        onAddVehicle = { dialog = "addVehicle" },
                        onEditOdometer = { dialog = "odometer" },
                        onPickPhoto = ::pickPhoto,
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
                        onLogWash = { editingWash = null; dialog = "wash" },
                        onEditWash = { editingWash = it; dialog = "wash" },
                        onDeleteWash = vm::deleteExpense,
                        modifier = m,
                    )
                    1 -> AnalyticsScreen(active, logs, onEdit = { editingExpense = it; dialog = "entry" }, onDelete = vm::deleteExpense, modifier = m)
                    2 -> Column(m) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SegmentedButton(wheelsPane == 0, { wheelsPane = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Tires") }
                            SegmentedButton(wheelsPane == 1, { wheelsPane = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Brakes") }
                        }
                        if (wheelsPane == 0) TiresScreen(
                            vehicle = active, sets = tireSets, events = tireEvents,
                            onAddSet = { editingSet = null; dialog = "tireSet" },
                            onEditSet = { editingSet = it; dialog = "tireSet" },
                            onDeleteSet = { setTarget = it; dialog = "deleteSet" },
                            onMountSet = { setTarget = it; dialog = "swap" },
                            onSwap = { setTarget = null; dialog = "swap" },
                            onLogEvent = { type, wheel -> editingEvent = null; eventPreset = type to wheel; dialog = "tireEvent" },
                            onEditEvent = { editingEvent = it; eventPreset = null to null; dialog = "tireEvent" },
                            onDeleteEvent = vm::deleteTireEvent,
                            modifier = Modifier.weight(1f),
                        ) else BrakesScreen(
                            vehicle = active, entries = brakeEntries,
                            onLog = { type -> editingBrake = null; brakePreset = type; dialog = "brake" },
                            onEdit = { editingBrake = it; brakePreset = null; dialog = "brake" },
                            onDelete = vm::deleteBrakeEntry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    3 -> RemindersScreen(reminders, list, vm::deleteReminder, m)
                    else -> SettingsScreen(
                        vehicle = active, settings = settings, photoFile = photo, photoVersion = photoVersion,
                        onDueSoonChange = vm::setDueSoonKm, onReminderHourChange = vm::setReminderHour,
                        onBackup = { scope.launch { shareBackup(context, vm.createBackupFile()) } },
                        onImport = { importPicker.launch(arrayOf("*/*")) },
                        onAddVehicle = { dialog = "addVehicle" },
                        onEditVehicle = { dialog = "editVehicle" },
                        onSetOdometer = { dialog = "odometer" },
                        onPickPhoto = ::pickPhoto,
                        onRemovePhoto = vm::removePhoto,
                        insurance = insurancePlan,
                        onInsurance = { dialog = "insurance" },
                        onDeleteVehicle = { dialog = "deleteVehicle" },
                        modifier = m,
                    )
                }
            }
        }
    }

    when (dialog) {
        "addVehicle" -> VehicleDialog(null, { dialog = null }, vm::addVehicle)
        "editVehicle" -> active?.let { v ->
            VehicleDialog(v, { dialog = null }) { make, model, year, odo, color ->
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
            ) { date, odo, category, cost, litres ->
                if (editing != null) vm.updateExpense(editing.copy(date = date, odometer = odo, category = category, cost = cost, fuelLiters = litres))
                else vm.addExpense(date, odo, category, cost, litres)
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

        "reminder" -> AddReminderDialog(settings.reminderHour, prefill.first, prefill.second, { dialog = null }, vm::addReminder)
        "deleteVehicle" -> active?.let { v ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete ${v.make} ${v.model}?") },
                text = { Text("This also deletes its services, expenses, tires, brakes, reminders and photo. It can't be undone, so make a backup first if you're unsure.") },
                confirmButton = { TextButton({ vm.deleteVehicle(v); dialog = null; tab = 0 }) { Text("Delete") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
            )
        }
    }
}
