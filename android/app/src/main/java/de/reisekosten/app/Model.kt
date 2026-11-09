package de.reisekosten.app

import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val tripName: String,
    val budget: Double,
    val baseCurrency: String,
    val rates: Map<String, Double>,
)

data class Expense(
    val id: String,
    val date: String, // yyyy-MM-dd
    val category: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val rate: Double,
    val baseAmount: Double,
    val paidBy: String,
    val pending: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("category", category)
        .put("description", description).put("amount", amount)
        .put("currency", currency).put("rate", rate)
        .put("baseAmount", baseAmount).put("paidBy", paidBy)

    companion object {
        fun fromJson(o: JSONObject, pending: Boolean = false) = Expense(
            id = o.getString("id"),
            date = o.optString("date"),
            category = o.optString("category"),
            description = o.optString("description"),
            amount = o.optDouble("amount", 0.0),
            currency = o.optString("currency"),
            rate = o.optDouble("rate", 1.0),
            baseAmount = o.optDouble("baseAmount", 0.0),
            paidBy = o.optString("paidBy"),
            pending = pending,
        )
    }
}

data class TripData(
    val settings: Settings,
    val categories: List<String>,
    val expenses: List<Expense>,
) {
    companion object {
        fun fromJson(o: JSONObject): TripData {
            val s = o.getJSONObject("settings")
            val ratesJson = s.optJSONObject("rates") ?: JSONObject()
            val rates = ratesJson.keys().asSequence().associateWith { ratesJson.getDouble(it) }
            val cats = o.optJSONArray("categories") ?: JSONArray()
            val exps = o.optJSONArray("expenses") ?: JSONArray()
            return TripData(
                settings = Settings(
                    tripName = s.optString("tripName", "Meine Reise"),
                    budget = s.optDouble("budget", 0.0),
                    baseCurrency = s.optString("baseCurrency", "EUR"),
                    rates = rates,
                ),
                categories = List(cats.length()) { cats.getString(it) },
                expenses = List(exps.length()) { Expense.fromJson(exps.getJSONObject(it)) },
            )
        }
    }
}

data class Draft(
    val date: String,
    val category: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val paidBy: String,
)
