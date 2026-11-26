package com.androplus.pwdmgr.services

import android.content.Context
import com.androplus.pwdmgr.utils.ExpenseGrouping
import com.androplus.pwdmgr.utils.PayeeNames
import java.io.File

/** The phone's store of your own payee names, wired into how names are shown. */
object Nicknames {
    @Volatile private var names: PayeeNames? = null

    fun init(context: Context) {
        if (names != null) return
        val n = PayeeNames(File(context.applicationContext.filesDir, "payee_names.json"))
        names = n
        ExpenseGrouping.nicknameOf = { n.get(it) }
    }

    /** Re-reads names from disk (after a restore). */
    fun reload(context: Context) {
        names = null
        init(context)
    }

    fun get(payee: String): String? = names?.get(payee)

    fun set(payee: String, name: String?): Boolean = names?.set(payee, name) ?: false
}
