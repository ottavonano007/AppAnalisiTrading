package it.pietro.tradinganalysis.data

import it.pietro.tradinganalysis.domain.Candle

/**
 * Tries providers in order. Each successful result keeps its real provider attribution.
 * Failure details are retained in the final error for diagnostics.
 */
class FallbackMarketDataSource(
    private val providers: List<MarketDataSource>
) : MarketDataSource {
    override suspend fun loadDaily(symbol: String): Result<MarketDataResult> {
        val failures = mutableListOf<String>()
        for (provider in providers) {
            val result = provider.loadDaily(symbol)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                if (data.candles.isNotEmpty()) return Result.success(data)
                failures += "${data.provider}: nessun dato valido"
            } else {
                failures += result.exceptionOrNull()?.message ?: "errore non specificato"
            }
        }
        return Result.failure(IllegalStateException(failures.joinToString(" · ").ifBlank { "Nessuna fonte dati disponibile" }))
    }
}
