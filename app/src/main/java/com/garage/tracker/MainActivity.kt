package com.garage.tracker

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garage.tracker.data.Categories
import com.garage.tracker.ui.screens.*
import com.garage.tracker.ui.theme.Cream
import com.garage.tracker.ui.theme.GarageTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn

class MainActivity : ComponentActivity() {
    private val vm: GarageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GarageTheme { GarageApp(vm) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GarageApp(vm: GarageViewModel) {
    val vehicle by vm.activeVehicle.collectAsStateWithLifecycle()
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        status?.takeIf { it != "Syncing…" }?.let { snackbar.showSnackbar(it); vm.clearSyncStatus() }
    }

    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result }
            .onSuccess { if (vm.sheets.spreadsheetId.isBlank()) dialog = "sheet" else vm.sync() }
            .onFailure { vm.reportSyncStatus("Google sign-in failed") }
    }
    fun startSync() = when {
        vm.sheets.signedInAccount() == null -> signIn.launch(vm.sheets.signInIntent())
        vm.sheets.spreadsheetId.isBlank() -> dialog = "sheet"
        else -> vm.sync()
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "My Garage" else "Expenses") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                actions = {
                    IconButton(onClick = { dialog = "sheet" }) { Icon(Icons.Default.Settings, "Sheet settings") }
                    IconButton(onClick = ::startSync) {
                        if (status == "Syncing…") CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CloudSync, "Sync with Google Sheets")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.DirectionsCar, null) }, label = { Text("Garage") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DonutLarge, null) }, label = { Text("Expenses") })
            }
        },
        floatingActionButton = {
            if (vehicle != null) ExtendedFloatingActionButton(
                onClick = { dialog = "entry" },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add entry") },
            )
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        if (tab == 0) DashboardScreen(
            vehicle, vehicles, schedules,
            onSelectVehicle = vm::select, onAddVehicle = { dialog = "vehicle" }, onBump = vm::bumpOdometer,
            onAddSchedule = { dialog = "schedule" }, onMarkDone = vm::markDone, onDeleteSchedule = vm::deleteSchedule,
            modifier = m,
        ) else AnalyticsScreen(vehicle, logs, vm::deleteExpense, m)
    }

    when (dialog) {
        "entry" -> AddEntryDialog(
            defaultOdometer = vehicle?.currentOdometer ?: 0,
            previousFuelOdometer = logs.filter { it.category == Categories.FUEL }.maxOfOrNull { it.odometer },
            onDismiss = { dialog = null }, onSave = vm::addExpense,
        )
        "vehicle" -> AddVehicleDialog({ dialog = null }, vm::addVehicle)
        "schedule" -> AddScheduleDialog(vehicle?.currentOdometer ?: 0, { dialog = null }, vm::addSchedule)
        "sheet" -> SpreadsheetIdDialog(vm.sheets.spreadsheetId, { dialog = null }) { vm.sheets.spreadsheetId = it }
    }
}
