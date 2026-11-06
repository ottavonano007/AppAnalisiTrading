package com.trading.analysis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TradingAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingAppScreen() {
    var apiKey by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("BTC-USD") }
    var selectedMarket by remember { mutableStateOf("Cripto") }
    var selectedLevel by remember { mutableStateOf(UserLevel.BASE) }
    var useAi by remember { mutableStateOf(true) }
    var analysisResult by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val engine = remember { AnalysisEngine() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("App Analisi Trading", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key (Gratuita)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = symbol,
            onValueChange = { symbol = it },
            label = { Text("Asset (es. AAPL, BTC-USD, GC=F)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Seleziona Livello Utente:", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UserLevel.entries.forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                    label = { Text(level.name) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Elabora con Intelligenza Artificiale")
            Switch(checked = useAi, onCheckedChange = { useAi = it })
        }

        Button(
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    analysisResult = if (useAi) {
                        engine.runAiAnalysis(
                            apiKey = apiKey,
                            symbol = symbol,
                            marketType = selectedMarket,
                            level = selectedLevel,
                            marketDataSummary = "Prezzo stimato e volumi correnti per $symbol"
                        )
                    } else {
                        "Analisi Standard per $symbol completata con successo."
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Elaborazione in corso..." else "Genera Report Strategico")
        }

        if (analysisResult.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report Strategico", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(analysisResult, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
