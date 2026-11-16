/**
 * Reisekosten – Google Apps Script Backend (JSON-API für die Android-App)
 * Wird im Google Sheet unter Erweiterungen > Apps Script eingefügt.
 *
 * Tabellenblätter (werden automatisch angelegt):
 *   "Ausgaben"      – eine Zeile pro Ausgabe
 *   "Bargeld"       – eine Zeile pro Geldwechsel / Abhebung am Automaten
 *   "Einstellungen" – Reisename, Budget, Hauptwährung, Wechselkurse
 *
 * Zahlart: "Karte" (Konto/Karte) nutzt den Kurs aus "Einstellungen".
 * "Bar" nutzt den Durchschnittskurs aller Wechsel/Abhebungen dieser Währung
 * (Summe bezahlt in Hauptwährung / Summe erhalten). Ohne Wechsel: Kurs aus "Einstellungen".
 *
 * Sicherheit: Die Web-App läuft als "Jeder" erreichbar, jede Anfrage muss aber
 * den geheimen Schlüssel mitschicken. Den Schlüssel erzeugst du über das Menü
 * "Reisekosten > App-Schlüssel anzeigen" im Sheet.
 */

const SHEET_EXPENSES = 'Ausgaben';
const SHEET_SETTINGS = 'Einstellungen';
const SHEET_CASH = 'Bargeld';
const HEADERS = ['ID', 'Datum', 'Kategorie', 'Beschreibung', 'Betrag', 'Währung', 'Kurs', 'Betrag (Haupt)', 'Bezahlt von', 'Erfasst am', 'Zahlart', 'Land'];
const CASH_HEADERS = ['ID', 'Datum', 'Währung', 'Erhalten', 'Bezahlt (Haupt)', 'Kurs', 'Notiz', 'Erfasst am'];
const CATEGORIES = ['Transport Kurzstrecke', 'Transport Langstrecke', 'Unterkunft', 'Kaution', 'Essen & Trinken', 'Aktivitäten', 'Einkäufe', 'Sonstiges'];

// ---------------------------------------------------------------- Einstieg

function doGet() {
  return json_({ ok: true, info: 'Reisekosten-API läuft. Bitte die Android-App verwenden.' });
}

function doPost(e) {
  try {
    const req = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (!req.token || req.token !== getToken_()) {
      return json_({ ok: false, code: 'auth', error: 'Ungültiger App-Schlüssel.' });
    }
    setup_();
    switch (req.action) {
      case 'getData':
        return json_({ ok: true, data: getData_() });
      case 'addExpense':
        addExpense_(req.expense || {});
        return json_({ ok: true, data: getData_() });
      case 'updateExpense':
        updateExpense_(req.expense || {});
        return json_({ ok: true, data: getData_() });
      case 'deleteExpense':
        deleteRowById_(SHEET_EXPENSES, req.id);
        return json_({ ok: true, data: getData_() });
      case 'addExchange':
        addExchange_(req.exchange || {});
        return json_({ ok: true, data: getData_() });
      case 'deleteExchange':
        deleteRowById_(SHEET_CASH, req.id);
        return json_({ ok: true, data: getData_() });
      default:
        return json_({ ok: false, error: 'Unbekannte Aktion: ' + req.action });
    }
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  }
}

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('Reisekosten')
    .addItem('Tabellen einrichten', 'setup_')
    .addItem('App-Schlüssel anzeigen', 'showToken')
    .addItem('Neuen App-Schlüssel erzeugen', 'resetToken')
    .addToUi();
}

function showToken() {
  setup_();
  SpreadsheetApp.getUi().alert('App-Schlüssel', 'In der Android-App unter „Verbindung“ eintragen:\n\n' + getToken_(), SpreadsheetApp.getUi().ButtonSet.OK);
}

function resetToken() {
  PropertiesService.getScriptProperties().deleteProperty('API_TOKEN');
  showToken();
}

// ---------------------------------------------------------------- Intern

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function getToken_() {
  const props = PropertiesService.getScriptProperties();
  let t = props.getProperty('API_TOKEN');
  if (!t) {
    t = Utilities.getUuid().replace(/-/g, '').slice(0, 20);
    props.setProperty('API_TOKEN', t);
  }
  return t;
}

