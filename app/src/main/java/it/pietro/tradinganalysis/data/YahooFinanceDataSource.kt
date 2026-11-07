package it.pietro.tradinganalysis.data

import it.pietro.tradinganalysis.domain.Candle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Yahoo Finance chart endpoint. This is an undocumented interface, not an official
 * public API; keep the provider label visible and treat schema changes as normal failures.
 */
class YahooFinanceDataSource : MarketDataSource {
    override suspend fun loadDaily(symbol: String): Result<MarketDataResult> = withContext(Dispatchers.IO) {
        runCatching {
            val ticker = symbol.trim().uppercase()
            require(ticker.matches(Regex("[A-Z0-9.^=_-]{1,24}"))) { "Simbolo non valido" }
            val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$ticker?range=1y&interval=1d&events=div%2Csplits")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 18_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 15) AnalisiTrading/0.1")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(code in 200..299) { "Yahoo Finance HTTP $code" }
                val root = JSONObject(body).getJSONObject("chart")
                val error = root.optJSONObject("error")
                check(error == null || error.isNull("code")) {
                    error?.optString("description") ?: "Risposta Yahoo Finance non valida"
                }
                val result = root.getJSONArray("result").optJSONObject(0)
                    ?: error("Yahoo Finance non ha restituito dati per $ticker")
                val timestamps = result.getJSONArray("timestamp")
                val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                val opens = quote.getJSONArray("open")
                val highs = quote.getJSONArray("high")
                val lows = quote.getJSONArray("low")
                val closes = quote.getJSONArray("close")
                val volumes = quote.optJSONArray("volume")
                val formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
                val candles = (0 until timestamps.length()).mapNotNull { i ->
                    if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) return@mapNotNull null
                    val open = opens.optDouble(i, Double.NaN)
                    val high = highs.optDouble(i, Double.NaN)
                    val low = lows.optDouble(i, Double.NaN)
                    val close = closes.optDouble(i, Double.NaN)
                    val volume = volumes?.optDouble(i, 0.0) ?: 0.0
                    if (listOf(open, high, low, close, volume).any { !it.isFinite() } ||
                        close <= 0.0 || high < low || open !in low..high || close !in low..high) return@mapNotNull null
                    Candle(formatter.format(Instant.ofEpochSecond(timestamps.getLong(i))), open, high, low, close, volume.coerceAtLeast(0.0))
                }.sortedBy { it.date }
                check(candles.isNotEmpty()) { "Yahoo Finance non ha restituito candele valide per $ticker" }
                MarketDataResult(candles, "Yahoo Finance", "Fonte non ufficiale: interfaccia non documentata")
            } finally {
                connection.disconnect()
            }
        }
    }
}
