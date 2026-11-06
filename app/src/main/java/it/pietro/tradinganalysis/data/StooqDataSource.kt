package it.pietro.tradinganalysis.data

import it.pietro.tradinganalysis.domain.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Contract for replaceable market-data providers. */
interface MarketDataSource {
    suspend fun loadDaily(symbol: String): Result<List<Candle>>
}

/** Stooq public daily CSV endpoint. Availability, symbols and terms are controlled by Stooq. */
class StooqDataSource : MarketDataSource {
    override suspend fun loadDaily(symbol: String): Result<List<Candle>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeSymbol(symbol)
            require(normalized.matches(Regex("[a-z0-9.]{1,24}"))) { "Simbolo non valido" }
            val url = URL("https://stooq.com/q/d/l/?s=$normalized&i=d")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "AnalisiTradingAndroid/0.1")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Fonte dati non disponibile (HTTP $code)")
                val csv = connection.inputStream.bufferedReader().use { it.readText() }
                parseCsv(csv).also { if (it.isEmpty()) error("Nessun dato restituito per $symbol") }
            } finally { connection.disconnect() }
        }
    }

    private fun normalizeSymbol(symbol: String): String {
        val cleaned = symbol.trim().lowercase(Locale.US)
        return when {
            cleaned.endsWith(".us") || cleaned.contains('.') -> cleaned
            else -> "$cleaned.us"
        }
    }

    private fun parseCsv(csv: String): List<Candle> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2 || !lines.first().startsWith("Date,Open,High,Low,Close", ignoreCase = true)) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val fields = line.split(',')
            if (fields.size < 6) return@mapNotNull null
            val values = fields.drop(1).take(5).map { it.toDoubleOrNull() ?: return@mapNotNull null }
            val (open, high, low, close, volume) = values
            if (listOf(open, high, low, close, volume).any { !it.isFinite() } || close <= 0.0) return@mapNotNull null
            if (high < low || open !in low..high || close !in low..high) return@mapNotNull null
            Candle(fields[0], open, high, low, close, volume.coerceAtLeast(0.0))
        }.sortedBy { it.date }
    }
}
