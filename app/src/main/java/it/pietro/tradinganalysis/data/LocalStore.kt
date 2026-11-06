package it.pietro.tradinganalysis.data

import android.content.Context
import it.pietro.tradinganalysis.domain.AnalysisRecord
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("trading_analysis_local", Context.MODE_PRIVATE)

    fun portfolios(): Map<String, List<String>> {
        val raw = prefs.getString("portfolios", null) ?: return mapOf("Principale" to emptyList())
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { name ->
                val array = json.optJSONArray(name) ?: JSONArray()
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }
        }.getOrDefault(mapOf("Principale" to emptyList()))
    }

    fun savePortfolios(value: Map<String, List<String>>) {
        val json = JSONObject()
        value.forEach { (name, symbols) -> json.put(name, JSONArray(symbols.distinct())) }
        prefs.edit().putString("portfolios", json.toString()).apply()
    }

    fun history(): List<AnalysisRecord> {
        val raw = prefs.getString("history", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                AnalysisRecord(item.optLong("id"), item.optString("symbol"), item.optString("market"),
                    item.optString("level"), item.optString("createdAt"), item.optString("summary"))
            }.sortedByDescending { it.id }
        }.getOrDefault(emptyList())
    }

    fun addHistory(record: AnalysisRecord) {
        val records = (listOf(record) + history()).take(100)
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("symbol", item.symbol); put("market", item.market)
                put("level", item.level); put("createdAt", item.createdAt); put("summary", item.summary)
            })
        }
        prefs.edit().putString("history", array.toString()).apply()
    }
}
