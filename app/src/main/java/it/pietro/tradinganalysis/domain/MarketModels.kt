package it.pietro.tradinganalysis.domain

enum class MarketType(val label: String) {
    STOCK("Azionario"), CRYPTO("Criptovalute"), COMMODITY("Materie prime"), BOND("Obbligazionario"), ETF("ETF"), INDEX("Indici"), FX("Valute")
}

enum class AnalysisLevel(val label: String) { BASE("Base"), ADVANCED("Avanzato"), EXPERT("Esperto") }
enum class AnalysisMode(val label: String) { DATA_ONLY("Solo dati"), AI("AI esterna (non configurata)") }

data class Candle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class MarketInstrument(val symbol: String, val name: String, val market: MarketType)

data class TechnicalSnapshot(
    val lastClose: Double,
    val changePercent: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val rsi14: Double?,
    val annualizedVolatility: Double?,
    val volumeVs20DayAverage: Double?,
    val maxDrawdown: Double?,
    val observations: Int,
    val lastDate: String
)

data class AnalysisRecord(
    val id: Long,
    val symbol: String,
    val market: String,
    val level: String,
    val createdAt: String,
    val summary: String
)
