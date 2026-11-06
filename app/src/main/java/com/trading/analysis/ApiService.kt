package com.trading.analysis

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        chain.proceed(request)
    }
    .build()

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}?range=1mo&interval=1d")
    suspend fun getStockData(@Path("symbol") symbol: String): retrofit2.Response<ResponseBody>
}

object RetrofitClient {
    val api: YahooFinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://query1.finance.yahoo.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YahooFinanceApi::class.java)
    }

    suspend fun fetchMarketSummary(symbol: String): String = withContext(Dispatchers.IO) {
        try {
            val response = api.getStockData(symbol)
            if (response.isSuccessful && response.body() != null) {
                val jsonString = response.body()!!.string()
                // Estrae un estratto sintetico del JSON per l'analisi dell'IA
                if (jsonString.length > 1500) jsonString.take(1500) else jsonString
            } else {
                "Dati di mercato non disponibili per $symbol (Codice HTTP: ${response.code()})"
            }
        } catch (e: Exception) {
            "Impossibile recuperare i dati in tempo reale per $symbol: ${e.localizedMessage}"
        }
    }
}

enum class UserLevel(val label: String) {
    BASE("Base (Semplice & Diretto)"),
    ADVANCED("Avanzato (Indicatori Tecnici)"),
    EXPERT("Esperto (Order Flow & Volumi)")
}

class AnalysisEngine {

    suspend fun runAiAnalysis(
        apiKey: String,
        symbol: String,
        marketType: String,
        level: UserLevel,
        marketDataSummary: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "⚠️ Inserisci una chiave API Gemini valida nelle impostazioni dell'app per procedere."

        val prompt = """
            Sei un analista finanziario e trader professionista di altissimo livello. 
            Analizza il seguente asset finanziario:
            - Asset: $symbol
            - Mercato: $marketType
            - Livello Utente di Comprensione: ${level.label}
            - Dati di mercato/prezzo attuali: $marketDataSummary

            REQUISITI FONDAMENTALI DELLA RISPOSTA:
            1. Adatta rigorosamente il linguaggio, i termini tecnici e la profondità dell'analisi al livello (${level.name}) dell'utente.
            2. Fornisci un verdetto chiaro e motivato: ACQUISTARE, MANTENERE o VENDERE.
            3. Delinea 2 strategie operative pratiche, prive di fronzoli e orientate al risk management.
        """.trimIndent()

        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Errore: Nessuna risposta generata dall'Intelligenza Artificiale."
        } catch (e: Exception) {
            "Errore durante l'elaborazione dell'AI: ${e.localizedMessage}"
        }
    }
}