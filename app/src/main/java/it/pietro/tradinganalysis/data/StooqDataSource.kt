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

/**
 * Stooq public daily CSV endpoint.
 * The response is validated explicitly so an HTML/error message is not mistaken for market data.
 */
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
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AnalisiTrading/0.1")
                setRequestProperty("Accept", "text/csv,text/plain,*/*")
            }

            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    error("Stooq ha risposto HTTP $code: ${preview(body)}")
                }
                parseCsv(body, symbol)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun normalizeSymbol(symbol: String): String {
        val cleaned = symbol.trim().lowercase(Locale.US)
        return when {
            cleaned.endsWith(".us") || cleaned.contains('.') -> cleaned
            else -> "$cleaned.us"
        }
    }

    private fun parseCsv(csv: String, requestedSymbol: String): List<Candle> {
        val normalizedBody = csv.removePrefix("\uFEFF").trim()
        if (normalizedBody.isBlank()) {
            error("Stooq ha restituito una risposta vuota per $requestedSymbol")
        }

        val lines = normalizedBody.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull().orEmpty()
        val expectedHeader = listOf("date", "open", "high", "low", "close")
        val actualHeader = header.split(',').map { it.trim().trim('"').lowercase(Locale.US) }
        if (actualHeader.size < 6 || actualHeader.take(5) != expectedHeader) {
            error("Risposta Stooq non riconosciuta per $requestedSymbol: ${preview(normalizedBody)}")
        }

        val candles = lines.drop(1).mapNotNull { line ->
            val fields = line.split(',').map { it.trim().trim('"') }
            if (fields.size < 6) return@mapNotNull null
            val numbers = fields.drop(1).take(5).map { it.toDoubleOrNull() ?: return@mapNotNull null }
            val (open, high, low, close, volume) = numbers
            if (listOf(open, high, low, close, volume).any { !it.isFinite() } || close <= 0.0) return@mapNotNull null
            if (high < low || open !in low..high || close !in low..high) return@mapNotNull null
            Candle(fields[0], open, high, low, close, volume.coerceAtLeast(0.0))
        }.sortedBy { it.date }

        if (candles.isEmpty()) {
            error("Stooq ha restituito l'intestazione CSV ma nessuna riga storica per $requestedSymbol. Risposta: ${preview(normalizedBody)}")
        }
        return candles
    }

    private fun preview(value: String): String =
        value.replace(Regex("\\s+"), " ").take(180).ifBlank { "(vuota)" }
}
