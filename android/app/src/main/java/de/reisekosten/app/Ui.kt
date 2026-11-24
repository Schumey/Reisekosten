@file:OptIn(ExperimentalMaterial3Api::class)

package de.reisekosten.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

// ------------------------------------------------------------------ Hilfen

private val German = Locale.GERMANY
private val DayFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", German)
private val ShortDate = DateTimeFormatter.ofPattern("dd.MM.yyyy", German)

fun money(v: Double, cur: String): String = try {
    NumberFormat.getCurrencyInstance(German).apply { currency = Currency.getInstance(cur) }.format(v)
} catch (_: Exception) {
    String.format(German, "%,.2f %s", v, cur)
}

private fun dayLabel(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    when (d) {
        LocalDate.now() -> "Heute"
        LocalDate.now().minusDays(1) -> "Gestern"
        else -> d.format(DayFormat)
    }
}.getOrDefault(iso)

/** Betrag fürs Eingabefeld, z. B. 12.5 → "12,5". */
private fun amountInput(v: Double): String =
    v.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')

/** Nur Ziffern, Komma, Punkt und ein Minus ganz vorne. */
private fun signedAmount(v: String): String =
    (if (v.startsWith("-")) "-" else "") + v.filter { it.isDigit() || it == ',' || it == '.' }

@Composable
private fun creditColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

private fun rateText(rate: Double, cur: String, base: String): String =
    String.format(German, "1 %s = %.4f %s", cur, rate, base)

private const val NO_COUNTRY = "Ohne Land"

private fun categoryIcon(c: String): ImageVector = when (c) {
    "Transport", "Transport Kurzstrecke" -> Icons.Filled.DirectionsCar
    "Transport Langstrecke" -> Icons.Filled.Flight
    "Kaution" -> Icons.Filled.Key
    "Unterkunft" -> Icons.Filled.Hotel
    "Essen & Trinken" -> Icons.Filled.Restaurant
    "Aktivitäten" -> Icons.Filled.LocalActivity
    "Einkäufe" -> Icons.Filled.ShoppingBag
    else -> Icons.Filled.Receipt
}

// ------------------------------------------------------------------ Einstieg

@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showConnection by rememberSaveable { mutableStateOf(false) }

    if (!state.configured || showConnection) {
        ConnectionScreen(
            initialUrl = vm.url,
            initialToken = vm.token,
            canCancel = state.configured,
            onCancel = { showConnection = false },
            onSave = { u, t -> vm.saveConnection(u, t); showConnection = false },
        )
    } else {
        MainScreen(state, vm, onOpenConnection = { showConnection = true })
    }
}

// ------------------------------------------------------------------ Hauptansicht

