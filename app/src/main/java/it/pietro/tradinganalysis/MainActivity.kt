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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.pietro.tradinganalysis.data.LocalStore
import it.pietro.tradinganalysis.data.StooqDataSource
import it.pietro.tradinganalysis.domain.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore(applicationContext)
        setContent { TradingApp(store) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradingApp(store: LocalStore) {
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
        val scope = rememberCoroutineScope()
        val source = remember { StooqDataSource() }

        fun analyze(target: String = symbol) {
            val clean = target.trim().uppercase()
            if (loading) return
            if (clean.isBlank()) { status = "Inserisci un simbolo valido."; return }
            if (market != MarketType.STOCK) {
                snapshot = null; candles = emptyList(); analyzedSymbol = ""
                status = "Feed non ancora integrato per ${market.label}. In questa versione il connettore Stooq è utilizzato solo per titoli azionari USA; nessun dato alternativo viene simulato."
                return
            }
            loading = true
            status = "Recupero dati giornalieri da Stooq…"
            scope.launch {
                val result = source.loadDaily(clean)
                result.onSuccess { values ->
                    val computed = TechnicalAnalysis.calculate(values)
                    candles = values
                    snapshot = computed
                    analyzedSymbol = clean
                    status = if (computed != null) "Dati ricevuti. Ultima sessione: ${computed.lastDate}." else "Dati insufficienti per l'analisi."
                    if (computed != null) {
                        val summary = "Chiusura ${fmt(computed.lastClose)} · ${computed.observations} osservazioni · RSI ${computed.rsi14?.let { fmt(it) } ?: "n/d"}"
                        val record = AnalysisRecord(System.currentTimeMillis(), clean, market.label, level.label,
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
                        addToPortfolio = {
                            val updated = portfolios.toMutableMap()
                            updated[selectedPortfolio] = (updated[selectedPortfolio].orEmpty() + symbol.trim().uppercase()).distinct()
                            portfolios = updated; store.savePortfolios(updated)
                            status = "${symbol.uppercase()} aggiunto a $selectedPortfolio."
                        }
                    )
                    1 -> PortfolioScreen(portfolios, selectedPortfolio, { selectedPortfolio = it },
                        onCreate = { showPortfolioDialog = true }, onRemove = { name, ticker ->
                            val updated = portfolios.toMutableMap()
                            updated[name] = updated[name].orEmpty() - ticker
                            portfolios = updated; store.savePortfolios(updated)
                        }, onAnalyze = { symbol = it; market = MarketType.STOCK; tab = 0; analyze(it) })
                    2 -> AnalysisScreen(analyzedSymbol, snapshot, candles, level, { level = it }, mode, { mode = it }, status, ::analyze)
                    else -> HistoryScreen(history, onOpen = { symbol = it; market = MarketType.STOCK; tab = 0; analyze(it) })
                }
            }
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
    onAnalyze: (String) -> Unit, addToPortfolio: () -> Unit
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
                OutlinedTextField(value = symbol, onValueChange = onSymbol, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ticker / simbolo") }, placeholder = { Text("Es. AAPL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                Text("Fonte attuale: Stooq · copertura iniziale: titoli azionari USA · dati giornalieri", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Ultima data: ${s.lastDate}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Text("${fmt(s.lastClose)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (candles.size > 1) PriceLine(candles.takeLast(60).map { it.close })
        val metrics = listOf(
            "Variazione" to s.changePercent?.let { "${fmt(it)}%" },
            "RSI (14)" to s.rsi14?.let(::fmt),
            "Media 20" to s.sma20?.let(::fmt),
            "Media 50" to s.sma50?.let(::fmt),
            "Volatilità ann." to s.annualizedVolatility?.let { "${fmt(it)}%" },
            "Volume / media 20g" to s.volumeVs20DayAverage?.let { "${fmt(it)}x" },
            "Max drawdown" to s.maxDrawdown?.let { "${fmt(it)}%" },
            "Osservazioni" to s.observations.toString()
        )
        MetricGrid(when (level) {
            AnalysisLevel.BASE -> metrics.take(3)
            AnalysisLevel.ADVANCED -> metrics.take(6)
            AnalysisLevel.EXPERT -> metrics
        })
        Text("Indicatori descrittivi, non segnali automatici di acquisto o vendita.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
private fun PriceLine(values: List<Double>) {
    if (values.size < 2) return
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp)) {
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val points = values.mapIndexed { index, value ->
            Offset(x = size.width * index / (values.size - 1), y = size.height - ((value - min) / range).toFloat() * size.height)
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round) }
    }
}

@Composable
private fun PortfolioScreen(portfolios: Map<String, List<String>>, selected: String, onSelect: (String) -> Unit,
                            onCreate: () -> Unit, onRemove: (String, String) -> Unit, onAnalyze: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("I tuoi portafogli"); Spacer(Modifier.weight(1f)); Button(onClick = onCreate) { Text("+ Nuovo") } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { portfolios.keys.forEach { name -> FilterChip(selected = selected == name, onClick = { onSelect(name) }, label = { Text(name) }) } } }
        val symbols = portfolios[selected].orEmpty()
        if (symbols.isEmpty()) item { CardBlock { Text("Portafoglio vuoto", fontWeight = FontWeight.SemiBold); Text("Aggiungi strumenti dalla sezione Mercati per creare il tuo elenco di osservazione.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(symbols) { ticker ->
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(ticker, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("Dato non aggiornato finché non avvii l'analisi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onAnalyze(ticker) }) { Text("Analizza") }
                    TextButton(onClick = { onRemove(selected, ticker) }) { Text("Rimuovi") }
                }
            }
        }
        item { Disclaimer() }
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
private fun StatusCard(text: String) { CardBlock { Text("Stato dei dati", fontWeight = FontWeight.SemiBold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun Disclaimer() {
    CardBlock {
        Text("Trasparenza e limiti", fontWeight = FontWeight.SemiBold)
        Text("Questa versione usa dati storici giornalieri pubblici Stooq, quando disponibili. Non fornisce quotazioni real-time, order book, flussi buy/sell istituzionali, open interest o fondamentali certificati. I dati assenti sono mostrati come N/D.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
