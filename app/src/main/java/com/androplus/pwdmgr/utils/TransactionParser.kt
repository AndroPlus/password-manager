package com.androplus.pwdmgr.utils

import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val isExpense: Boolean = true
)

object TransactionParser {
    // Regex for patterns like: "Paid ₹150.00 to Merchant", "Spent Rs 500 at Store", "Txn of INR 1,200.00"
    private val amountPatterns = listOf(
        Pattern.compile("(?:RS|INR|₹|RE|amount)\\.?\\s*([\\d,]+\\.?\\d{0,2})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([\\d,]+\\.?\\d{0,2})\\s*(?:RS|INR|₹|RE)", Pattern.CASE_INSENSITIVE)
    )

    private val merchantKeywords = listOf("at", "to", "vpa", "paid to", "spent on")

    fun parseSms(message: String): ParsedTransaction? {
        val cleanMessage = message.replace(",", "")
        
        var amount: Double? = null
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(cleanMessage)
            if (matcher.find()) {
                amount = matcher.group(1)?.toDoubleOrNull()
                break
            }
        }

        if (amount == null) return null

        // Try to extract merchant/description
        var merchant = "Online Payment"
        for (keyword in merchantKeywords) {
            val regex = Pattern.compile("$keyword\\s+([^\\.\\n\\,]{2,20})", Pattern.CASE_INSENSITIVE)
            val matcher = regex.matcher(cleanMessage)
            if (matcher.find()) {
                merchant = matcher.group(1)?.trim() ?: merchant
                break
            }
        }

        // Determine if it's credit or debit
        val isCredit = message.contains("credited", ignoreCase = true) || message.contains("received", ignoreCase = true)
        
        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            isExpense = !isCredit
        )
    }
}