@Composable
private fun MainScreen(state: UiState, vm: MainViewModel, onOpenConnection: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var countryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }
    var toEdit by remember { mutableStateOf<Expense?>(null) }
    var exchangeToDelete by remember { mutableStateOf<Exchange?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    val data = state.data
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        data?.settings?.tripName ?: "Reisekosten",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                    IconButton(onClick = onOpenConnection) { Icon(Icons.Filled.Settings, "Verbindung") }
                },
            )
        },
        floatingActionButton = {
            if (data != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Ausgabe") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (data == null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (state.loading) CircularProgressIndicator()
                    else Text(
                        "Noch keine Daten. Prüfe die Verbindung und zieh zum Aktualisieren nach unten.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                ExpenseList(
                    state, data, filter, onFilter = { filter = it },
                    countryFilter = countryFilter, onCountryFilter = { countryFilter = it; filter = null },
                    onEdit = { toEdit = it }, onDelete = { toDelete = it }, onDeleteExchange = { exchangeToDelete = it },
                )
            }
        }
    }

    if ((showAdd || toEdit != null) && data != null) {
        val editing = toEdit
        AddSheet(
            editing = editing,
            state = state,
            data = data,
            defaultCurrency = vm.defaultCurrency(data),
            defaultPaidBy = vm.lastPaidBy,
            defaultPayment = vm.lastPayment,
            defaultCountry = vm.lastCountry,
            onDismiss = { showAdd = false; toEdit = null },
            onSave = {
                if (editing != null) vm.updateExpense(editing, it) else vm.addExpense(it)
                showAdd = false; toEdit = null
            },
            onSaveExchange = { vm.addExchange(it); showAdd = false },
        )
    }

    exchangeToDelete?.let { x ->
        AlertDialog(
            onDismissRequest = { exchangeToDelete = null },
            title = { Text("Geldwechsel löschen?") },
            text = {
                Text("${money(x.received, x.currency)} für ${money(x.paid, data?.settings?.baseCurrency ?: "EUR")}")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteExchange(x); exchangeToDelete = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { exchangeToDelete = null }) { Text("Abbrechen") } },
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Ausgabe löschen?") },
            text = { Text("${e.description.ifBlank { e.category }} – ${money(e.amount, e.currency)}") },
            confirmButton = {
                TextButton(onClick = { vm.deleteExpense(e); toDelete = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ExpenseList(
    state: UiState,
    data: TripData,
    filter: String?,
    onFilter: (String?) -> Unit,
    countryFilter: String?,
    onCountryFilter: (String?) -> Unit,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    onDeleteExchange: (Exchange) -> Unit,
) {
    val base = data.settings.baseCurrency
    val all = state.allExpenses
    val scoped = all.filter { countryFilter == null || it.country.ifBlank { NO_COUNTRY } == countryFilter }
    val shown = scoped.filter { filter == null || it.category == filter }
    val byDay = shown.groupBy { it.date }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "summary") { SummaryCard(data.settings, all.sumOf { it.baseAmount }) }

        val pendingCount = state.pending.size + state.pendingEdits.size + state.pendingExchanges.size
        if (state.offline || pendingCount > 0) {
            item(key = "status") { StatusBanner(state.offline, pendingCount) }
        }

        if (all.isNotEmpty()) {
            item(key = "cats") {
                CategoryCard(scoped, base, title = countryFilter?.let { "Nach Kategorie · $it" } ?: "Nach Kategorie")
            }
        }

        if (all.any { it.country.isNotBlank() }) {
            item(key = "countries") { CountryCard(all, base, countryFilter, onCountryFilter) }
        }

        if (state.allExchanges.isNotEmpty()) {
            item(key = "cash") { CashCard(state.cash, state.allExchanges, base, onDeleteExchange) }
        }

        item(key = "filter") {
            val cats = (data.categories + all.map { it.category }).distinct()
                .filter { c -> countryFilter == null || scoped.any { it.category == c } }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("Alle") }) }
                items(cats) { c ->
                    FilterChip(
                        selected = filter == c,
                        onClick = { onFilter(if (filter == c) null else c) },
                        label = { Text(c) },
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Noch keine Ausgaben. Tippe auf „Ausgabe“, um die erste zu erfassen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        }

        byDay.forEach { (day, list) ->
            item(key = "day-$day") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(dayLabel(day), style = MaterialTheme.typography.titleSmall)
                    Text(
                        money(list.sumOf { it.baseAmount }, base),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { e -> ExpenseRow(e, base, onEdit, onDelete) }
        }
    }
}

@Composable
private fun SummaryCard(s: Settings, spent: Double) {
    val left = s.budget - spent
    val over = s.budget > 0 && left < 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Ausgegeben", style = MaterialTheme.typography.labelLarge)
            Text(
                money(spent, s.baseCurrency),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (s.budget > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (spent / s.budget).toFloat().coerceIn(0f, 1f) },
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Budget ${money(s.budget, s.baseCurrency)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (over) "${money(-left, s.baseCurrency)} drüber" else "${money(left, s.baseCurrency)} übrig",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(offline: Boolean, pending: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (offline) Icons.Filled.CloudOff else Icons.Filled.Sync, null)
            Spacer(Modifier.width(12.dp))
            Text(
                buildString {
                    if (offline) append("Offline. ")
                    if (pending > 0) append(
                        if (pending == 1) "1 Eintrag wird gesendet, sobald du online bist."
                        else "$pending Einträge werden gesendet, sobald du online bist."
                    )
                    else append("Angezeigt wird der letzte bekannte Stand.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CategoryCard(all: List<Expense>, base: String, title: String) {
    val sums = all.groupBy { it.category }
        .mapValues { (_, v) -> v.sumOf { it.baseAmount } }
        .entries.sortedByDescending { it.value }
    val max = sums.maxOf { it.value }.coerceAtLeast(0.01)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            sums.forEach { (cat, sum) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(categoryIcon(cat), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(money(sum, base), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (sum / max).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryCard(all: List<Expense>, base: String, selected: String?, onSelect: (String?) -> Unit) {
    val sums = all.groupBy { it.country.ifBlank { NO_COUNTRY } }
        .mapValues { (_, v) -> v.sumOf { it.baseAmount } }
        .entries.sortedByDescending { it.value }
    val max = sums.maxOf { it.value }.coerceAtLeast(0.01)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nach Land (antippen zum Filtern)", style = MaterialTheme.typography.titleSmall)
            sums.forEach { (land, sum) ->
                val isSel = selected == land
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(if (isSel) null else land) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Public, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            land, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(money(sum, base), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (sum / max).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}

@Composable
private fun CashCard(cash: List<CashInfo>, exchanges: List<Exchange>, base: String, onDelete: (Exchange) -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bargeld", style = MaterialTheme.typography.titleSmall)
            cash.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Payments, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${money(c.left, c.currency)} übrig", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Ø ${rateText(c.rate, c.currency, base)} · ${money(c.received, c.currency)} für ${money(c.paid, base)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showAll) "Wechsel ausblenden" else "Wechsel anzeigen (${exchanges.size})")
            }
            if (showAll) exchanges.forEach { x ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${money(x.received, x.currency)} für ${money(x.paid, base)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val meta = listOfNotNull(
                            runCatching { LocalDate.parse(x.date).format(ShortDate) }.getOrDefault(x.date),
                            x.note.takeIf { it.isNotBlank() },
                            "nicht gesendet".takeIf { x.pending },
                        ).joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(x) }) {
                        Icon(Icons.Filled.DeleteOutline, "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(e: Expense, base: String, onEdit: (Expense) -> Unit, onDelete: (Expense) -> Unit) {
    Card(
        onClick = { onEdit(e) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(e.category), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.description.ifBlank { e.category },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    e.category,
                    "Gutschrift".takeIf { e.amount < 0 },
                    e.country.takeIf { it.isNotBlank() },
                    "bar".takeIf { e.isCash },
                    e.paidBy.takeIf { it.isNotBlank() },
                    "nicht gesendet".takeIf { e.pending },
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(e.baseAmount, base),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (e.baseAmount < 0) creditColor() else Color.Unspecified,
                )
                if (e.currency != base) {
                    Text(
                        money(e.amount, e.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onDelete(e) }) {
                Icon(Icons.Filled.DeleteOutline, "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ Neue Ausgabe / Geldwechsel

@Composable
private fun AddSheet(
    editing: Expense?,
    state: UiState,
    data: TripData,
    defaultCurrency: String,
    defaultPaidBy: String,
    defaultPayment: String,
    defaultCountry: String,
    onDismiss: () -> Unit,
    onSave: (Draft) -> Unit,
    onSaveExchange: (ExchangeDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var exchangeMode by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing != null) {
                Text("Ausgabe bearbeiten", style = MaterialTheme.typography.titleLarge)
                ExpenseForm(state, data, editing, defaultCurrency, defaultPaidBy, defaultPayment, defaultCountry, onSave)
                return@Column
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !exchangeMode, onClick = { exchangeMode = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Ausgabe") }
                SegmentedButton(
                    selected = exchangeMode, onClick = { exchangeMode = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Bargeld geholt") }
            }
            if (exchangeMode) {
                ExchangeForm(data, defaultCurrency, onSaveExchange)
            } else {
                ExpenseForm(state, data, null, defaultCurrency, defaultPaidBy, defaultPayment, defaultCountry, onSave)
            }
        }
    }
}

@Composable
private fun ExpenseForm(
    state: UiState,
    data: TripData,
    editing: Expense?,
    defaultCurrency: String,
    defaultPaidBy: String,
    defaultPayment: String,
    defaultCountry: String,
    onSave: (Draft) -> Unit,
) {
    val base = data.settings.baseCurrency
    var date by remember {
        mutableStateOf(editing?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() } ?: LocalDate.now())
    }
    var category by rememberSaveable {
        mutableStateOf(editing?.category ?: data.categories.firstOrNull() ?: "Sonstiges")
    }
    var description by rememberSaveable { mutableStateOf(editing?.description ?: "") }
    var amountText by rememberSaveable { mutableStateOf(editing?.let { amountInput(it.amount) } ?: "") }
    var currency by rememberSaveable { mutableStateOf(editing?.currency ?: defaultCurrency) }
    var paidBy by rememberSaveable { mutableStateOf(editing?.paidBy ?: defaultPaidBy) }
    var payment by rememberSaveable { mutableStateOf(editing?.payment ?: defaultPayment) }
    var country by rememberSaveable { mutableStateOf(editing?.country ?: defaultCountry) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val valid = amount != null && amount != 0.0
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editing == null) amountFocus.requestFocus() }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { v -> amountText = signedAmount(v) },
            label = { Text("Betrag") },
            supportingText = { Text("± = Gutschrift") },
            trailingIcon = {
                // Nicht jede Zahlentastatur hat ein Minus – daher der Umschalter
                TextButton(onClick = {
                    amountText = if (amountText.startsWith("-")) amountText.drop(1) else "-$amountText"
                }) { Text("±", style = MaterialTheme.typography.titleMedium) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).focusRequester(amountFocus),
        )
        Dropdown(
            label = "Währung",
            value = currency,
            options = (data.settings.rates.keys + listOfNotNull(editing?.currency)).distinct().sorted(),
            onSelect = { currency = it },
            modifier = Modifier.width(130.dp),
        )
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = payment == PAY_CARD, onClick = { payment = PAY_CARD },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = { Icon(Icons.Filled.CreditCard, null, Modifier.size(18.dp)) },
        ) { Text("Karte / Konto") }
        SegmentedButton(
            selected = payment == PAY_CASH, onClick = { payment = PAY_CASH },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = { Icon(Icons.Filled.Payments, null, Modifier.size(18.dp)) },
        ) { Text("Bargeld") }
    }

    if (currency != base) {
        val keepRate = editing != null && editing.currency == currency && editing.payment == payment && editing.rate > 0
        val rate = if (editing != null) state.rateForEdit(editing, currency, payment) else state.rateFor(currency, payment)
        val fromCash = payment == PAY_CASH && cashRates(state.allExchanges)[currency] != null
        val source = when {
            keepRate -> "gespeicherter Kurs"
            fromCash -> "Ø-Kurs deiner Wechsel"
            payment == PAY_CASH -> "noch kein Wechsel erfasst, Kurs aus Einstellungen"
            else -> "Kurs aus Einstellungen"
        }
        Text(
            buildString {
                if (amount != null) append("≈ ${money(amount * rate, base)} · ")
                append("${rateText(rate, currency, base)} ($source)")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Dropdown(
        label = "Kategorie",
        value = category,
        options = (data.categories + category).distinct(),
        onSelect = { category = it },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Beschreibung") },
        placeholder = { Text("z. B. Taxi zum Hotel") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    DateField(date) { date = it }

    SuggestField(
        label = "Land (optional)",
        value = country,
        suggestions = state.countries,
        onChange = { country = it },
    )

    OutlinedTextField(
        value = paidBy,
        onValueChange = { paidBy = it },
        label = { Text("Bezahlt von (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = {
            onSave(
                Draft(
                    date = date.toString(),
                    category = category,
                    description = description.trim(),
                    amount = amount ?: 0.0,
                    currency = currency,
                    paidBy = paidBy.trim(),
                    payment = payment,
                    country = country.trim(),
                )
            )
        },
        enabled = valid,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (editing != null) "Änderung speichern" else "Speichern") }
}

@Composable
private fun ExchangeForm(data: TripData, defaultCurrency: String, onSave: (ExchangeDraft) -> Unit) {
    val base = data.settings.baseCurrency
    val foreign = data.settings.rates.keys.filter { it != base }.sorted()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var currency by rememberSaveable {
        mutableStateOf(defaultCurrency.takeIf { it != base } ?: foreign.firstOrNull() ?: "")
    }
    var receivedText by rememberSaveable { mutableStateOf("") }
    var paidText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val received = receivedText.replace(',', '.').toDoubleOrNull()
    val paid = paidText.replace(',', '.').toDoubleOrNull()
    val valid = currency.isNotBlank() && received != null && received > 0 && paid != null && paid > 0

    Text(
        "Geld gewechselt oder am Automaten abgehoben? Trag ein, was du bekommen hast und was es dich " +
            "insgesamt gekostet hat (inkl. Gebühren). Bar-Ausgaben werden dann mit diesem Kurs umgerechnet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (foreign.isEmpty()) {
        Text(
            "Lege zuerst im Blatt „Einstellungen“ eine Fremdwährung an.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = receivedText,
            onValueChange = { v -> receivedText = v.filter { it.isDigit() || it == ',' || it == '.' } },
            label = { Text("Erhalten") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Dropdown(
            label = "Währung",
            value = currency,
            options = foreign,
            onSelect = { currency = it },
            modifier = Modifier.width(130.dp),
        )
    }

    OutlinedTextField(
        value = paidText,
        onValueChange = { v -> paidText = v.filter { it.isDigit() || it == ',' || it == '.' } },
        label = { Text("Bezahlt / abgebucht in $base") },
        supportingText = { Text("Laut Kontoauszug bzw. Wechselquittung, inkl. Gebühren") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )

    if (received != null && received > 0 && paid != null && paid > 0) {
        val rate = paid / received
        val ref = data.settings.rates[currency]
        Text(
            buildString {
                append("Dein Kurs: ${rateText(rate, currency, base)}")
                if (ref != null && ref > 0) {
                    append(String.format(German, " (%+.1f %% ggü. Einstellungen)", (rate / ref - 1) * 100))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    DateField(date) { date = it }

    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Notiz (optional)") },
        placeholder = { Text("z. B. Automat Flughafen") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = { onSave(ExchangeDraft(date.toString(), currency, received ?: 0.0, paid ?: 0.0, note.trim())) },
        enabled = valid,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Speichern") }
}

@Composable
private fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    val dateClicks = remember { MutableInteractionSource() }
    LaunchedEffect(dateClicks) {
        dateClicks.interactions.collect { if (it is PressInteraction.Release) showDate = true }
    }
    OutlinedTextField(
        value = date.format(ShortDate),
        onValueChange = {},
        readOnly = true,
        label = { Text("Datum") },
        trailingIcon = {
            IconButton(onClick = { showDate = true }) { Icon(Icons.Filled.CalendarMonth, "Datum wählen") }
        },
        interactionSource = dateClicks,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun Dropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

/** Freitextfeld mit Vorschlägen aus bisherigen Einträgen. */
@Composable
private fun SuggestField(
    label: String,
    value: String,
    suggestions: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var expanded by remember { mutableStateOf(false) }
    val matches = suggestions.filter { it.contains(value.trim(), ignoreCase = true) && it != value }
    ExposedDropdownMenuBox(
        expanded = expanded && matches.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            singleLine = true,
            label = { Text(label) },
            trailingIcon = {
                if (suggestions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded && matches.isNotEmpty())
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false })
            }
        }
    }
}

// ------------------------------------------------------------------ Verbindung

@Composable
private fun ConnectionScreen(
    initialUrl: String,
    initialToken: String,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var token by rememberSaveable { mutableStateOf(initialToken) }
    val urlOk = url.trim().startsWith("https://script.google.com/")

    BackHandler(enabled = canCancel, onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verbindung") },
                navigationIcon = {
                    if (canCancel) IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Verbinde die App mit deinem Google Sheet. Beides findest du nach der Einrichtung des " +
                    "Apps Scripts: die Web-App-URL unter „Bereitstellen“, den App-Schlüssel im Sheet-Menü " +
                    "„Reisekosten → App-Schlüssel anzeigen“.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Web-App-URL") },
                placeholder = { Text("https://script.google.com/macros/s/…/exec") },
                isError = url.isNotBlank() && !urlOk,
                supportingText = {
                    if (url.isNotBlank() && !urlOk) Text("Die URL beginnt mit https://script.google.com/")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("App-Schlüssel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(url, token) },
                enabled = urlOk && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Speichern & verbinden") }
        }
    }
}
