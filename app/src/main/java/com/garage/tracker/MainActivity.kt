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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import com.garage.tracker.data.milesRemaining
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
    val vehicle by vm.activeVehicle.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var prefill by remember { mutableStateOf("" to "") }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.say("Notifications are blocked, so reminders won't show. Enable them in system settings.")
    }
    fun openReminderDialog(title: String = "", note: String = "") {
        prefill = title to note
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        dialog = "reminder"
    }

    val titles = listOf("My Garage", "Expenses", "Reminders")
    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titles[tab]) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Back up (share JSON)") }, leadingIcon = { Icon(Icons.Default.IosShare, null) },
                            onClick = { menu = false; scope.launch { shareBackup(context, vm.createBackupFile()) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Import backup") }, leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                            onClick = { menu = false; importPicker.launch(arrayOf("*/*")) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.DirectionsCar, null) }, label = { Text("Garage") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DonutLarge, null) }, label = { Text("Expenses") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Notifications, null) }, label = { Text("Reminders") })
            }
        },
        floatingActionButton = {
            if (tab == 2) ExtendedFloatingActionButton(
                onClick = { openReminderDialog() },
                icon = { Icon(Icons.Default.AddAlert, null) }, text = { Text("Add reminder") },
            ) else if (vehicle != null) ExtendedFloatingActionButton(
                onClick = { dialog = "entry" },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add entry") },
            )
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> DashboardScreen(
                vehicle, vehicles, schedules,
                onSelectVehicle = vm::select, onAddVehicle = { dialog = "vehicle" }, onBump = vm::bumpOdometer,
                onAddSchedule = { dialog = "schedule" }, onMarkDone = vm::markDone,
                onRemind = { sch ->
                    val left = sch.milesRemaining(vehicle?.currentOdometer ?: 0)
                    openReminderDialog(
                        "${sch.serviceName} due",
                        if (left < 0) "Overdue by ${"%,d".format(-left)} mi" else "About ${"%,d".format(left)} mi left (due at ${"%,d".format(sch.lastDoneOdometer + sch.intervalMiles)} mi)",
                    )
                }, onDeleteSchedule = vm::deleteSchedule,
                modifier = m,
            )
            1 -> AnalyticsScreen(vehicle, logs, vm::deleteExpense, m)
            else -> RemindersScreen(reminders, vehicles, vm::deleteReminder, m)
        }
    }

    when (dialog) {
        "entry" -> AddEntryDialog(
            defaultOdometer = vehicle?.currentOdometer ?: 0,
            previousFuelOdometer = logs.filter { it.category == Categories.FUEL }.maxOfOrNull { it.odometer },
            onDismiss = { dialog = null }, onSave = vm::addExpense,
        )
        "vehicle" -> AddVehicleDialog({ dialog = null }, vm::addVehicle)
        "schedule" -> AddScheduleDialog(vehicle?.currentOdometer ?: 0, { dialog = null }, vm::addSchedule)
        "reminder" -> AddReminderDialog(prefill.first, prefill.second, { dialog = null }, vm::addReminder)
    }
}
