# Analisi Trading — Android

Progetto Android nativo in Kotlin + Jetpack Compose. Target minimo Android 15 (API 35), compile/target SDK 36.

## Stato reale di questa versione

Questa è una **prima versione funzionante come struttura applicativa (MVP)**, non una piattaforma professionale di dati finanziari completa.

### Funzioni presenti

- Interfaccia Android nativa con sezioni Mercati, Portafogli, Analisi e Storico.
- Selezione della categoria di mercato nell'interfaccia.
- Creazione di più portafogli locali, aggiunta e rimozione di simboli.
- Livelli di dettaglio Base, Avanzato ed Esperto.
- Calcolo locale di variazione giornaliera, SMA20, SMA50, RSI(14) con smoothing di Wilder, volatilità storica annualizzata, rapporto volume/precedente media 20 sedute e massimo drawdown.
- Grafico lineare dei prezzi recenti.
- Storico sintetico delle analisi conservato sul dispositivo (ultime 100; salva simbolo, livello, data e riepilogo, non l’intera serie storica).
- Nessuna quotazione di esempio viene spacciata per dato reale: i dati assenti sono N/D o generano un messaggio esplicito.
- Nessuna funzione di invio ordini o collegamento a broker.

### Limiti espliciti

- Il connettore attualmente implementato è Stooq CSV giornaliero con copertura iniziale per azioni USA (esempio simbolo `AAPL`, convertito in `aapl.us`). La disponibilità del simbolo e del servizio dipende dal fornitore e può cambiare.
- Non è un feed real-time. Non garantisce dati intraday.
- Le categorie Crypto, Materie prime, Obbligazionario, ETF, Indici e Valute sono predisposte nell'interfaccia, ma **non hanno ancora un provider dati implementato**. L'app blocca l'analisi per tali categorie per evitare di mostrare dati di un mercato diverso.
- Non sono implementati order book, volumi aggressori compratori/venditori, flussi istituzionali, open interest, posizionamento COT, dati completi sui futures, fondamentali certificati, bilanci, stime analisti o dati proprietari a pagamento.
- La modalità AI è dichiarata come non configurata. Non viene chiamata alcuna AI e non viene simulato un responso AI.
- Non è presente sincronizzazione cloud: portafogli e storico sono locali al dispositivo. I portafogli sono elenchi di simboli, senza quantità, prezzi di carico o pesi; l’analisi aggregata del portafoglio non è ancora implementata.
- L'app non fornisce consulenza finanziaria e non formula segnali automatici di acquisto/vendita.

L'accesso gratuito a dati soggetti a licenza non può essere garantito. Prima di un uso continuativo occorre verificare termini, licenze, attribuzione, limiti e condizioni del fornitore dati.

## Struttura

```text
TradingAnalysisApp/
├── .github/workflows/android.yml
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/it/pietro/tradinganalysis/
│   │   ├── MainActivity.kt
│   │   ├── data/LocalStore.kt
│   │   ├── data/StooqDataSource.kt
│   │   └── domain/MarketModels.kt
│   │       domain/TechnicalAnalysis.kt
│   └── src/test/java/it/pietro/tradinganalysis/domain/TechnicalAnalysisTest.kt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Generare l'APK con GitHub

1. Crea un repository GitHub vuoto.
2. Estrai questo archivio ZIP e carica **tutti i file e le cartelle** nella radice del repository.
3. Esegui il commit sul branch `main` (oppure `master`).
4. Apri la scheda **Actions** del repository.
5. Attendi il completamento del workflow **Android APK**. Il workflow esegue i test unitari e `assembleDebug`.
6. Apri la run completata e scarica l'artifact `analisi-trading-debug-apk`.
7. Estrai l'artifact: contiene `app-debug.apk`, firmato con la chiave debug automatica di Android Gradle Plugin e installabile su un dispositivo compatibile.

Il workflow può anche essere avviato manualmente da **Actions → Android APK → Run workflow**.

## Compilazione locale

Requisiti: JDK 17, Android SDK con piattaforma 36 e Build Tools 36.0.0, Gradle 8.13.

Dalla cartella del progetto:

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Questo archivio non contiene `gradle-wrapper.jar`: la pipeline GitHub installa Gradle 8.13 direttamente tramite `gradle/actions/setup-gradle`. Per una compilazione locale si usa Gradle 8.13 installato nel sistema.

## Verifiche e sicurezza

- `minSdk = 35`: Android 15 o successivo.
- Il traffico dati usa HTTPS; il cleartext HTTP è disabilitato.
- Le richieste di rete hanno timeout di connessione e lettura.
- I simboli sono validati prima di inserirli nell'URL.
- I dati ricevuti vengono controllati e ordinati per data prima dei calcoli.
- I test unitari coprono input vuoto, variazione, medie mobili, RSI crescente/decrescente e drawdown.
- I risultati dipendono dalla qualità e dalla disponibilità della fonte esterna.

**Nota sulla verifica:** l'ambiente che ha generato l'archivio non dispone di Gradle/Android SDK e non ha accesso alla rete per scaricare dipendenze. Di conseguenza non è possibile dichiarare che la compilazione sia stata eseguita qui. La verifica automatica effettiva è demandata al workflow GitHub incluso; controlla che sia verde prima di installare l'APK.
