package com.androplus.pwdmgr.utils

/**
 * Picks a category for an auto-captured transaction from India-specific
 * merchant keywords.
 *
 * Each rule lists category names in order of preference; the first one that
 * exists in the user's category list wins. That way a user who has created a
 * "Groceries" category gets it, and everyone else falls back to a default such
 * as "Food & Dining". Returns null when nothing matches.
 */
object TransactionCategorizer {

    private class Rule(val keywords: List<String>, val categories: List<String>) {
        val patterns = keywords.map { Regex("""\b${Regex.escape(it)}\b""") }
        fun matches(text: String) = patterns.any { it.containsMatchIn(text) }
    }

    // Specific money movements first, so e.g. "LIC premium via Amazon Pay" is Insurance.
    // Paying a credit-card bill or moving money to your own account is not spending:
    // those land in categories the totals leave out (see ExpenseGrouping.NOT_COUNTED).
    private val expenseRules = listOf(
        Rule(listOf("tata payments", "creditcard payment", "credit card payment", "cc payment",
            "cred club", "card bill"), listOf("Card Bill")),
        Rule(listOf("selfft", "self transfer", "own account", "to self", "self"), listOf("Transfers")),
        Rule(listOf("zerodha", "groww", "upstox", "iccl", "indian clearing", "mutual fund",
            "sip", "kuvera", "paytm money", "smallcase", "recurring deposit", "si to- rd"),
            listOf("Investments", "Insurance & Savings", "Savings")),
        Rule(listOf("apy", "atal pension", "nps"), listOf("Insurance & Savings", "Insurance")),
        Rule(listOf("emi", "loan", "bajaj finance", "nach"), listOf("EMI", "Loans", "Loan")),
        Rule(listOf("lic", "insurance", "policybazaar", "hdfc life", "star health", "acko"),
            listOf("Insurance", "Insurance & Savings", "Healthcare")),
        Rule(listOf("atm withdrawal", "atm"), listOf("Cash", "ATM")),
        Rule(listOf("wines", "wine shop", "tasmac", "bar", "brandy", "liquor", "beer"),
            listOf("Liquor & Bars", "Entertainment")),
        Rule(listOf("bigbasket", "blinkit", "zepto", "dmart", "jiomart", "instamart",
            "reliance fresh", "more retail", "spencer", "supermarket", "grocery", "kirana",
            "agency", "agencies", "store", "stores", "traders", "provision", "provisions",
            "fruit", "fruits", "vegetable", "vegetables", "chicken", "fish", "meat", "egg", "eggs",
            "milk", "dairy", "mart"),
            listOf("Groceries", "Food & Dining")),
        Rule(listOf("swiggy", "zomato", "hungerbox", "eatsure", "dominos", "domino", "mcdonald",
            "mcdonalds", "kfc", "pizza", "cafe", "restaurant", "bakery", "bakes", "starbucks",
            "burger king", "haldiram", "saravana bhavan", "a2b", "hotel", "mess", "biryani",
            "briyani", "sweets", "tea", "teastall", "coffee", "juice", "cooldrinks", "snacks",
            "tiffin", "canteen", "food", "foods", "soup"), listOf("Food & Dining", "Food")),
        Rule(listOf("petrol", "fuel", "hp pay", "hppay", "indian oil", "iocl", "ioc", "bpcl",
            "hpcl", "shell", "nayara", "filling"), listOf("Fuel", "Transportation")),
        Rule(listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fastag", "parking",
            "makemytrip", "goibibo", "indigo", "air india", "quick ride", "blablacar", "travels",
            "toll", "tyres", "cycles"), listOf("Transportation", "Travel")),
        Rule(listOf("electricity", "tneb", "tangedco", "bescom", "water bill", "broadband",
            "airtel", "jio", "vodafone", "bsnl", "act fibernet", "recharge", "dth", "tata play",
            "indane", "hp gas", "bharat gas", "gas booking", "google india", "google play"),
            listOf("Bills & Utilities", "Utilities", "Bills", "Housing")),
        Rule(listOf("rent", "maintenance", "nobroker", "society"), listOf("Housing", "Rent")),
        Rule(listOf("apollo", "pharmacy", "medplus", "hospital", "clinic", "1mg", "pharmeasy",
            "netmeds", "diagnostics", "doctor", "dental", "medical", "medicals", "lab", "labs"),
            listOf("Healthcare", "Health")),
        Rule(listOf("netflix", "hotstar", "prime video", "spotify", "bookmyshow", "pvr", "inox",
            "youtube", "jiocinema", "zee5", "sonyliv", "talkies", "cinema", "theatre", "theater"),
            listOf("Entertainment", "Subscriptions")),
        Rule(listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon",
            "croma", "reliance digital", "ikea", "tata cliq", "lenskart"), listOf("Shopping")),
        Rule(listOf("school", "college", "tuition", "fees", "udemy", "coursera", "vedantu",
            "byjus", "education"), listOf("Education")),
    )

    private val incomeRules = listOf(
        Rule(listOf("selfft", "self transfer", "own account", "self"), listOf("Transfers", "Income")),
        Rule(listOf("salary", "sal cr", "payroll"), listOf("Salary", "Income")),
        Rule(listOf("interest", "int pd", "int.pd", "dividend"), listOf("Interest", "Income")),
        Rule(listOf("refund", "reversal", "cashback"), listOf("Refunds", "Income")),
    )

    fun categorize(
        merchant: String,
        text: String,
        isExpense: Boolean,
        available: List<String>,
    ): String? {
        val haystack = "$merchant $text".lowercase()
        val byLower = available.associateBy { it.lowercase() }
        fun firstAvailable(names: List<String>) = names.firstNotNullOfOrNull { byLower[it.lowercase()] }

        val rules = if (isExpense) expenseRules else incomeRules
        for (rule in rules) {
            if (rule.matches(haystack)) firstAvailable(rule.categories)?.let { return it }
        }
        return if (isExpense) null else firstAvailable(listOf("Income"))
    }

    /**
     * [categorize], falling back to "Other" (never to an arbitrary category such
     * as Shopping, which made everything unrecognised look like shopping).
     */
    fun categorizeOrOther(merchant: String, text: String, isExpense: Boolean, available: List<String>): String? =
        categorize(merchant, text, isExpense, available)
            ?: available.firstOrNull { it.equals("Other", ignoreCase = true) }
            ?: available.firstOrNull { it.contains("other", ignoreCase = true) }
            ?: available.firstOrNull()
}

/** Re-reads an auto-captured entry's original message with the current rules. */
object AutoReclassifier {

    data class Result(val merchant: String, val isExpense: Boolean, val category: String?)

    /** [rawText] is what was saved as the entry's description: the SMS, or "title\ntext". */
    fun reclassify(source: String, rawText: String, categoryNames: List<String>): Result? {
        fun notification(): ParsedTransaction? {
            val parts = rawText.split("\n", limit = 2)
            return TransactionParser.parseNotification(parts[0], parts.getOrElse(1) { "" })
        }
        val parsed = when (source) {
            "SMS" -> TransactionParser.parseSms(rawText)
            "GPay", "PhonePe", "Paytm" -> notification()
            else -> TransactionParser.parseSms(rawText) ?: notification()
        } ?: return null
        return Result(
            parsed.merchant,
            parsed.isExpense,
            TransactionCategorizer.categorizeOrOther(parsed.merchant, rawText, parsed.isExpense, categoryNames),
        )
    }
}
