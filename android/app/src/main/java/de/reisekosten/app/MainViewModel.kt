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
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
) {
    /** Ausstehende + gespeicherte Ausgaben, neueste zuerst. */
    val allExpenses: List<Expense>
        get() = (pending + (data?.expenses ?: emptyList())).sortedByDescending { it.date }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        UiState(
            configured = store.url.isNotBlank() && store.token.isNotBlank(),
            data = store.cachedData(),
            pending = store.pending,
        )
    )
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val url: String get() = store.url
    val token: String get() = store.token
    val lastPaidBy: String get() = store.lastPaidBy

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
        val rate = _state.value.data?.settings?.rates?.get(d.currency) ?: 1.0
        val e = Expense(
            id = UUID.randomUUID().toString(),
            date = d.date, category = d.category, description = d.description,
            amount = d.amount, currency = d.currency, rate = rate,
            baseAmount = Math.round(d.amount * rate * 100) / 100.0,
            paidBy = d.paidBy, pending = true,
        )
        store.pending = store.pending + e
        store.lastCurrency = d.currency
        store.lastPaidBy = d.paidBy
        _state.update { it.copy(pending = store.pending) }
        refresh()
    }

    fun deleteExpense(e: Expense) {
        if (e.pending) {
            store.pending = store.pending.filterNot { it.id == e.id }
            _state.update { it.copy(pending = store.pending) }
            return
        }
        viewModelScope.launch {
            mutex.withLock {
                _state.update { it.copy(loading = true) }
                try {
                    val json = withContext(Dispatchers.IO) { api().deleteExpense(e.id) }
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
            _state.update { it.copy(loading = false, pending = store.pending, error = ex.message) }
        } catch (_: IOException) {
            _state.update { it.copy(loading = false, pending = store.pending, offline = true) }
        }
    }
}
