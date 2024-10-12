package com.androplus.pwdmgr.services
import java.security.SecureRandom

class PasswordGenerator {

    companion object {
        fun generatePassword(
            length: Int = 12,
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeNumbers: Boolean = true,
            includeSpecial: Boolean = true
        ): String {
            val uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val lowercase = "abcdefghijklmnopqrstuvwxyz"
            val numbers = "0123456789"
            val special = "!@#\$%^&*()-_+=<>?"

            var chars = ""
            if (includeUppercase) chars += uppercase
            if (includeLowercase) chars += lowercase
            if (includeNumbers) chars += numbers
            if (includeSpecial) chars += special

            if (chars.isEmpty()) return ""

            val random = SecureRandom()
            val password = StringBuilder()
            for (i in 0 until length) {
                val randomIndex = random.nextInt(chars.length)
                password.append(chars[randomIndex])
            }
            return password.toString()
        }
    }

}
