package de.reisekosten.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class UiState(
    val configured: Boolean,
    val data: TripData?,
    val pending: List<Expense>,
    val pendingExchanges: List<Exchange> = emptyList(),
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
) {
    /** Ausstehende + gespeicherte Ausgaben, neueste zuerst. */
    val allExpenses: List<Expense>
        get() = (pending + (data?.expenses ?: emptyList())).sortedByDescending { it.date }

    /** Ausstehende + gespeicherte Geldwechsel, neueste zuerst. */
    val allExchanges: List<Exchange>
        get() = (pendingExchanges + (data?.exchanges ?: emptyList())).sortedByDescending { it.date }

    /** Kurs für eine Ausgabe: bar → Ø-Kurs der Wechsel (falls vorhanden), sonst Kurs aus den Einstellungen. */
    fun rateFor(currency: String, payment: String): Double {
        val s = data?.settings ?: return 1.0
        if (currency == s.baseCurrency) return 1.0
        val cash = if (payment == PAY_CASH) cashRates(allExchanges)[currency] else null
        return cash ?: s.rates[currency] ?: 1.0
    }

    /** Bisher verwendete Länder, häufigste zuerst. */
    val countries: List<String>
        get() = allExpenses.map { it.country }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    /** Bargeldstand je Fremdwährung, für die es Wechsel gibt. */
    val cash: List<CashInfo>
        get() {
            val expenses = allExpenses
            return allExchanges.groupBy { it.currency }.map { (cur, xs) ->
                CashInfo(
                    currency = cur,
                    received = xs.sumOf { it.received },
                    paid = xs.sumOf { it.paid },
                    spent = expenses.filter { it.isCash && it.currency == cur }.sumOf { it.amount },
                )
            }.sortedBy { it.currency }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        UiState(
            configured = store.url.isNotBlank() && store.token.isNotBlank(),
            data = store.cachedData(),
            pending = store.pending,
            pendingExchanges = store.pendingExchanges,
        )
    )
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val url: String get() = store.url
    val token: String get() = store.token
    val lastPaidBy: String get() = store.lastPaidBy
    val lastPayment: String get() = store.lastPayment
    val lastCountry: String get() = store.lastCountry

    fun defaultCurrency(data: TripData): String =
        store.lastCurrency.takeIf { it in data.settings.rates } ?: data.settings.baseCurrency

    init {
        if (_state.value.configured) refresh()
    }

    fun refresh() {
        viewModelScope.launch { sync() }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun saveConnection(url: String, token: String) {
        store.url = url.trim()
        store.token = token.trim()
        _state.update { it.copy(configured = true, error = null) }
        refresh()
    }

    /** Ausgabe sofort lokal merken, dann versuchen zu senden – funktioniert auch ohne Netz. */
    fun addExpense(d: Draft) {
        val rate = _state.value.rateFor(d.currency, d.payment)
        val e = Expense(
            id = UUID.randomUUID().toString(),
            date = d.date, category = d.category, description = d.description,
            amount = d.amount, currency = d.currency, rate = rate,
            baseAmount = Math.round(d.amount * rate * 100) / 100.0,
            paidBy = d.paidBy, payment = d.payment, country = d.country, pending = true,
        )
        store.pending = store.pending + e
        store.lastCurrency = d.currency
        store.lastPaidBy = d.paidBy
        store.lastPayment = d.payment
        store.lastCountry = d.country
        _state.update { it.copy(pending = store.pending) }
        refresh()
    }

    fun addExchange(d: ExchangeDraft) {
        val x = Exchange(
            id = UUID.randomUUID().toString(),
            date = d.date, currency = d.currency, received = d.received, paid = d.paid,
            note = d.note, pending = true,
        )
        store.pendingExchanges = store.pendingExchanges + x
        store.lastCurrency = d.currency
        _state.update { it.copy(pendingExchanges = store.pendingExchanges) }
        refresh()
    }

    fun deleteExpense(e: Expense) {
        if (e.pending) {
            store.pending = store.pending.filterNot { it.id == e.id }
            _state.update { it.copy(pending = store.pending) }
            return
        }
        deleteRemote { it.deleteExpense(e.id) }
    }

    fun deleteExchange(x: Exchange) {
        if (x.pending) {
            store.pendingExchanges = store.pendingExchanges.filterNot { it.id == x.id }
            _state.update { it.copy(pendingExchanges = store.pendingExchanges) }
            return
        }
        deleteRemote { it.deleteExchange(x.id) }
    }

    private fun deleteRemote(call: (Api) -> JSONObject) {
        viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(loading = true) }
                try {
                    val json = withContext(Dispatchers.IO) { call(api()) }
                    applyData(json)
                } catch (ex: ApiException) {
                    _state.update { it.copy(loading = false) }
                    _messages.emit(ex.message ?: "Fehler beim Löschen")
                } catch (_: IOException) {
                    _state.update { it.copy(loading = false, offline = true) }
                    _messages.emit("Löschen geht nur mit Internetverbindung.")
                }
            }
        }
    }

    private fun api() = Api(store.url, store.token)

    private fun applyData(json: JSONObject) {
        store.cachedJson = json.toString()
        _state.update {
            it.copy(
                data = TripData.fromJson(json), pending = store.pending,
                pendingExchanges = store.pendingExchanges,
                loading = false, offline = false, error = null,
            )
        }
    }

    private suspend fun sync() = mutex.withLock {
        if (store.url.isBlank()) return@withLock
        _state.update { it.copy(loading = true) }
        try {
            val api = api()
            var latest: JSONObject? = null
            // Wechsel zuerst, damit Bar-Ausgaben auf dem Server schon den richtigen Kurs bekommen
            for (x in store.pendingExchanges) {
                try {
                    latest = withContext(Dispatchers.IO) { api.addExchange(x) }
                } catch (ex: ApiException) {
                    if (ex.blocking) throw ex
                    _messages.emit("Geldwechsel abgelehnt: ${ex.message}")
                }
                store.pendingExchanges = store.pendingExchanges.filterNot { it.id == x.id }
            }
            for (e in store.pending) {
                try {
                    latest = withContext(Dispatchers.IO) { api.addExpense(e) }
                } catch (ex: ApiException) {
                    if (ex.blocking) throw ex
                    val name = e.description.ifBlank { e.category }
                    _messages.emit("\"$name\" abgelehnt: ${ex.message}")
                }
                store.pending = store.pending.filterNot { it.id == e.id }
            }
            applyData(latest ?: withContext(Dispatchers.IO) { api.getData() })
        } catch (ex: ApiException) {
            _state.update {
                it.copy(loading = false, pending = store.pending, pendingExchanges = store.pendingExchanges, error = ex.message)
            }
        } catch (_: IOException) {
            _state.update {
                it.copy(loading = false, pending = store.pending, pendingExchanges = store.pendingExchanges, offline = true)
            }
        }
    }
}
