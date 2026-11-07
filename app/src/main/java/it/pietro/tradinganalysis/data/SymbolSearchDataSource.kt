package it.pietro.tradinganalysis.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SymbolSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String
)

/**
 * Yahoo Finance symbol search. This is an undocumented interface and may change.
 */
class SymbolSearchDataSource {
    suspend fun search(query: String): Result<List<SymbolSuggestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = query.trim()
            require(clean.length >= 2) { "Digita almeno due caratteri" }
            require(clean.length <= 60) { "Testo di ricerca troppo lungo" }
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://query1.finance.yahoo.com/v1/finance/search?q=$encoded&quotesCount=10&newsCount=0&enableFuzzyQuery=true")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 9_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 15) AnalisiTrading/0.1")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(code in 200..299) { "Ricerca ticker non disponibile (HTTP $code)" }
                val quotes = JSONObject(body).optJSONArray("quotes") ?: return@runCatching emptyList()
                val seen = mutableSetOf<String>()
                (0 until quotes.length()).mapNotNull { index ->
                    val item = quotes.optJSONObject(index) ?: return@mapNotNull null
                    val type = item.optString("quoteType")
                    if (type != "EQUITY" && type != "ETF") return@mapNotNull null
                    val ticker = item.optString("symbol").trim()
                    if (ticker.isBlank() || !seen.add(ticker.uppercase())) return@mapNotNull null
                    val name = item.optString("longname").takeIf { it.isNotBlank() }
                        ?: item.optString("shortname").takeIf { it.isNotBlank() }
                        ?: ticker
                    val exchange = item.optString("exchDisp").takeIf { it.isNotBlank() }
                        ?: item.optString("exchange").takeIf { it.isNotBlank() }
                        ?: "Mercato non specificato"
                    SymbolSuggestion(ticker, name, exchange, if (type == "ETF") "ETF" else "Azione")
                }.take(8)
            } finally {
                connection.disconnect()
            }
        }
    }
}
