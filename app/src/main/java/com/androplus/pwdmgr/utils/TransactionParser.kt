package com.androplus.pwdmgr.utils

/**
 * Turns Indian bank SMS and UPI-app notifications into transactions.
 *
 * Deliberately conservative: anything that is not clearly a completed money
 * movement (OTP, balance alert, promo, payment request, "will be debited"
 * reminder) returns null, so junk never becomes an expense.
 *
 * Pure Kotlin (no Android imports) so it can be unit-tested on the JVM.
 */
data class ParsedTransaction(
    val amount: Double,
    val merchant: String,
    val isExpense: Boolean = true,
    val channel: String = "Bank",   // UPI | Card | ATM | IMPS/NEFT | Wallet | AutoDebit | Bank
    val last4: String? = null,
)

object TransactionParser {

    private val amountRegex =
        Regex("""(?:rs|inr|₹)\.?\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // Masked account/card number: "a/c XX1234", "Acct XX123", "Card **5678", "AC X1234".
    private val last4Regex =
        Regex("""(?:a/?c|acct|account|card|ac)\D{0,6}?[x*]+\s*0*(\d{3,4})""", RegexOption.IGNORE_CASE)
    private val cardEndingRegex = Regex("""ending(?:\s+with)?\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val plainCardRegex = Regex("""\bcard\s+(?:no\.?\s*)?(\d{4})\b""", RegexOption.IGNORE_CASE)

    // Axis: "UPI/P2M/<ref>/<NAME>", "UPI TO MERCHANT : NAME (", "UPI TRANSFER TO NAME ("
    private val upiPathRegex = Regex("""UPI/P2[AMP]/\d+/([^/\n]+)""", RegexOption.IGNORE_CASE)
    private val upiNarrationRegex =
        Regex("""UPI (?:TO MERCHANT\s*:|TRANSFER TO)\s*([^(\n]+?)\s*\(""", RegexOption.IGNORE_CASE)

    private val vpaRegex = Regex("""\b([a-z0-9._-]{2,}@[a-z]{2,})\b""", RegexOption.IGNORE_CASE)

    // Phrases after "from/to/for" that are the user's own account or an amount, not a payee.
    private val notAMerchant = Regex(
        """^(?:a/?c|ac\b|acct|account|card|your|rs\b|inr|₹|upi\b|neft|imps|\d+$|x+\d)""",
        RegexOption.IGNORE_CASE,
    )

    // Messages that mention money but are not a completed transaction.
    private val smsRejects = listOf(
        "otp", "one time password", "verification code", "do not share", "is your",
        "will be debited", "will be credited", "requested", "requesting", "request money",
        "e-mandate registered", "mandate is set", "due on", "min amt due", "minimum amount due",
        "reminder", "statement is", "statement for", "cashback of", "congratulations", "win ",
        "declined", "failed", "unsuccessful", "not successful", "could not be",
    )

    private val notificationRejects = listOf(
        "request", "reminder", "cashback", "reward", "scratch", "won ", "offer",
        "failed", "declined", "pending", "processing", "otp", "due on", "expires", "expiring",
    )

    private val debitWords = listOf(
        "debited", "debit", "spent", "withdrawn", "paid", "purchase", "sent",
        "deducted", "transferred to", "used for",
        // HDFC card alerts: "Txn Rs.30.00 On HDFC Bank Card 1234 At ... by UPI"
        "txn",
    )
    private val creditWords = listOf("credited", "credit", "received", "deposited", "refund")

    /** Parse a bank SMS. Requires an account/card reference, so promos don't slip through. */
    fun parseSms(message: String): ParsedTransaction? {
        val text = message.trim()
        if (text.isEmpty()) return null
        val low = text.lowercase()
        if (smsRejects.any { it in low }) return null

        val amount = firstAmount(text) ?: return null
        val isExpense = detectIsExpense(low) ?: return null

        val last4 = last4Regex.find(text)?.groupValues?.get(1)?.takeLast(4)
            ?: cardEndingRegex.find(text)?.groupValues?.get(1)
            ?: plainCardRegex.find(text)?.groupValues?.get(1)
        val hasAccountRef = last4 != null ||
            listOf("a/c", "acct", "account", "card").any { it in low }
        if (!hasAccountRef) return null

        val channel = detectChannel(low)
        return ParsedTransaction(
            amount = amount,
            merchant = extractMerchant(text, channel),
            isExpense = isExpense,
            channel = channel,
            last4 = last4,
        )
    }

    // ---- merchant receipts ---------------------------------------------------
    // Some merchants confirm a payment themselves, without naming an account:
    // "Transaction of Rs. 954.00 for your LPG booking is successful ... Regards, HP PAY Team".

    private val receiptAmountRegex = Regex(
        """(?:transaction|txn|payment|purchase|order|recharge|booking)\s+(?:amount\s+)?(?:of\s+)?(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val receivedYourPaymentRegex = Regex(
        """received\s+(?:your\s+)?payment\s+of\s+(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )
    private val receiptSuccessWords = listOf("successful", "success", "completed", "confirmed", "is done", "received your payment")
    private val receiptRejects = listOf(
        "refund", "cashback", "reversal", "reversed", "credited to your", "failed", "declined",
        "unsuccessful", "not successful", "pending", "offer", "win ", "won ", "otp", "due on",
        "reminder", "request", "will be", "expire", "get rs", "upto", "up to",
    )
    private val signatureRegex = Regex("""(?:regards|thanks|thank you|team)[,.:\s-]*\n?\s*([A-Za-z][A-Za-z0-9 &.'-]{1,30}?)\s*(?:team)?\s*\.?\s*$""",
        RegexOption.IGNORE_CASE)
    private val trailingDashNameRegex = Regex("""[-–]\s*([A-Za-z][A-Za-z0-9 &.']{1,30})\s*\.?\s*$""")

    /**
     * A merchant's own "payment successful" SMS. Used when [parseSms] finds no bank
     * message. [sender] is the SMS header (e.g. "VM-HPPAYS"), the payee of last resort.
     */
    fun parseMerchantReceipt(message: String, sender: String? = null): ParsedTransaction? {
        val text = message.trim()
        if (text.isEmpty()) return null
        val low = text.lowercase()
        if (receiptRejects.any { it in low } || smsRejects.any { it in low }) return null
        if (receiptSuccessWords.none { it in low }) return null
        val raw = (receiptAmountRegex.find(text) ?: receivedYourPaymentRegex.find(text))
            ?.groupValues?.get(1) ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return ParsedTransaction(
            amount = amount,
            merchant = receiptMerchant(text, sender),
            isExpense = true,
            channel = "Merchant",
            last4 = null,
        )
    }

    private fun receiptMerchant(text: String, sender: String?): String {
        signatureRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }?.let { return it }
        trailingDashNameRegex.find(text)?.groupValues?.get(1)?.trim()?.let { return it }
        // "VM-HPPAYS" / "AD-ZOMATO-S" → "HPPAYS" / "ZOMATO"
        val header = sender?.substringAfter('-', sender)?.substringBefore('-')?.trim()
        return header?.takeIf { it.any(Char::isLetter) && it.length >= 3 } ?: "Merchant payment"
    }

    /** Parse a GPay / PhonePe / Paytm notification (title + text). */
    fun parseNotification(title: String, text: String): ParsedTransaction? {
        // Newline, not space: the merchant pattern must not run from title into body.
        val full = "$title\n$text".trim()
        if (full.isEmpty()) return null
        val low = full.lowercase()
        if (notificationRejects.any { it in low }) return null

        val amount = firstAmount(full) ?: return null
        val isExpense = when {
            listOf("received", "credited", "sent you", "added to").any { it in low } -> false
            listOf("paid", "sent", "payment to", "payment of", "debited", "transferred")
                .any { it in low } -> true
            else -> return null   // e.g. "Pay ₹500 to Airtel" is a prompt, not a payment
        }
        val merchant = Regex(
            """(?:paid to|sent to|payment to|to|from)\s+([A-Za-z0-9][A-Za-z0-9 &._'-]{1,40})""",
            RegexOption.IGNORE_CASE,
        ).find(full)?.groupValues?.get(1)?.let { trimMerchant(it) }
            ?.takeIf { it.isNotBlank() }
            ?: "UPI payment"
        return ParsedTransaction(amount, merchant, isExpense, channel = "UPI")
    }

    private fun firstAmount(text: String): Double? {
        val raw = amountRegex.find(text)?.groupValues?.get(1) ?: return null
        val value = raw.replace(",", "").trimEnd('.').toDoubleOrNull() ?: return null
        return if (value > 0) value else null
    }

    /** true = debit, false = credit, null = no clear verb (balance alert etc). */
    private fun detectIsExpense(low: String): Boolean? {
        // "credit card"/"debit card" are nouns, not directions.
        val t = low.replace("credit card", "card").replace("debit card", "card")
        val debitAt = debitWords.mapNotNull { w -> t.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = creditWords.mapNotNull { w -> t.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        return when {
            debitAt == null && creditAt == null -> null
            creditAt == null -> true
            debitAt == null -> false
            // "debited from your a/c ... credited to beneficiary": first verb wins.
            else -> debitAt <= creditAt
        }
    }

    private fun detectChannel(low: String): String = when {
        "atm" in low -> "ATM"
        "upi" in low || "vpa" in low || "@" in low -> "UPI"
        "card" in low || "pos" in low -> "Card"
        "neft" in low || "imps" in low || "rtgs" in low -> "IMPS/NEFT"
        "wallet" in low -> "Wallet"
        listOf("mandate", "autopay", "auto debit", "nach", "ecs").any { it in low } -> "AutoDebit"
        else -> "Bank"
    }

    private fun extractMerchant(text: String, channel: String): String {
        if (channel == "ATM") return "ATM Withdrawal"
        vpaRegex.find(text)?.groupValues?.get(1)?.let { vpa ->
            val handle = vpa.substringBefore("@")
            // A digits-only handle (a phone number or a short id) is still a distinct
            // payee: keep the whole UPI id so it can be categorised and given a name.
            return if (handle.all { it.isDigit() }) vpa.lowercase() else handle
        }
        (upiPathRegex.find(text) ?: upiNarrationRegex.find(text))?.let { m ->
            val name = m.groupValues[1].trim().trimEnd('.', ' ')
            if (name.isNotEmpty()) return name.take(40)
        }
        // ICICI style: "...; RAVI KUMAR credited. UPI:123"
        Regex(""";\s*([A-Za-z][A-Za-z .]{1,40}?)\s+credited""").find(text)
            ?.let { return trimMerchant(it.groupValues[1]) }
        // Lookahead so candidates may overlap: in "for Rs 750 at ZOMATO" the rejected
        // "Rs 750 ..." candidate must not swallow the later "at ZOMATO".
        // "at" in any case: HDFC card alerts write "At SRI GANESH STORES".
        Regex("""(?=\b(?:[Aa][Tt]|[Tt]o|[Ff]rom|[Tt]owards|[Ff]or)\s+([A-Z0-9][A-Za-z0-9 &._/-]{1,40}))""")
            .findAll(text)
            .map { trimMerchant(it.groupValues[1]) }
            .firstOrNull { it.isNotBlank() && !notAMerchant.containsMatchIn(it) && !isPlaceholder(it) }
            ?.let { return it }
        return when (channel) {
            "UPI" -> "UPI payment"
            "Card" -> "Card payment"
            "IMPS/NEFT" -> "Bank transfer"
            else -> "Bank transaction"
        }
    }

    /** Banks write these when they don't know the payee ("At NA"). */
    private val placeholders = setOf("na", "n a", "n/a", "n.a", "nil", "null", "none", "unknown",
        "not available", "not applicable", "-", "--", "x", "xx")

    private fun isPlaceholder(name: String): Boolean =
        name.lowercase().trim().trimEnd('.').replace(Regex("""\s+"""), " ") in placeholders

    private fun trimMerchant(name: String): String {
        val cut = name.split(
            Regex("""\s+(?:on|ref|info|dated|avl|via|upi|a/?c|no\.?|txn|for|by|dt|using|successfully|is|has|at|to|from)\b""",
                RegexOption.IGNORE_CASE),
            limit = 2,
        )[0]
        return cut.trim().trimEnd('.', ':', '-', ' ').take(40)
    }
}
