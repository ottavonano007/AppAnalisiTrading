package it.pietro.tradinganalysis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import it.pietro.tradinganalysis.data.LocalStore
import it.pietro.tradinganalysis.data.ApiKeyStore
import it.pietro.tradinganalysis.data.AlphaVantageDataSource
import it.pietro.tradinganalysis.data.FallbackMarketDataSource
import it.pietro.tradinganalysis.data.StooqDataSource
import it.pietro.tradinganalysis.data.YahooFinanceDataSource
import it.pietro.tradinganalysis.data.SymbolSearchDataSource
import it.pietro.tradinganalysis.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        val apiKeyStore = ApiKeyStore(applicationContext)
        setContent { TradingApp(store, apiKeyStore) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradingApp(store: LocalStore, apiKeyStore: ApiKeyStore) {
    val colors = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF70D6B0),
        secondary = androidx.compose.ui.graphics.Color(0xFF91A9FF),
        background = androidx.compose.ui.graphics.Color(0xFF10151D),
        surface = androidx.compose.ui.graphics.Color(0xFF19212C),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF263241)
    )
    MaterialTheme(colorScheme = colors) {
        var tab by remember { mutableIntStateOf(0) }
        var level by remember { mutableStateOf(AnalysisLevel.ADVANCED) }
        var mode by remember { mutableStateOf(AnalysisMode.DATA_ONLY) }
        var symbol by remember { mutableStateOf("AAPL") }
        var market by remember { mutableStateOf(MarketType.STOCK) }
        var portfolios by remember { mutableStateOf(store.portfolios()) }
        var selectedPortfolio by remember { mutableStateOf(portfolios.keys.firstOrNull() ?: "Principale") }
        var history by remember { mutableStateOf(store.history()) }
        var snapshot by remember { mutableStateOf<TechnicalSnapshot?>(null) }
        var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
        var analyzedSymbol by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Inserisci un simbolo e avvia un'analisi.") }
        var loading by remember { mutableStateOf(false) }
        var showPortfolioDialog by remember { mutableStateOf(false) }
        var newPortfolioName by remember { mutableStateOf("") }
        var showProviderSettings by remember { mutableStateOf(false) }
        var alphaApiKey by remember { mutableStateOf(apiKeyStore.read().orEmpty()) }
        val scope = rememberCoroutineScope()
        

        fun analyze(target: String = symbol, targetMarket: MarketType = market) {
            val clean = target.trim().uppercase()
            if (loading) return
            if (clean.isBlank()) { status = "Inserisci un simbolo valido."; return }
            if (targetMarket != MarketType.STOCK) {
                snapshot = null; candles = emptyList(); analyzedSymbol = ""
                status = "Feed non ancora integrato per ${targetMarket.label}. In questa versione i connettori disponibili sono utilizzati per titoli azionari USA; nessun dato alternativo viene simulato."
                return
            }
            loading = true
            status = "Recupero dati giornalieri: Stooq → Yahoo Finance → Alpha Vantage…"
            scope.launch {
                val providers = buildList {
                    add(StooqDataSource())
                    add(YahooFinanceDataSource())
                    if (alphaApiKey.isNotBlank()) add(AlphaVantageDataSource(alphaApiKey))
                }
                val result = FallbackMarketDataSource(providers).loadDaily(clean)
                result.onSuccess { data ->
                    val values = data.candles
                    val computed = TechnicalAnalysis.calculate(values)
                    candles = values
                    snapshot = computed
                    analyzedSymbol = clean
                    status = if (computed != null) "Fonte: ${data.provider}. Ultima sessione: ${computed.lastDate}." + (data.note?.let { " $it." } ?: "") else "Dati insufficienti per l'analisi. Fonte: ${data.provider}."
                    if (computed != null) {
                        val summary = "Chiusura ${fmt(computed.lastClose)} · ${computed.observations} osservazioni · RSI ${computed.rsi14?.let { fmt(it) } ?: "n/d"}"
                        val record = AnalysisRecord(System.currentTimeMillis(), clean, targetMarket.label, level.label,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), summary)
                        store.addHistory(record)
                        history = store.history()
                    }
                }.onFailure { error ->
                    snapshot = null; candles = emptyList(); analyzedSymbol = ""
                    status = "Dato non disponibile: ${error.message ?: "errore di rete"}. Nessun valore sostitutivo è stato inventato."
                }
                loading = false
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Mercati") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Portafogli") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Analisi") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Refresh, null) }, label = { Text("Storico") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding)) {
                TopHeader()
                when (tab) {
                    0 -> MarketScreen(
                        symbol, { symbol = it }, market, { market = it }, level, { level = it }, mode, { mode = it },
                        loading, status, snapshot, candles, analyzedSymbol, ::analyze,
                        onProviderSettings = { showProviderSettings = true },
                        addToPortfolio = {
                            val updated = portfolios.toMutableMap()
                            updated[selectedPortfolio] = (updated[selectedPortfolio].orEmpty() + symbol.trim().uppercase()).distinct()
                            portfolios = updated; store.savePortfolios(updated)
                            status = "${symbol.uppercase()} aggiunto a $selectedPortfolio."
                        }
                    )
                    1 -> PortfolioScreen(portfolios, selectedPortfolio, alphaApiKey, { selectedPortfolio = it },
                        onCreate = { showPortfolioDialog = true }, onRemove = { name, ticker ->
                            val updated = portfolios.toMutableMap()
                            updated[name] = updated[name].orEmpty() - ticker
                            portfolios = updated; store.savePortfolios(updated)
                        }, onAnalyze = { symbol = it; market = MarketType.STOCK; tab = 2; analyze(it, MarketType.STOCK) })
                    2 -> AnalysisScreen(analyzedSymbol, snapshot, candles, level, { level = it }, mode, { mode = it }, status, ::analyze)
                    else -> HistoryScreen(history, onOpen = { symbol = it; market = MarketType.STOCK; tab = 0; analyze(it) })
                }
            }
        }
        if (showProviderSettings) {
            AlertDialog(
                onDismissRequest = { showProviderSettings = false },
                title = { Text("Fonti dati") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ordine automatico: Stooq, Yahoo Finance, Alpha Vantage.", style = MaterialTheme.typography.bodyMedium)
                        Text("Yahoo Finance usa un'interfaccia non ufficiale e non documentata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = alphaApiKey,
                            onValueChange = { alphaApiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Chiave API Alpha Vantage") },
                            placeholder = { Text("Inserisci la tua API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Text("La chiave viene salvata sul dispositivo, cifrata con Android Keystore. Non viene inserita nel repository. L'uso in un'app client non la rende completamente segreta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Alpha Vantage usa TIME_SERIES_DAILY in modalità compact (ultimi 100 dati giornalieri).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        apiKeyStore.save(alphaApiKey)
                        alphaApiKey = apiKeyStore.read().orEmpty()
                        showProviderSettings = false
                        status = if (alphaApiKey.isBlank()) "Chiave Alpha Vantage rimossa. Verranno provate Stooq e Yahoo Finance." else "Chiave Alpha Vantage salvata sul dispositivo. Verrà usata come terza fonte."
                    }) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { alphaApiKey = apiKeyStore.read().orEmpty(); showProviderSettings = false }) { Text("Annulla") }
                }
            )
        }
        if (showPortfolioDialog) {
            AlertDialog(onDismissRequest = { showPortfolioDialog = false },
                title = { Text("Nuovo portafoglio") },
                text = { OutlinedTextField(value = newPortfolioName, onValueChange = { newPortfolioName = it }, label = { Text("Nome") }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    val name = newPortfolioName.trim()
                    if (name.isNotEmpty() && name !in portfolios) {
                        portfolios = portfolios + (name to emptyList())
                        store.savePortfolios(portfolios); selectedPortfolio = name
                    }
                    newPortfolioName = ""; showPortfolioDialog = false
                }) { Text("Crea") } },
                dismissButton = { TextButton(onClick = { showPortfolioDialog = false }) { Text("Annulla") } }
            )
        }
    }
}

