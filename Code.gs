/**
 * Reisekosten – Google Apps Script Backend (JSON-API für die Android-App)
 * Wird im Google Sheet unter Erweiterungen > Apps Script eingefügt.
 *
 * Tabellenblätter (werden automatisch angelegt):
 *   "Ausgaben"      – eine Zeile pro Ausgabe
 *   "Einstellungen" – Reisename, Budget, Hauptwährung, Wechselkurse
 *
 * Sicherheit: Die Web-App läuft als "Jeder" erreichbar, jede Anfrage muss aber
 * den geheimen Schlüssel mitschicken. Den Schlüssel erzeugst du über das Menü
 * "Reisekosten > App-Schlüssel anzeigen" im Sheet.
 */

const SHEET_EXPENSES = 'Ausgaben';
const SHEET_SETTINGS = 'Einstellungen';
const HEADERS = ['ID', 'Datum', 'Kategorie', 'Beschreibung', 'Betrag', 'Währung', 'Kurs', 'Betrag (Haupt)', 'Bezahlt von', 'Erfasst am'];
const CATEGORIES = ['Transport', 'Unterkunft', 'Essen & Trinken', 'Aktivitäten', 'Einkäufe', 'Sonstiges'];

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
      case 'deleteExpense':
        deleteExpense_(req.id);
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

function getData_() {
  const settings = getSettings_();
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_EXPENSES);
  const rows = sh.getLastRow() > 1
    ? sh.getRange(2, 1, sh.getLastRow() - 1, HEADERS.length).getValues()
    : [];
  const tz = Session.getScriptTimeZone();

  const expenses = rows
    .filter(r => r[0])
    .map(r => ({
      id: String(r[0]),
      date: r[1] instanceof Date ? Utilities.formatDate(r[1], tz, 'yyyy-MM-dd') : String(r[1]),
      category: String(r[2]),
      description: String(r[3]),
      amount: Number(r[4]),
      currency: String(r[5]),
      rate: Number(r[6]),
      baseAmount: Number(r[7]),
      paidBy: String(r[8]),
    }))
    .sort((a, b) => b.date.localeCompare(a.date));

  return { settings, categories: CATEGORIES, expenses };
}

/** Speichert eine Ausgabe. Gleiche ID zweimal = nur einmal gespeichert (wichtig fürs Offline-Nachsenden). */
function addExpense_(e) {
  const settings = getSettings_();
  const amount = Number(String(e.amount).replace(',', '.'));
  if (!(amount > 0)) throw new Error('Bitte einen gültigen Betrag eingeben.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(e.date || ''))) throw new Error('Ungültiges Datum.');

  const currency = String(e.currency || settings.baseCurrency).toUpperCase();
  const rate = settings.rates[currency];
  if (!rate) throw new Error('Kein Wechselkurs für ' + currency + ' im Blatt "Einstellungen".');

  const id = String(e.id || Utilities.getUuid());
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_EXPENSES);
    if (sh.getLastRow() > 1) {
      const ids = sh.getRange(2, 1, sh.getLastRow() - 1, 1).getValues();
      if (ids.some(r => String(r[0]) === id)) return; // schon vorhanden
    }
    const [y, m, d] = e.date.split('-').map(Number);
    sh.appendRow([
      id,
      new Date(y, m - 1, d),
      e.category || 'Sonstiges',
      e.description || '',
      amount,
      currency,
      rate,
      Math.round(amount * rate * 100) / 100,
      e.paidBy || '',
      new Date(),
    ]);
  } finally {
    lock.releaseLock();
  }
}

function deleteExpense_(id) {
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_EXPENSES);
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
