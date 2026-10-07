# XRechnung Validator

A small web app that validates electronic invoices (XRechnung / ZUGFeRD) using the
[Mustangproject](https://www.mustangproject.org/) validator, version 2.24.0.

- **backend/** — Spring Boot 3.5 REST API (Java 21)
- **frontend/** — Vue 3 + Vite single-page app

## Requirements

- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Node.js 20+ and npm (`node -v`)

## Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080** and exposes one endpoint:

```
POST /upload-xml       (multipart form field "file")
```

It returns JSON:

```json
{
  "fileName": "invoice.xml",
  "status": "VALID",
  "validationResult": "<validation> ... </validation>",
  "error": null
}
```

`status` is one of `VALID`, `INVALID`, or `ERROR`.

## Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**, choose or drag in an `.xml` invoice, and click *Validieren*.

## Notes

- The frontend expects the backend at `http://localhost:8080`. If you change the backend
  port, update `API_URL` in `frontend/src/components/XRechnungValidator.vue` and the allowed
  origin in `backend/.../WebConfig.java`.
- CORS is open only to `http://localhost:5173` (the Vite dev server).
- First backend build downloads the Mustang validator and its dependencies from Maven Central,
  so the initial `mvn` run needs internet access.

# XRechnung Validator – Benutzeranleitung

## 1. Was macht der Validator?

Der XRechnung Validator prüft elektronische Rechnungen darauf, ob sie den geltenden
Standards entsprechen. Unterstützt werden:

- **XRechnung**
- **ZUGFeRD 2 / Factur-X**
- **ZUGFeRD 1**

Geprüft werden sowohl reine XML-Rechnungen als auch PDF-Dateien, bei denen die
Rechnungsdaten im PDF eingebettet sind (ZUGFeRD / Factur-X).

Die Prüfung erfolgt serverseitig mit dem Open-Source-Validator des Mustangprojects.
**Hochgeladene Dateien werden nicht gespeichert.**

## 2. Aufruf

Die Anwendung wird im Browser geöffnet:

**https://xrechnung.cciot.de**

Eine Installation ist nicht erforderlich.

## 3. Rechnung prüfen

1. **Datei auswählen** anklicken oder die Datei per Drag & Drop in den gestrichelten
   Bereich ziehen.
2. Zulässige Formate: **XML** oder **PDF** (max. 20 MB).
3. Die ausgewählte Datei wird mit Namen und Größe angezeigt. Über das **×** kann sie
   wieder entfernt werden.
4. Auf **Validieren** klicken.
5. Nach wenigen Sekunden erscheint das Ergebnis.

Über **Zurücksetzen** wird die Eingabe geleert, um die nächste Rechnung zu prüfen.

## 4. Das Ergebnis verstehen

Das Ergebnis wird als Bewertung in drei Stufen angezeigt:

### Grün – „Die Datei ist in Ordnung."

Die Rechnung ist konform und kann so verwendet werden. Es sind keine Punkte zu
bearbeiten.

### Gelb – „Die Datei ist gültig – mit Anmerkungen."

Die Rechnung kann verwendet werden, erfüllt aber einzelne Empfehlungen nicht
vollständig. Die betroffenen Stellen werden darunter aufgelistet und sollten geprüft
werden. Eine Korrektur ist möglich, aber nicht zwingend erforderlich.

### Rot – „Die Datei muss überarbeitet werden."

Die Rechnung ist nicht konform. Die aufgelisteten Stellen müssen korrigiert werden,
bevor die Rechnung weiterverarbeitet werden kann.

## 5. Die Meldungen

Unterhalb der Bewertung werden die beanstandeten Stellen aufgeführt. Eine Meldung
besteht aus:

- **Regel-Kennung** (z. B. `BR-DE-1`, `BR-CO-16`) – die verletzte Prüfregel, sofern
  der Bericht sie liefert.
- **Meldungstext** – was beanstandet wurde, z. B.
  *„Das Element 'Buyer reference' (BT-10) muss übermittelt werden."*

Kommt dieselbe Meldung mehrfach vor, wird sie zu einem Eintrag zusammengefasst und mit
der Anzahl gekennzeichnet (z. B. `3 ×`).

Typische Regel-Präfixe:

| Präfix   | Bedeutung                                              |
|----------|--------------------------------------------------------|
| `BR-`    | Allgemeine Geschäftsregel aus EN 16931                 |
| `BR-DE-` | Zusätzliche deutsche Anforderung (XRechnung)           |
| `BR-CO-` | Rechenregel (z. B. Summen- und Betragsprüfungen)       |
| `BR-CL-` | Codelisten-Regel (zulässige Schlüsselwerte)            |

## 6. Kennzahlen zur Prüfung

Unter der Bewertung werden die Eckdaten des Prüflaufs angezeigt:

| Feld          | Bedeutung                                                      |
|---------------|----------------------------------------------------------------|
| **Datei**     | Name der geprüften Datei                                       |
| **Profil**    | Erkannter Rechnungsstandard und Profil                         |
| **Regeln**    | Anzahl der geprüften und der beanstandeten Regeln              |
| **Dauer**     | Dauer der Prüfung in Millisekunden                             |
| **Validator** | Eingesetzte Validator-Version                                  |

## 7. Vollständiger Prüfbericht

Über **Vollständiger Bericht (XML)** lässt sich der komplette technische Prüfbericht
einblenden. Er enthält unter anderem die genaue Fundstelle (Location) jeder Meldung
innerhalb der Rechnung.

Mit **Bericht als XML herunterladen** wird der Bericht als Datei gespeichert
(Dateiname: `Pruefbericht_<Rechnungsname>.xml`). Diese Datei eignet sich zur
Dokumentation oder zur Weitergabe an den Rechnungssteller.

## 8. Hinweise

- Bei einer PDF-Datei wird nicht nur das eingebettete XML geprüft, sondern auch der
  PDF-Container selbst. Die Prüfung ist dadurch strenger als bei Online-Validatoren,
  die ausschließlich XML verarbeiten.
- Unterschiedliche Ergebnisse gegenüber anderen Validatoren können auf abweichende
  Versionsstände der Prüfregeln zurückgehen.
- Werden Meldungen angezeigt, die auf fehlende oder leere Pflichtfelder hinweisen,
  liegt die Ursache in den Rechnungsdaten – nicht in der Prüfung.

