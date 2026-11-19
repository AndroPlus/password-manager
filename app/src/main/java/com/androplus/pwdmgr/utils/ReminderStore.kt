package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Reminders kept in a small JSON file on the phone (no database change). */
class ReminderStore(private val file: File) {
    private val gson = Gson()

    @Synchronized
    fun all(): List<Reminder> = runCatching {
        if (!file.exists()) emptyList()
        else gson.fromJson<List<Reminder>>(file.readText(), object : TypeToken<List<Reminder>>() {}.type)
    }.getOrNull().orEmpty()

    @Synchronized
    fun save(r: Reminder) = write(all().filterNot { it.id == r.id } + r)

    @Synchronized
    fun delete(id: String) = write(all().filterNot { it.id == id })

    @Synchronized
    fun get(id: String): Reminder? = all().firstOrNull { it.id == id }

    private fun write(list: List<Reminder>) = file.writeText(gson.toJson(list))
}
