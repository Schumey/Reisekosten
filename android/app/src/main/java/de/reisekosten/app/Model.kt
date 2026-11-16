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
    val payment: String = PAY_CARD, // PAY_CARD oder PAY_CASH
    val country: String = "",
    val pending: Boolean = false,
) {
    val isCash: Boolean get() = payment == PAY_CASH

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("category", category)
        .put("description", description).put("amount", amount)
        .put("currency", currency).put("rate", rate)
        .put("baseAmount", baseAmount).put("paidBy", paidBy)
        .put("payment", payment).put("country", country)

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
            payment = if (o.optString("payment") == PAY_CASH) PAY_CASH else PAY_CARD,
            country = o.optString("country"),
            pending = pending,
        )
    }
}

const val PAY_CARD = "Karte"
const val PAY_CASH = "Bar"

/** Geldwechsel oder Abhebung am Automaten: [received] in [currency] hat [paid] in Hauptwährung gekostet (inkl. Gebühren). */
data class Exchange(
    val id: String,
    val date: String,
    val currency: String,
    val received: Double,
    val paid: Double,
    val note: String,
    val pending: Boolean = false,
) {
    val rate: Double get() = if (received > 0) paid / received else 0.0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("currency", currency)
        .put("received", received).put("paid", paid).put("note", note)

    companion object {
        fun fromJson(o: JSONObject, pending: Boolean = false) = Exchange(
            id = o.getString("id"),
            date = o.optString("date"),
            currency = o.optString("currency"),
            received = o.optDouble("received", 0.0),
            paid = o.optDouble("paid", 0.0),
            note = o.optString("note"),
            pending = pending,
        )
    }
}

/** Bargeldstand einer Währung. */
data class CashInfo(val currency: String, val received: Double, val paid: Double, val spent: Double) {
    val rate: Double get() = if (received > 0) paid / received else 0.0
    val left: Double get() = received - spent
}

/** Durchschnittskurs je Währung aus allen Wechseln (Summe bezahlt / Summe erhalten). */
fun cashRates(exchanges: List<Exchange>): Map<String, Double> =
    exchanges.groupBy { it.currency }
        .mapValues { (_, l) -> l.sumOf { it.received }.let { r -> if (r > 0) l.sumOf { it.paid } / r else 0.0 } }
        .filterValues { it > 0 }

data class TripData(
    val settings: Settings,
    val categories: List<String>,
    val expenses: List<Expense>,
    val exchanges: List<Exchange> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): TripData {
            val s = o.getJSONObject("settings")
            val ratesJson = s.optJSONObject("rates") ?: JSONObject()
            val rates = ratesJson.keys().asSequence().associateWith { ratesJson.getDouble(it) }
            val cats = o.optJSONArray("categories") ?: JSONArray()
            val exps = o.optJSONArray("expenses") ?: JSONArray()
            val xs = o.optJSONArray("exchanges") ?: JSONArray()
            return TripData(
                settings = Settings(
                    tripName = s.optString("tripName", "Meine Reise"),
                    budget = s.optDouble("budget", 0.0),
                    baseCurrency = s.optString("baseCurrency", "EUR"),
                    rates = rates,
                ),
                categories = List(cats.length()) { cats.getString(it) },
                expenses = List(exps.length()) { Expense.fromJson(exps.getJSONObject(it)) },
                exchanges = List(xs.length()) { Exchange.fromJson(xs.getJSONObject(it)) },
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
    val payment: String,
    val country: String,
)

data class ExchangeDraft(
    val date: String,
    val currency: String,
    val received: Double,
    val paid: Double,
    val note: String,
)
