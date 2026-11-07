package it.pietro.tradinganalysis.data

import it.pietro.tradinganalysis.domain.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Alpha Vantage official daily time-series API. Free access requires a user-supplied API key. */
class AlphaVantageDataSource(private val apiKey: String) : MarketDataSource {
    override suspend fun loadDaily(symbol: String): Result<MarketDataResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Chiave Alpha Vantage non configurata" }
            val ticker = symbol.trim().uppercase(Locale.US)
            require(ticker.matches(Regex("[A-Z0-9.^=_-]{1,24}"))) { "Simbolo non valido" }
            val encodedSymbol = URLEncoder.encode(ticker, "UTF-8")
            val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
            val url = URL("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=$encodedSymbol&outputsize=compact&apikey=$encodedKey")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 18_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AnalisiTrading/0.1 (Android)")
            }
            try {
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(code in 200..299) { "Alpha Vantage HTTP $code" }
                val json = JSONObject(body)
                val message = listOf("Error Message", "Note", "Information")
                    .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                check(message == null) { "Alpha Vantage: $message" }
                val series = json.optJSONObject("Time Series (Daily)")
                    ?: error("Alpha Vantage non ha restituito la serie giornaliera. Verifica simbolo, chiave e limiti del servizio.")
                val candles = series.keys().asSequence().mapNotNull { date ->
                    val day = series.optJSONObject(date) ?: return@mapNotNull null
                    val open = day.optString("1. open").toDoubleOrNull() ?: return@mapNotNull null
                    val high = day.optString("2. high").toDoubleOrNull() ?: return@mapNotNull null
                    val low = day.optString("3. low").toDoubleOrNull() ?: return@mapNotNull null
                    val close = day.optString("4. close").toDoubleOrNull() ?: return@mapNotNull null
                    val volume = day.optString("5. volume").toDoubleOrNull() ?: 0.0
                    if (listOf(open, high, low, close, volume).any { !it.isFinite() } ||
                        close <= 0.0 || high < low || open !in low..high || close !in low..high) return@mapNotNull null
                    Candle(date, open, high, low, close, volume.coerceAtLeast(0.0))
                }.sortedBy { it.date }
                check(candles.isNotEmpty()) { "Alpha Vantage ha restituito una serie vuota o non valida" }
                MarketDataResult(candles, "Alpha Vantage")
            } finally {
                connection.disconnect()
            }
        }
    }
}
