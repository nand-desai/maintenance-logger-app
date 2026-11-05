package com.garage.tracker.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.garage.tracker.data.ExpenseLog
import com.garage.tracker.data.GarageDatabase
import com.garage.tracker.data.MaintenanceSchedule
import com.garage.tracker.data.Vehicle
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Two-way sync between Room and a personal Google Spreadsheet using the Sheets v4 REST API.
 *
 * Each table maps to a tab (Vehicles, Maintenance_Schedules, Expense_Logs) with the column order of
 * the schema in PROMPT.md. [push] rewrites each tab from local data (idempotent, no duplicate rows
 * on repeated syncs); [pull] upserts every sheet row into Room.
 */
class GoogleSheetsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("sheets", Context.MODE_PRIVATE)

    var spreadsheetId: String
        get() = prefs.getString("spreadsheetId", "") ?: ""
        set(v) = prefs.edit().putString("spreadsheetId", v.trim()).apply()

    private val signInClient: GoogleSignInClient by lazy {
        val opts = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SHEETS_SCOPE))
            .build()
        GoogleSignIn.getClient(context, opts)
    }

    fun signedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf { it.grantedScopes.contains(Scope(SHEETS_SCOPE)) }

    fun signInIntent(): Intent = signInClient.signInIntent

    fun signOut() { signInClient.signOut() }

    private suspend fun token(account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, "oauth2:$SHEETS_SCOPE")
    }

    suspend fun push(): Result<Unit> = runCatching {
        val (acct, id) = requireReady()
        val t = token(acct)
        val db = GarageDatabase.get(context)
        ensureTabs(id, t)
        writeTab(id, t, VEHICLES, VEHICLE_HEADER, db.vehicleDao().getAll().map {
            listOf(it.vehicleId, it.make, it.model, it.year, it.currentOdometer, it.colorHex)
        })
        writeTab(id, t, SCHEDULES, SCHEDULE_HEADER, db.scheduleDao().getAll().map {
            listOf(it.scheduleId, it.vehicleId, it.serviceName, it.intervalMiles, it.lastDoneOdometer)
        })
        writeTab(id, t, EXPENSES, EXPENSE_HEADER, db.expenseDao().getAll().map {
            listOf(it.logId, it.vehicleId, it.date, it.odometer, it.category, it.cost, it.fuelGallons)
        })
    }

    suspend fun pull(): Result<Unit> = runCatching {
        val (acct, id) = requireReady()
        val t = token(acct)
        val db = GarageDatabase.get(context)
        readTab(id, t, VEHICLES).forEach { r ->
            db.vehicleDao().upsert(Vehicle(r[0], r[1], r[2], r[3].toInt(), r[4].toInt(), r.getOrElse(5) { "#3B82F6" }))
        }
        readTab(id, t, SCHEDULES).forEach { r ->
            db.scheduleDao().upsert(MaintenanceSchedule(r[0], r[1], r[2], r[3].toInt(), r[4].toInt()))
        }
        readTab(id, t, EXPENSES).forEach { r ->
            db.expenseDao().upsert(
                ExpenseLog(r[0], r[1], r[2], r[3].toInt(), r[4], r[5].toDouble(), r.getOrElse(6) { "0" }.toDoubleOrNull() ?: 0.0),
            )
        }
    }

    /** Pull remote changes first, then push the merged result. */
    suspend fun sync(): Result<Unit> = pull().mapCatching { push().getOrThrow() }

    // ---- REST helpers -------------------------------------------------------------------------

    private fun requireReady(): Pair<Account, String> {
        val account = signedInAccount()?.account ?: error("Sign in with Google first")
        val id = spreadsheetId.ifBlank { error("Enter a spreadsheet ID first") }
        return account to id
    }

    private suspend fun ensureTabs(id: String, token: String) {
        val meta = JSONObject(call("GET", "$BASE/$id?fields=sheets.properties.title", token))
        val existing = (0 until (meta.optJSONArray("sheets")?.length() ?: 0))
            .map { meta.getJSONArray("sheets").getJSONObject(it).getJSONObject("properties").getString("title") }
        val missing = listOf(VEHICLES, SCHEDULES, EXPENSES).filter { it !in existing }
        if (missing.isEmpty()) return
        val requests = JSONArray(missing.map {
            JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", it)))
        })
        call("POST", "$BASE/$id:batchUpdate", token, JSONObject().put("requests", requests).toString())
    }

    private suspend fun writeTab(id: String, token: String, tab: String, header: List<String>, rows: List<List<Any>>) {
        call("POST", "$BASE/$id/values/${enc(tab)}:clear", token, "{}")
        val values = JSONArray().put(JSONArray(header)).also { arr -> rows.forEach { arr.put(JSONArray(it)) } }
        val body = JSONObject().put("values", values).toString()
        call("PUT", "$BASE/$id/values/${enc("$tab!A1")}?valueInputOption=RAW", token, body)
    }

    /** Returns data rows (header skipped) as strings; rows that are too short are dropped. */
    private suspend fun readTab(id: String, token: String, tab: String): List<List<String>> {
        val json = JSONObject(call("GET", "$BASE/$id/values/${enc(tab)}", token))
        val rows = json.optJSONArray("values") ?: return emptyList()
        val minCols = when (tab) { VEHICLES -> 5; SCHEDULES -> 5; else -> 6 }
        return (1 until rows.length()).map { i ->
            val r = rows.getJSONArray(i)
            List(r.length()) { r.get(it).toString() }
        }.filter { it.size >= minCols }
    }

    private suspend fun call(method: String, url: String, token: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Sheets API $code: $text")
                text
            } finally {
                conn.disconnect()
            }
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        private const val BASE = "https://sheets.googleapis.com/v4/spreadsheets"
        const val VEHICLES = "Vehicles"
        const val SCHEDULES = "Maintenance_Schedules"
        const val EXPENSES = "Expense_Logs"
        private val VEHICLE_HEADER = listOf("vehicle_id", "make", "model", "year", "current_odometer", "color_hex")
        private val SCHEDULE_HEADER = listOf("schedule_id", "vehicle_id", "service_name", "interval_miles", "last_done_odometer")
        private val EXPENSE_HEADER = listOf("log_id", "vehicle_id", "date", "odometer", "category", "cost", "fuel_gallons")
    }
}
