package com.androplus.pwdmgr.fragment


import android.os.Bundle
import android.os.Environment
import android.os.Environment.getExternalStoragePublicDirectory
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.AppField
import com.androplus.pwdmgr.services.RealmService
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
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "download_option"
            -> {

                val jsonFile = File(getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "my_data.json")
                val jsonWriter = FileWriter(jsonFile)
                val gson = GsonBuilder().setPrettyPrinting().create()

                gson.toJson(exportApplicationsToJson(), jsonWriter)
                jsonWriter.close()

                Toast.makeText(preference.context, jsonFile.absolutePath, Toast.LENGTH_SHORT).show()
                return true
            }
            else -> {
                return super.onPreferenceTreeClick(preferenceScreen)
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
                fieldsArray.add(jsonObject)
               // val appField = gson.fromJson(jsonObject, AppField::class.java)

            };

            appJsonObject.add("fields", fieldsArray);
            exportArray.add(appJsonObject)
        }

        return exportArray
    }

}
