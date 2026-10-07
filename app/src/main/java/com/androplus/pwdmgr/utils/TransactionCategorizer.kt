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
    private val expenseRules = listOf(
        Rule(listOf("selfft", "self transfer", "own account", "to self"), listOf("Transfers")),
        Rule(listOf("zerodha", "groww", "upstox", "iccl", "indian clearing", "mutual fund",
            "sip", "kuvera", "paytm money", "smallcase"), listOf("Investments", "Savings")),
        Rule(listOf("emi", "loan", "bajaj finance", "nach"), listOf("EMI", "Loans", "Loan")),
        Rule(listOf("lic", "insurance", "policybazaar", "hdfc life", "star health", "acko"),
            listOf("Insurance", "Healthcare")),
        Rule(listOf("atm withdrawal", "atm"), listOf("Cash", "ATM")),
        Rule(listOf("bigbasket", "blinkit", "zepto", "dmart", "jiomart", "instamart",
            "reliance fresh", "more retail", "spencer", "supermarket", "grocery", "kirana"),
            listOf("Groceries", "Food & Dining")),
        Rule(listOf("swiggy", "zomato", "hungerbox", "eatsure", "dominos", "mcdonald",
            "mcdonalds", "kfc", "pizza", "cafe", "restaurant", "bakery", "starbucks",
            "burger king", "haldiram", "saravana bhavan", "a2b"), listOf("Food & Dining", "Food")),
        Rule(listOf("petrol", "fuel", "hp pay", "hppay", "indian oil", "iocl", "bpcl", "hpcl",
            "shell", "nayara"), listOf("Fuel", "Transportation")),
        Rule(listOf("uber", "ola", "rapido", "irctc", "redbus", "metro", "fastag", "parking",
            "makemytrip", "goibibo", "indigo", "air india"), listOf("Transportation", "Travel")),
        Rule(listOf("electricity", "tneb", "tangedco", "bescom", "water bill", "broadband",
            "airtel", "jio", "vodafone", "bsnl", "act fibernet", "recharge", "dth", "tata play",
            "indane", "hp gas", "bharat gas", "gas booking"),
            listOf("Bills & Utilities", "Utilities", "Bills", "Housing")),
        Rule(listOf("rent", "maintenance", "nobroker", "society"), listOf("Housing", "Rent")),
        Rule(listOf("apollo", "pharmacy", "medplus", "hospital", "clinic", "1mg", "pharmeasy",
            "netmeds", "diagnostics", "doctor", "dental"), listOf("Healthcare", "Health")),
        Rule(listOf("netflix", "hotstar", "prime video", "spotify", "bookmyshow", "pvr", "inox",
            "youtube", "jiocinema", "zee5", "sonyliv"), listOf("Entertainment", "Subscriptions")),
        Rule(listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon",
            "croma", "reliance digital", "ikea", "tata cliq", "lenskart"), listOf("Shopping")),
        Rule(listOf("school", "college", "tuition", "fees", "udemy", "coursera"),
            listOf("Education")),
    )

    private val incomeRules = listOf(
        Rule(listOf("selfft", "self transfer", "own account"), listOf("Transfers", "Income")),
        Rule(listOf("salary", "sal cr", "payroll"), listOf("Salary", "Income")),
        Rule(listOf("interest", "int pd", "dividend"), listOf("Interest", "Income")),
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
}
