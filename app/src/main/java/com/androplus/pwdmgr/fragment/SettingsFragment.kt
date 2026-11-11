package com.androplus.pwdmgr.fragment


import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.AppField
import com.androplus.pwdmgr.services.EncryptDecrypt
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.services.ServerSync
import com.androplus.pwdmgr.worker.SmsHistoryWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.androplus.pwdmgr.utils.UploadClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileWriter

class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        private const val REQUEST_READ_SMS = 102
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        setupServerSyncPreferences()
    }

    override fun onResume() {
        super.onResume()
        updateSyncSummary()
    }

    private fun setupServerSyncPreferences() {
        findPreference<EditTextPreference>(ServerSync.PREF_URL)?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
                p.text?.takeIf { it.isNotBlank() } ?: "e.g. https://finintel-xxxx.onrender.com"
            }
            setOnPreferenceChangeListener { _, value ->
                val ok = UploadClient.isValidServerUrl(value as String)
                if (!ok) Toast.makeText(context, "Server URL must start with https://", Toast.LENGTH_LONG).show()
                else ServerSync.schedule(requireContext())
                ok
            }
        }
        findPreference<EditTextPreference>(ServerSync.PREF_TOKEN)?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            // Never show the token itself on screen.
            summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
                if (p.text.isNullOrBlank()) "Not set" else "Set (hidden)"
            }
            setOnPreferenceChangeListener { _, _ ->
                ServerSync.schedule(requireContext())
                true
            }
        }
    }

    private fun chooseImportPeriod() {
        val labels = arrayOf("Last 30 days", "Last 90 days", "Last 180 days")
        val days = intArrayOf(30, 90, 180)
        AlertDialog.Builder(context)
            .setTitle("Import past bank SMS")
            .setItems(labels) { _, which -> startHistoryImport(days[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startHistoryImport(days: Int) {
        val request = OneTimeWorkRequestBuilder<SmsHistoryWorker>()
            .setInputData(workDataOf(SmsHistoryWorker.KEY_DAYS to days))
            .build()
        val wm = WorkManager.getInstance(requireContext())
        wm.enqueueUniqueWork(SmsHistoryWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
        Toast.makeText(context, "Importing the last $days days of bank SMS…", Toast.LENGTH_SHORT).show()

        val appContext = requireContext().applicationContext
        wm.getWorkInfoByIdLiveData(request.id).observe(this) { info ->
            if (info == null || !info.state.isFinished) return@observe
            val out = info.outputData
            val message = if (info.state == WorkInfo.State.SUCCEEDED) {
                val found = out.getInt(SmsHistoryWorker.KEY_RECORDED, 0)
                val queued = out.getInt(SmsHistoryWorker.KEY_QUEUED, 0)
                "Found $found transactions in ${out.getInt(SmsHistoryWorker.KEY_SCANNED, 0)} bank SMS" +
                    (if (queued > 0) "; $queued queued for the server." else ".") +
                    " Already-saved ones were skipped."
            } else {
                "Import failed: " + (out.getString(SmsHistoryWorker.KEY_ERROR) ?: "unknown error")
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            if (isAdded) updateSyncSummary()
        }
    }

    private fun updateSyncSummary() {
        val pending = ServerSync.outbox(requireContext()).size()
        findPreference<Preference>("server_sync_now")?.summary =
            if (pending == 0) "Nothing waiting to upload" else "$pending message(s) waiting to upload"
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "sms_tracking_enabled" -> {
                val isEnabled = (preference as SwitchPreferenceCompat).isChecked
                if (isEnabled) {
                    checkSmsPermission()
                }
                return true
            }
            "payment_app_tracking_enabled" -> {
                val isEnabled = (preference as SwitchPreferenceCompat).isChecked
                if (isEnabled) {
                    checkNotificationAccess()
                }
                return true
            }
            "import_past_sms" -> {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    chooseImportPeriod()
                } else {
                    requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
                }
                return true
            }
            "server_sync_now" -> {
                ServerSync.schedule(requireContext())
                Toast.makeText(context, "Uploading in the background", Toast.LENGTH_SHORT).show()
                updateSyncSummary()
                return true
            }
            "download_option" -> {
                showPasswordDialog()
                return true
            }
            else -> {
                return super.onPreferenceTreeClick(preference)
            }
        }
    }

    private fun checkNotificationAccess() {
        val notificationListenerString = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        if (notificationListenerString == null || !notificationListenerString.contains(requireContext().packageName)) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(context, "Please enable Notification Access for Expense Tracker", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPasswordDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Encrypt Export")
        builder.setMessage("Enter a password to encrypt the exported file:")

        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Export") { dialog, _ ->
            val password = input.text.toString()
            if (password.isNotEmpty()) {
                exportData(password)
            } else {
                Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun exportData(password: String) {
        try {
            val jsonArray = exportApplicationsToJson()
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(jsonArray)

            // Encrypt the JSON string
            val encryptedString = EncryptDecrypt.getInstance().encryptNew(jsonString.toByteArray(Charsets.UTF_8), password)

            val file = File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "my_data_encrypted.enc")
            val writer = FileWriter(file)
            writer.write(encryptedString)
            writer.close()

            Toast.makeText(context, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_READ_SMS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                chooseImportPeriod()
            } else {
                Toast.makeText(context, "SMS permission is needed to import past messages.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "SMS Permission Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Permission denied. SMS tracking will not work.", Toast.LENGTH_LONG).show()
                // Optionally toggle the switch back off
                (findPreference<SwitchPreferenceCompat>("sms_tracking_enabled"))?.isChecked = false
            }
        }
    }

    fun exportApplicationsToJson(): JsonArray {
        val exportArray= JsonArray()
        val results = RealmService.getInstance().getAllUserApplications()
        for (selectApplication in results) {
            val appFieldsString = selectApplication!!.app_fields_json_array
            val jsonParser = JsonParser()
            val fieldsArray = JsonArray()
            val appJsonObject = JsonObject()
            appJsonObject.addProperty("app_name", selectApplication.app_name)
            jsonParser.parse(appFieldsString).asJsonArray.forEachIndexed { index, jsonElement ->
                val gson = Gson()
                val jsonObject = jsonElement.asString
                val appField = gson.fromJson(jsonObject, AppField::class.java)
                // AppField data is already plain text (decrypted by Realm upon access),
                // so we don't need to decrypt it again.
                fieldsArray.add(gson.toJsonTree(appField))
            }

            appJsonObject.add("fields", fieldsArray)
            exportArray.add(appJsonObject)
        }

        return exportArray
    }

}