/** Legt fehlende Blätter mit Standardwerten an */
function setup_() {
  const ss = SpreadsheetApp.getActive();

  let exp = ss.getSheetByName(SHEET_EXPENSES);
  if (!exp) {
    exp = ss.insertSheet(SHEET_EXPENSES);
    exp.appendRow(HEADERS);
    exp.setFrozenRows(1);
    exp.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
    exp.getRange('B:B').setNumberFormat('yyyy-mm-dd');
    exp.getRange('E:E').setNumberFormat('#,##0.00');
    exp.getRange('H:H').setNumberFormat('#,##0.00');
  } else {
    // Ältere Tabelle: fehlende Spalten (z. B. "Zahlart", "Land") ergänzen
    const head = exp.getRange(1, 1, 1, HEADERS.length).getValues()[0];
    HEADERS.forEach((h, i) => {
      if (head[i] === '') exp.getRange(1, i + 1).setValue(h).setFontWeight('bold');
    });
  }

  let cash = ss.getSheetByName(SHEET_CASH);
  if (!cash) {
    cash = ss.insertSheet(SHEET_CASH);
    cash.appendRow(CASH_HEADERS);
    cash.setFrozenRows(1);
    cash.getRange(1, 1, 1, CASH_HEADERS.length).setFontWeight('bold');
    cash.getRange('B:B').setNumberFormat('yyyy-mm-dd');
    cash.getRange('D:E').setNumberFormat('#,##0.00');
    cash.getRange('F:F').setNumberFormat('0.000000');
  }

  let set = ss.getSheetByName(SHEET_SETTINGS);
  if (!set) {
    set = ss.insertSheet(SHEET_SETTINGS);
    set.getRange('A1:B3').setValues([
      ['Reisename', 'Meine Reise'],
      ['Budget', 1500],
      ['Hauptwährung', 'EUR'],
    ]);
    set.getRange('A5:B5').setValues([['Währung', 'Kurs (1 Einheit = x Hauptwährung)']]);
    set.getRange('A6:B8').setValues([['EUR', 1], ['USD', 0.92], ['GBP', 1.17]]);
    set.getRange('A1:A3').setFontWeight('bold');
    set.getRange('A5:B5').setFontWeight('bold');
  }
}

function getSettings_() {
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_SETTINGS);
  const data = sh.getDataRange().getValues();
  const cell = (r, c) => (data[r] && data[r][c] !== undefined ? data[r][c] : '');
  const settings = {
    tripName: String(cell(0, 1) || 'Meine Reise'),
    budget: Number(cell(1, 1)) || 0,
    baseCurrency: String(cell(2, 1) || 'EUR').trim().toUpperCase(),
    rates: {},
  };
  for (let i = 5; i < data.length; i++) {
    const cur = String(data[i][0] || '').trim().toUpperCase();
    const rate = Number(data[i][1]);
    if (cur && rate > 0) settings.rates[cur] = rate;
  }
  settings.rates[settings.baseCurrency] = 1;
  return settings;
}

function rows_(name, cols) {
  const sh = SpreadsheetApp.getActive().getSheetByName(name);
  return sh.getLastRow() > 1 ? sh.getRange(2, 1, sh.getLastRow() - 1, cols).getValues() : [];
}

function isoDate_(v) {
  return v instanceof Date ? Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(v);
}

function getExchanges_() {
  return rows_(SHEET_CASH, CASH_HEADERS.length)
    .filter(r => r[0])
    .map(r => ({
      id: String(r[0]),
      date: isoDate_(r[1]),
      currency: String(r[2]).toUpperCase(),
      received: Number(r[3]),
      paid: Number(r[4]),
      note: String(r[6]),
    }))
    .sort((a, b) => b.date.localeCompare(a.date));
}

/** Durchschnittskurs je Währung aus allen Wechseln/Abhebungen */
function cashRates_(exchanges) {
  const sums = {};
  exchanges.forEach(x => {
    const s = sums[x.currency] || (sums[x.currency] = { received: 0, paid: 0 });
    s.received += x.received;
    s.paid += x.paid;
  });
  const rates = {};
  Object.keys(sums).forEach(c => { if (sums[c].received > 0) rates[c] = sums[c].paid / sums[c].received; });
  return rates;
}

function getData_() {
  const settings = getSettings_();
  const rows = rows_(SHEET_EXPENSES, HEADERS.length);

  const expenses = rows
    .filter(r => r[0])
    .map(r => ({
      id: String(r[0]),
      date: isoDate_(r[1]),
      category: String(r[2]),
      description: String(r[3]),
      amount: Number(r[4]),
      currency: String(r[5]),
      rate: Number(r[6]),
      baseAmount: Number(r[7]),
      paidBy: String(r[8]),
      payment: String(r[10]) === 'Bar' ? 'Bar' : 'Karte',
      country: String(r[11] || ''),
    }))
    .sort((a, b) => b.date.localeCompare(a.date));

  return { settings, categories: CATEGORIES, expenses, exchanges: getExchanges_() };
}

