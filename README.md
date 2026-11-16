# Reisekosten – Android-App + Google Sheet

Die App speichert alle Ausgaben in deinem Google Sheet. Als Brücke dient ein kleines
Google Apps Script. Du brauchst dafür kein Google-Cloud-Projekt und keinen API-Key.

```
Android-App  ──(HTTPS + App-Schlüssel)──▶  Apps Script (Code.gs)  ──▶  Google Sheet
```

## 1. Sheet + Skript einrichten (ca. 5 Minuten, am PC)
1. Neues Google Sheet anlegen (sheets.new), z. B. „Reisekosten Lissabon“.
2. **Erweiterungen → Apps Script** öffnen.
3. Den Inhalt von `Code.gs` in die vorhandene `Code.gs` kopieren (alles ersetzen) und speichern.
4. **Bereitstellen → Neue Bereitstellung** → Zahnrad → **Web-App**
   - *Ausführen als:* **Ich**
   - *Zugriff:* **Jeder**. Nötig, damit die App ohne Google-Login zugreifen kann. Geschützt ist sie durch den App-Schlüssel.
5. **Bereitstellen** und den Zugriff autorisieren. Google warnt „App nicht verifiziert“: *Erweitert → Zu … wechseln*. Das ist dein eigenes Skript.
6. Die **Web-App-URL** kopieren (`https://script.google.com/macros/s/…/exec`).
7. Das Sheet neu laden. Im Menü **Reisekosten → App-Schlüssel anzeigen** wählen. Dabei werden auch die Blätter angelegt.

## 2. App installieren
1. `Reisekosten.apk` aus den **Releases** dieses Repos herunterladen und aufs Handy bringen (z. B. per Google Drive, USB oder E-Mail an dich selbst) und öffnen.
2. Android fragt, ob du Apps aus dieser Quelle zulassen willst: einmal erlauben.
3. In der App die **Web-App-URL** und den **App-Schlüssel** eintragen. Fertig.

Alternativ per USB-Debugging: `adb install Reisekosten.apk`

## 3. Anpassen (im Blatt „Einstellungen“)
| Zelle | Bedeutung |
|---|---|
| B1 | Reisename (Titel in der App) |
| B2 | Budget in Hauptwährung |
| B3 | Hauptwährung, z. B. `EUR` |
| ab Zeile 6 | Weitere Währungen und ihr Kurs (1 Einheit = x Hauptwährung), z. B. `USD` / `0.92` |

Der Kurs wird beim Erfassen mitgespeichert. Spätere Kursänderungen ändern alte Einträge nicht.
Die Kategorien stehen oben in `Code.gs` (`CATEGORIES`). Standard: Transport Kurzstrecke, Transport Langstrecke, Unterkunft, Kaution, Essen & Trinken, Aktivitäten, Einkäufe, Sonstiges. Für diese Kategorien hat die App eigene Icons.

## Funktionen
- Ausgabe erfassen: Betrag, Währung (mit Umrechnung), Zahlart (Karte/Konto oder Bargeld), Kategorie, Beschreibung, Datum, Land, „bezahlt von“
- **Gutschriften:** Ein negativer Betrag (Minus oder ±-Taste im Betragsfeld) zählt als Gutschrift und senkt die Summe, z. B. eine zurückgezahlte Kaution oder eine Erstattung. Kaution beim Zahlen mit +, bei der Rückgabe mit − eintragen. Bar zurückbekommen erhöht den Bargeldstand.
- **Land:** optional pro Ausgabe. Die App schlägt bereits verwendete Länder vor und merkt sich das zuletzt gewählte. Die Übersicht zeigt die Summen nach Land. Steht im Blatt „Ausgaben“ in der Spalte „Land“.
- **Bargeld geholt:** Geldwechsel oder Abhebung am Automaten erfassen, also was du bekommen hast und was es in Hauptwährung gekostet hat (inkl. Gebühren). Steht im Blatt „Bargeld“.
  - Bar-Ausgaben in Fremdwährung werden mit dem **Durchschnittskurs aller Wechsel** dieser Währung umgerechnet. Kartenzahlungen nutzen weiter den Kurs aus „Einstellungen“. Ohne Wechsel gilt auch für Bargeld der Kurs aus „Einstellungen“.
  - Der Wechsel selbst zählt nicht als Ausgabe. Die App zeigt, wie viel Bargeld pro Währung noch übrig ist.
- Budget-Übersicht mit Fortschrittsbalken, Aufschlüsselung nach Kategorie, Tagessummen, Filter
- **Offline-fähig:** Ohne Netz bleiben neue Ausgaben auf dem Handy, markiert als „nicht gesendet“. Sie werden beim nächsten Aktualisieren übertragen, ohne Duplikate. Die letzte Übersicht bleibt sichtbar.
- Mitreisende: Die gleiche URL und den gleichen Schlüssel in ihrer App eintragen.

## Sicherheit
- Wer URL **und** Schlüssel kennt, kann Ausgaben lesen und schreiben. Gib beides also nur Mitreisenden.
- Neuen Schlüssel erzeugen: **Reisekosten → Neuen App-Schlüssel erzeugen**. Der alte ist danach ungültig.

## Nach Änderungen am Skript
**Bereitstellen → Bereitstellungen verwalten → Bearbeiten (Stift) → Version: Neue Version → Bereitstellen.**
So bleibt die URL gleich.

## App selbst bauen
Das Projekt liegt im Ordner `android/` (Kotlin + Jetpack Compose). Du kannst es in Android Studio öffnen oder so bauen:
```
cd android
gradlew assembleDebug
```
Das APK liegt danach unter `android/app/build/outputs/apk/debug/`.
