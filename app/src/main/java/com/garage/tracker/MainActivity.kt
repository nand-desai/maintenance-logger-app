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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garage.tracker.data.Categories
import com.garage.tracker.data.MaintenanceSchedule
import com.garage.tracker.data.kmRemaining
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
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editingSchedule by remember { mutableStateOf<MaintenanceSchedule?>(null) }
    var prefill by remember { mutableStateOf("" to "") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

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
            val titles = listOf("My Garage", "Expenses", "Reminders", "Settings")
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
                        NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Notifications, null) }, label = { Text("Reminders") })
                        NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                    }
                },
                floatingActionButton = {
                    when (tab) {
                        2 -> ExtendedFloatingActionButton(
                            onClick = { openReminderDialog() },
                            icon = { Icon(Icons.Default.AddAlert, null) }, text = { Text("Add reminder") },
                        )
                        0, 1 -> ExtendedFloatingActionButton(
                            onClick = { dialog = "entry" },
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
                        modifier = m,
                    )
                    1 -> AnalyticsScreen(active, logs, vm::deleteExpense, m)
                    2 -> RemindersScreen(reminders, list, vm::deleteReminder, m)
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
            AddEntryDialog(
                defaultOdometer = v.currentOdometer,
                previousFuelOdometer = logs.filter { it.category == Categories.FUEL }.maxOfOrNull { it.odometer },
                onDismiss = { dialog = null }, onSave = vm::addExpense,
            )
        }
        "schedule" -> active?.let { v ->
            val editing = editingSchedule
            ScheduleDialog(v.currentOdometer, editing, { dialog = null }) { name, interval, last ->
                if (editing != null) vm.updateSchedule(editing.copy(serviceName = name, intervalKm = interval, lastDoneOdometer = last))
                else vm.addSchedule(name, interval, last)
            }
        }
        "reminder" -> AddReminderDialog(settings.reminderHour, prefill.first, prefill.second, { dialog = null }, vm::addReminder)
        "deleteVehicle" -> active?.let { v ->
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Delete ${v.make} ${v.model}?") },
                text = { Text("This also deletes its services, expenses, reminders and photo. It can't be undone, so make a backup first if you're unsure.") },
                confirmButton = { TextButton({ vm.deleteVehicle(v); dialog = null; tab = 0 }) { Text("Delete") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
            )
        }
    }
}
