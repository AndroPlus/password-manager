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
import com.androplus.pwdmgr.utils.UploadClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileWriter

class SettingsFragment : PreferenceFragmentCompat() {

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