/** Prüft eine Ausgabe aus der App und bringt sie in Form. */
function parseExpense_(e, settings) {
  const amount = Number(String(e.amount).replace(',', '.'));
  // Negativ = Gutschrift (z. B. Kaution zurück, Erstattung)
  if (!isFinite(amount) || amount === 0) throw new Error('Bitte einen gültigen Betrag eingeben.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(e.date || ''))) throw new Error('Ungültiges Datum.');
  const [y, m, d] = e.date.split('-').map(Number);
  return {
    date: new Date(y, m - 1, d),
    category: e.category || 'Sonstiges',
    description: e.description || '',
    amount,
    currency: String(e.currency || settings.baseCurrency).toUpperCase(),
    paidBy: e.paidBy || '',
    payment: e.payment === 'Bar' ? 'Bar' : 'Karte',
    country: String(e.country || '').trim(),
  };
}

/** Kurs für eine neue Ausgabe: bar → Ø-Kurs der Wechsel, sonst Kurs aus "Einstellungen". */
function rateFor_(settings, currency, payment) {
  const cashRate = payment === 'Bar' && currency !== settings.baseCurrency
    ? cashRates_(getExchanges_())[currency] : undefined;
  const rate = cashRate || settings.rates[currency];
  if (!rate) throw new Error('Kein Wechselkurs für ' + currency + ' im Blatt "Einstellungen".');
  return rate;
}

function expenseRow_(id, x, rate, createdAt) {
  return [
    id, x.date, x.category, x.description, x.amount, x.currency, rate,
    Math.round(x.amount * rate * 100) / 100, x.paidBy, createdAt, x.payment, x.country,
  ];
}

/** Zeilennummer (ab 2) der Ausgabe mit dieser ID, sonst 0. */
function findRow_(sh, id) {
  if (sh.getLastRow() < 2) return 0;
  const ids = sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues();
  const i = ids.findIndex(r => String(r[0]) === String(id));
  return i < 0 ? 0 : i + 2;
}

/** Speichert eine Ausgabe. Gleiche ID zweimal = nur einmal gespeichert (wichtig fürs Offline-Nachsenden). */
function addExpense_(e) {
  const settings = getSettings_();
  const x = parseExpense_(e, settings);
  const rate = rateFor_(settings, x.currency, x.payment);

  const id = String(e.id || Utilities.getUuid());
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_EXPENSES);
    if (findRow_(sh, id)) return; // schon vorhanden
    sh.appendRow(expenseRow_(id, x, rate, new Date()));
  } finally {
    lock.releaseLock();
  }
}

/**
 * Ändert eine vorhandene Ausgabe. Bleiben Währung und Zahlart gleich, bleibt auch der
 * gespeicherte Kurs – sonst wird er neu bestimmt wie bei einer neuen Ausgabe.
 */
function updateExpense_(e) {
  const settings = getSettings_();
  const x = parseExpense_(e, settings);
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_EXPENSES);
    const row = findRow_(sh, e.id);
    if (!row) throw new Error('Eintrag nicht gefunden – wurde er inzwischen gelöscht?');
    const old = sh.getRange(row, 1, 1, HEADERS.length).getValues()[0];
    const oldPayment = String(old[10]) === 'Bar' ? 'Bar' : 'Karte';
    const keepRate = String(old[5]).toUpperCase() === x.currency && oldPayment === x.payment && Number(old[6]) > 0;
    const rate = keepRate ? Number(old[6]) : rateFor_(settings, x.currency, x.payment);
    sh.getRange(row, 1, 1, HEADERS.length).setValues([expenseRow_(String(e.id), x, rate, old[9] || new Date())]);
  } finally {
    lock.releaseLock();
  }
}

/** Speichert einen Geldwechsel / eine Abhebung. Auch hier: gleiche ID nur einmal. */
function addExchange_(x) {
  const settings = getSettings_();
  const received = Number(String(x.received).replace(',', '.'));
  const paid = Number(String(x.paid).replace(',', '.'));
  if (!(received > 0) || !(paid > 0)) throw new Error('Bitte gültige Beträge für den Geldwechsel eingeben.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(x.date || ''))) throw new Error('Ungültiges Datum.');
  const currency = String(x.currency || '').trim().toUpperCase();
  if (!currency || currency === settings.baseCurrency) throw new Error('Bitte eine Fremdwährung wählen.');

  const id = String(x.id || Utilities.getUuid());
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_CASH);
    if (sh.getLastRow() > 1) {
      const ids = sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues();
      if (ids.some(r => String(r[0]) === id)) return;
    }
    const [y, m, d] = x.date.split('-').map(Number);
    sh.appendRow([id, new Date(y, m - 1, d), currency, received, paid, paid / received, x.note || '', new Date()]);
  } finally {
    lock.releaseLock();
  }
}

function deleteRowById_(sheetName, id) {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(sheetName);
    if (sh.getLastRow() < 2) return;
    const ids = sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues();
    for (let i = 0; i < ids.length; i++) {
      if (String(ids[i][0]) === String(id)) {
        sh.deleteRow(i + 2);
        break;
      }
    }
  } finally {
    lock.releaseLock();
  }
}