@Composable
private fun TopHeader() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ANALISI", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.width(7.dp)); Text("TRADING", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f)); Text("v0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Text("Mercati, dati, metodo.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketScreen(
    symbol: String, onSymbol: (String) -> Unit, market: MarketType, onMarket: (MarketType) -> Unit,
    level: AnalysisLevel, onLevel: (AnalysisLevel) -> Unit, mode: AnalysisMode, onMode: (AnalysisMode) -> Unit,
    loading: Boolean, status: String, snapshot: TechnicalSnapshot?, candles: List<Candle>, analyzedSymbol: String,
    onAnalyze: (String) -> Unit, onProviderSettings: () -> Unit, addToPortfolio: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("Seleziona mercato")
            MarketType.values().toList().chunked(2).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { item -> FilterChip(selected = market == item, onClick = { onMarket(item) }, enabled = !loading, label = { Text(item.label, fontSize = 11.sp) }) }
                }
            }
        }
        item {
            CardBlock {
                Text("Strumento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                var suggestions by remember { mutableStateOf<List<it.pietro.tradinganalysis.data.SymbolSuggestion>>(emptyList()) }
                var suggestionsExpanded by remember { mutableStateOf(false) }
                var selectedTicker by remember { mutableStateOf("") }
                var searchFinished by remember { mutableStateOf(false) }
                LaunchedEffect(symbol, market) {
                    val query = symbol.trim()
                    if (market != MarketType.STOCK || query.length < 2 || query.equals(selectedTicker, ignoreCase = true)) {
                        suggestions = emptyList()
                        suggestionsExpanded = false
                        searchFinished = false
                    } else {
                        delay(350)
                        val result = SymbolSearchDataSource().search(query)
                        suggestions = result.getOrElse { emptyList() }
                        searchFinished = result.isSuccess
                        suggestionsExpanded = suggestions.isNotEmpty()
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = symbol, onValueChange = {
                        selectedTicker = ""
                        onSymbol(it.uppercase())
                    }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ticker o nome dell'azienda") }, placeholder = { Text("Es. AAPL oppure Apple") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                    DropdownMenu(
                        expanded = suggestionsExpanded && suggestions.isNotEmpty(),
                        onDismissRequest = { suggestionsExpanded = false },
                        modifier = Modifier.fillMaxWidth(),
                        properties = PopupProperties(focusable = false)
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${suggestion.symbol} · ${suggestion.name}", fontWeight = FontWeight.SemiBold)
                                        Text("${suggestion.exchange} · ${suggestion.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    selectedTicker = suggestion.symbol
                                    onSymbol(suggestion.symbol)
                                    suggestionsExpanded = false
                                }
                            )
                        }
                    }
                }
                if (symbol.trim().length >= 2 && market == MarketType.STOCK) {
                    Text(
                        when {
                            suggestions.isNotEmpty() -> "Seleziona uno strumento dall'elenco per compilare il ticker."
                            searchFinished -> "Nessun risultato trovato. Puoi comunque inserire il ticker manualmente."
                            else -> "Ricerca suggerimenti in corso…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Ricerca assistita tramite Yahoo Finance · fonti dati: Stooq → Yahoo Finance → Alpha Vantage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onProviderSettings, modifier = Modifier.fillMaxWidth()) { Text("Configura Alpha Vantage") }
                Button(onClick = { onAnalyze(symbol) }, enabled = !loading && symbol.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Avvia analisi")
                }
                OutlinedButton(onClick = addToPortfolio, enabled = symbol.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Aggiungi al portafoglio") }
            }
        }
        item {
            CardBlock {
                SectionTitle("Impostazioni analisi")
                Text("Livello di dettaglio", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnalysisLevel.values().forEach { FilterChip(selected = level == it, onClick = { onLevel(it) }, enabled = !loading, label = { Text(it.label) }) }
                }
                Text("Elaborazione", style = MaterialTheme.typography.labelLarge)
                AnalysisMode.values().forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == item, onClick = { onMode(item) }, enabled = item == AnalysisMode.DATA_ONLY && !loading)
                        Column(Modifier.weight(1f)) {
                            Text(item.label)
                            if (item == AnalysisMode.AI) Text("Richiede un servizio AI e una chiave configurata. Non attivo in questa versione.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { StatusCard(status) }
        if (snapshot != null && analyzedSymbol.isNotBlank()) {
            item { SnapshotCard(analyzedSymbol, snapshot, candles, level) }
        }
        item { Disclaimer() }
    }
}

@Composable
private fun AnalysisScreen(symbol: String, snapshot: TechnicalSnapshot?, candles: List<Candle>, level: AnalysisLevel,
                           onLevel: (AnalysisLevel) -> Unit, mode: AnalysisMode, onMode: (AnalysisMode) -> Unit,
                           status: String, onAnalyze: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Analisi tecnica") }
        item {
            CardBlock {
                Text("Livello", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AnalysisLevel.values().forEach { FilterChip(selected = level == it, onClick = { onLevel(it) }, label = { Text(it.label) }) } }
                Text("Modalità", style = MaterialTheme.typography.labelLarge)
                AnalysisMode.values().forEach { Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == it, onClick = { onMode(it) }, enabled = it == AnalysisMode.DATA_ONLY)
                    Text(it.label)
                } }
            }
        }
        if (snapshot != null) item {
            Button(onClick = { onAnalyze(symbol) }, enabled = symbol.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Aggiorna analisi") }
            SnapshotCard(symbol, snapshot, candles, level)
        } else item { StatusCard("$status\nEsegui prima un'analisi dalla sezione Mercati.") }
        item { Disclaimer() }
    }
}

@Composable
private fun SnapshotCard(symbol: String, s: TechnicalSnapshot, candles: List<Candle>, level: AnalysisLevel) {
    var selectedAverages by remember(symbol) { mutableStateOf(setOf<String>()) }
    val onToggleAverage: (String) -> Unit = { key ->
        selectedAverages = if (key in selectedAverages) selectedAverages - key else selectedAverages + key
    }
    CardBlock {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Ultima data: ${s.lastDate}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    Text("${fmt(s.lastClose)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                MetricGrid(when (level) {
                    AnalysisLevel.BASE -> metricValues(s).take(3)
                    AnalysisLevel.ADVANCED -> metricValues(s).take(10)
                    AnalysisLevel.EXPERT -> metricValues(s)
                })
                if (level != AnalysisLevel.BASE) MovingAveragePanel(s, selectedAverages, onToggleAverage)
                Text("Indicatori descrittivi, non segnali automatici di acquisto o vendita.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (candles.size > 1) PriceLine(candles, selectedAverages)
    }
}

private fun metricValues(s: TechnicalSnapshot): List<Pair<String, String?>> = listOf(
    "Variazione" to s.changePercent?.let { "${fmt(it)}%" },
    "RSI (14)" to s.rsi14?.let(::fmt),
    "SMA 20" to s.sma20?.let(::fmt),
    "SMA 50" to s.sma50?.let(::fmt),
    "SMA 200" to s.sma200?.let(::fmt),
    "EMA 12" to s.ema12?.let(::fmt),
    "EMA 26" to s.ema26?.let(::fmt),
    "MACD" to s.macd?.let(::fmt),
    "Segnale MACD (9)" to s.macdSignal?.let(::fmt),
    "Bollinger superiore" to s.bollingerUpper?.let(::fmt),
    "Bollinger inferiore" to s.bollingerLower?.let(::fmt),
    "ATR (14)" to s.atr14?.let(::fmt),
    "Stocastico %K (14)" to s.stochasticK14?.let(::fmt),
    "Volatilità ann." to s.annualizedVolatility?.let { "${fmt(it)}%" },
    "Volume / media 20g" to s.volumeVs20DayAverage?.let { "${fmt(it)}x" },
    "Max drawdown" to s.maxDrawdown?.let { "${fmt(it)}%" },
    "Osservazioni" to s.observations.toString()
)

private data class AverageLine(val key: String, val label: String, val value: Double?, val color: Color)

private val averageLines = listOf(
    AverageLine("SMA20", "SMA 20", null, Color(0xFFFFC857)),
    AverageLine("SMA50", "SMA 50", null, Color(0xFF4FC3F7)),
    AverageLine("SMA200", "SMA 200", null, Color(0xFFE57373)),
    AverageLine("EMA12", "EMA 12", null, Color(0xFF81C784)),
    AverageLine("EMA26", "EMA 26", null, Color(0xFFBA68C8))
)

@Composable
private fun MovingAveragePanel(
    s: TechnicalSnapshot,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val rows = listOf(
        Triple("SMA20", "SMA 20", s.sma20),
        Triple("SMA50", "SMA 50", s.sma50),
        Triple("SMA200", "SMA 200", s.sma200),
        Triple("EMA12", "EMA 12", s.ema12),
        Triple("EMA26", "EMA 26", s.ema26)
    )
    val shortLong = if (s.sma50 != null && s.sma200 != null) {
        if (s.sma50 > s.sma200) "SMA 50 sopra SMA 200" else if (s.sma50 < s.sma200) "SMA 50 sotto SMA 200" else "SMA 50 e SMA 200 allineate"
    } else "Servono almeno 200 sedute per confrontare SMA 50 e SMA 200"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Confronto medie mobili · tocca per mostrarle sul grafico", fontWeight = FontWeight.SemiBold)
        rows.forEach { (key, name, value) ->
            val color = averageColor(key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = key in selected,
                    onClick = { onToggle(key) },
                    enabled = value != null,
                    label = { Text(name, color = if (value != null) color else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Spacer(Modifier.weight(1f))
                Text(value?.let(::fmt) ?: "N/D", fontWeight = FontWeight.SemiBold)
                if (value != null) {
                    val distance = (s.lastClose / value - 1.0) * 100.0
                    Spacer(Modifier.width(8.dp))
                    Text("${if (distance >= 0) "+" else ""}${fmt(distance)}%", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        Text(shortLong, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Le percentuali indicano la distanza della chiusura dalla media, non una previsione.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun averageColor(key: String): Color = when (key) {
    "SMA20" -> Color(0xFFFFC857)
    "SMA50" -> Color(0xFF4FC3F7)
    "SMA200" -> Color(0xFFE57373)
    "EMA12" -> Color(0xFF81C784)
    else -> Color(0xFFBA68C8)
}

@Composable
private fun MetricGrid(values: List<Pair<String, String?>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value ?: "N/D", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } }
    }
}

@Composable
private fun PriceLine(candles: List<Candle>, selected: Set<String>) {
    if (candles.size < 2) return
    val closes = candles.map { it.close }
    val overlays = buildList {
        if ("SMA20" in selected) add("SMA20" to movingAverageSeries(closes, 20, false))
        if ("SMA50" in selected) add("SMA50" to movingAverageSeries(closes, 50, false))
        if ("SMA200" in selected) add("SMA200" to movingAverageSeries(closes, 200, false))
        if ("EMA12" in selected) add("EMA12" to movingAverageSeries(closes, 12, true))
        if ("EMA26" in selected) add("EMA26" to movingAverageSeries(closes, 26, true))
    }
    val start = (candles.size - 60).coerceAtLeast(0)
    val visibleCloses = closes.drop(start)
    val visibleOverlays = overlays.map { (key, values) -> key to values.drop(start) }
    val allValues = visibleCloses + visibleOverlays.flatMap { it.second.filterNotNull() }
    val min = allValues.minOrNull() ?: return
    val max = allValues.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val priceColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prezzo e medie mobili · ultime ${visibleCloses.size} sedute", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(vertical = 8.dp)) {
            fun point(index: Int, value: Double): Offset = Offset(
                x = size.width * index / (visibleCloses.size - 1),
                y = size.height - ((value - min) / range).toFloat() * size.height
            )
            visibleCloses.zipWithNext().forEachIndexed { index, (a, b) ->
                drawLine(priceColor, point(index, a), point(index + 1, b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            visibleOverlays.forEach { (key, values) ->
                val color = averageColor(key)
                for (index in 0 until values.lastIndex) {
                    val a = values[index]
                    val b = values[index + 1]
                    if (a != null && b != null) {
                        drawLine(color, point(index, a), point(index + 1, b), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine("Prezzo", priceColor)
            overlays.forEach { (key, _) -> LegendLine(averageLabel(key), averageColor(key)) }
        }
    }
}

@Composable
private fun LegendLine(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(16.dp).height(3.dp)) { drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx()) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun averageLabel(key: String): String = when (key) {
    "SMA20" -> "SMA20"
    "SMA50" -> "SMA50"
    "SMA200" -> "SMA200"
    "EMA12" -> "EMA12"
    else -> "EMA26"
}

private fun movingAverageSeries(values: List<Double>, period: Int, exponential: Boolean): List<Double?> {
    val output = MutableList<Double?>(values.size) { null }
    if (values.size < period) return output
    if (!exponential) {
        var sum = values.take(period).sum()
        output[period - 1] = sum / period
        for (index in period until values.size) {
            sum += values[index] - values[index - period]
            output[index] = sum / period
        }
    } else {
        val multiplier = 2.0 / (period + 1.0)
        var ema = values.take(period).average()
        output[period - 1] = ema
        for (index in period until values.size) {
            ema = (values[index] - ema) * multiplier + ema
            output[index] = ema
        }
    }
    return output
}

private enum class PortfolioChartWindow(val label: String, val sessions: Int?) {
    MONTH("1M", 21), THREE_MONTHS("3M", 63), SIX_MONTHS("6M", 126),
    YEAR("1A", 252), THREE_YEARS("3A", 756), FIVE_YEARS("5A", 1260), ALL("Tutto", null)
}

private enum class PortfolioChartStyle(val label: String) {
    CANDLE("Candele"), HEIKIN_ASHI("Heikin-Ashi"), OHLC("Barre OHLC"), LINE("Linea")
}

@Composable
private fun PortfolioScreen(portfolios: Map<String, List<String>>, selected: String, alphaApiKey: String,
                            onSelect: (String) -> Unit, onCreate: () -> Unit,
                            onRemove: (String, String) -> Unit, onAnalyze: (String) -> Unit) {
    var chartView by remember { mutableStateOf(false) }
    var chartWindow by remember { mutableStateOf(PortfolioChartWindow.SIX_MONTHS) }
    var chartStyle by remember { mutableStateOf(PortfolioChartStyle.CANDLE) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("I tuoi portafogli")
                Spacer(Modifier.weight(1f))
                Button(onClick = onCreate) { Text("+ Nuovo") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                portfolios.keys.forEach { name ->
                    FilterChip(selected = selected == name, onClick = { onSelect(name) }, label = { Text(name) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Visualizzazione", style = MaterialTheme.typography.labelLarge)
                FilterChip(selected = !chartView, onClick = { chartView = false }, label = { Text("Elenco") })
                FilterChip(selected = chartView, onClick = { chartView = true }, label = { Text("Grafici") })
            }
        }
        if (chartView) {
            item {
                CardBlock {
                    Text("Periodo del grafico", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(PortfolioChartWindow.MONTH, PortfolioChartWindow.THREE_MONTHS, PortfolioChartWindow.SIX_MONTHS, PortfolioChartWindow.YEAR).forEach {
                            FilterChip(selected = chartWindow == it, onClick = { chartWindow = it }, label = { Text(it.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(PortfolioChartWindow.THREE_YEARS, PortfolioChartWindow.FIVE_YEARS, PortfolioChartWindow.ALL).forEach {
                            FilterChip(selected = chartWindow == it, onClick = { chartWindow = it }, label = { Text(it.label) })
                        }
                    }
                    Text("Tipo di grafico", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(PortfolioChartStyle.CANDLE, PortfolioChartStyle.HEIKIN_ASHI).forEach {
                            FilterChip(selected = chartStyle == it, onClick = { chartStyle = it }, label = { Text(it.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(PortfolioChartStyle.OHLC, PortfolioChartStyle.LINE).forEach {
                            FilterChip(selected = chartStyle == it, onClick = { chartStyle = it }, label = { Text(it.label) })
                        }
                    }
                    Text("Asse X: tempo · Asse Y: prezzo nella valuta di quotazione.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val symbols = portfolios[selected].orEmpty()
        if (symbols.isEmpty()) {
            item {
                CardBlock {
                    Text("Portafoglio vuoto", fontWeight = FontWeight.SemiBold)
                    Text("Aggiungi strumenti dalla sezione Mercati per creare il tuo elenco di osservazione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (chartView) {
            items(symbols, key = { "chart-$it" }) { ticker ->
                PortfolioChartCard(ticker, alphaApiKey, chartWindow, chartStyle, onAnalyze, onRemove = { onRemove(selected, ticker) })
            }
        } else {
            items(symbols, key = { "list-$it" }) { ticker ->
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Dato non aggiornato finché non avvii l'analisi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onAnalyze(ticker) }) { Text("Scheda tecnica") }
                        TextButton(onClick = { onRemove(selected, ticker) }) { Text("Rimuovi") }
                    }
                }
            }
        }
        item { Disclaimer() }
    }
}

@Composable
private fun PortfolioChartCard(
    ticker: String,
    alphaApiKey: String,
    chartWindow: PortfolioChartWindow,
    chartStyle: PortfolioChartStyle,
    onAnalyze: (String) -> Unit,
    onRemove: () -> Unit
) {
    var candles by remember(ticker, alphaApiKey) { mutableStateOf<List<Candle>>(emptyList()) }
    var provider by remember(ticker, alphaApiKey) { mutableStateOf("") }
    var error by remember(ticker, alphaApiKey) { mutableStateOf<String?>(null) }
    var loading by remember(ticker, alphaApiKey) { mutableStateOf(true) }

    LaunchedEffect(ticker, alphaApiKey) {
        loading = true
        error = null
        val providers = buildList {
            add(StooqDataSource())
            add(YahooFinanceDataSource())
            if (alphaApiKey.isNotBlank()) add(AlphaVantageDataSource(alphaApiKey))
        }
        FallbackMarketDataSource(providers).loadDaily(ticker).onSuccess { data ->
            candles = data.candles
            provider = data.provider
            if (data.candles.size < 2) error = "Storico insufficiente per il grafico."
        }.onFailure { cause ->
            error = cause.message ?: "Dati non disponibili."
        }
        loading = false
    }

    val visible = chartWindow.sessions?.let { candles.takeLast(it) } ?: candles
    val firstClose = visible.firstOrNull()?.close
    val lastClose = visible.lastOrNull()?.close
    val change = if (firstClose != null && firstClose != 0.0 && lastClose != null) (lastClose / firstClose - 1.0) * 100.0 else null
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(if (provider.isNotBlank()) "Fonte: $provider · \${visible.size} sedute" else "Andamento storico", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lastClose != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt(lastClose), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (change != null) Text(
                        "\${if (change >= 0) "+" else ""}\${fmt(change)}% · periodo",
                        color = if (change >= 0) Color(0xFF81C784) else Color(0xFFE57373),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Caricamento dello storico…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> Text("Grafico non disponibile: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            visible.size > 1 -> {
                PortfolioFinancialChart(visible, chartStyle)
                Text("Periodo: \${visible.first().date} – \${visible.last().date} · \${visible.size} sedute", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRemove) { Text("Rimuovi") }
            TextButton(onClick = { onAnalyze(ticker) }) { Text("Scheda tecnica") }
        }
    }
}

private data class DisplayCandle(val date: String, val open: Double, val high: Double, val low: Double, val close: Double)

private fun displayCandles(source: List<Candle>, style: PortfolioChartStyle): List<DisplayCandle> {
    if (style != PortfolioChartStyle.HEIKIN_ASHI) {
        return source.map { DisplayCandle(it.date, it.open, it.high, it.low, it.close) }
    }
    var previousOpen: Double? = null
    var previousClose: Double? = null
    return source.map { candle ->
        val haClose = (candle.open + candle.high + candle.low + candle.close) / 4.0
        val haOpen = if (previousOpen == null || previousClose == null) {
            (candle.open + candle.close) / 2.0
        } else {
            (previousOpen + previousClose) / 2.0
        }
        val result = DisplayCandle(
            candle.date, haOpen,
            maxOf(candle.high, haOpen, haClose),
            minOf(candle.low, haOpen, haClose),
            haClose
        )
        previousOpen = haOpen
        previousClose = haClose
        result
    }
}

@Composable
private fun PortfolioFinancialChart(candles: List<Candle>, style: PortfolioChartStyle) {
    val plotted = displayCandles(candles, style)
    val values = plotted.flatMap { listOf(it.high, it.low) }
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val positive = Color(0xFF45C99A)
    val negative = Color(0xFFE57373)
    val lineColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            when (style) {
                PortfolioChartStyle.CANDLE -> "Candele giapponesi · verde rialzista / rosso ribassista"
                PortfolioChartStyle.HEIKIN_ASHI -> "Heikin-Ashi · valori OHLC trasformati"
                PortfolioChartStyle.OHLC -> "Barre OHLC · apertura, massimo, minimo e chiusura"
                PortfolioChartStyle.LINE -> "Linea delle chiusure"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth().height(156.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(54.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt((max + min) / 2.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                fun y(value: Double): Float = size.height - ((value - min) / range).toFloat() * size.height
                for (fraction in 0..4) {
                    val gy = size.height * fraction / 4f
                    drawLine(Color(0xFF354252), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                if (style == PortfolioChartStyle.LINE) {
                    val points = plotted.mapIndexed { index, candle ->
                        Offset(size.width * index / (plotted.size - 1).coerceAtLeast(1), y(candle.close))
                    }
                    points.zipWithNext().forEach { (p1, p2) ->
                        drawLine(lineColor, p1, p2, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                } else {
                    val step = size.width / plotted.size
                    val bodyWidth = (step * 0.62f).coerceIn(2.dp.toPx(), 12.dp.toPx())
                    plotted.forEachIndexed { index, candle ->
                        val x = step * (index + 0.5f)
                        val rising = candle.close >= candle.open
                        val color = if (rising) positive else negative
                        drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 1.2.dp.toPx())
                        if (style == PortfolioChartStyle.OHLC) {
                            drawLine(color, Offset(x - bodyWidth / 2f, y(candle.open)), Offset(x, y(candle.open)), strokeWidth = 1.5.dp.toPx())
                            drawLine(color, Offset(x, y(candle.close)), Offset(x + bodyWidth / 2f, y(candle.close)), strokeWidth = 1.5.dp.toPx())
                        } else {
                            val top = y(maxOf(candle.open, candle.close))
                            val bottom = y(minOf(candle.open, candle.close))
                            val height = (bottom - top).coerceAtLeast(1.dp.toPx())
                            drawRect(color, Offset(x - bodyWidth / 2f, top), androidx.compose.ui.geometry.Size(bodyWidth, height))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plotted.first().date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tempo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(plotted.last().date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Valore: prezzo nella valuta di quotazione", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun HistoryScreen(history: List<AnalysisRecord>, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Storico analisi") }
        if (history.isEmpty()) item { CardBlock { Text("Nessuna analisi salvata."); Text("Le analisi completate appariranno qui sul dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(history, key = { it.id }) { record ->
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${record.createdAt} · ${record.market} · ${record.level}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { onOpen(record.symbol) }) { Text("Apri") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(text: String) { CardBlock { SelectionContainer { Column { Text("Stato dei dati", fontWeight = FontWeight.SemiBold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable
private fun Disclaimer() {
    CardBlock {
        Text("Trasparenza e limiti", fontWeight = FontWeight.SemiBold)
        Text("Questa versione prova in sequenza Stooq, Yahoo Finance e Alpha Vantage (se configurato). Yahoo Finance utilizza un'interfaccia non ufficiale e non documentata. Sono dati storici giornalieri, non quotazioni real-time. Non fornisce order book, flussi buy/sell istituzionali, open interest o fondamentali certificati. I dati assenti sono mostrati come N/D.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Strumento informativo e didattico: non è consulenza finanziaria. Nessun ordine reale viene eseguito.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun fmt(value: Double): String = String.format(java.util.Locale.ITALY, "%.2f", value)
