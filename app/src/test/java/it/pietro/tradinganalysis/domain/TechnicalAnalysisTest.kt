package it.pietro.tradinganalysis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TechnicalAnalysisTest {
    @Test fun returnsNullForEmptySeries() { assertNull(TechnicalAnalysis.calculate(emptyList())) }

    @Test fun computesLastCloseAndDailyChange() {
        val data = listOf(candle("2026-01-01", 100.0), candle("2026-01-02", 110.0))
        val result = TechnicalAnalysis.calculate(data)!!
        assertEquals(110.0, result.lastClose, 0.000001)
        assertEquals(10.0, result.changePercent!!, 0.000001)
    }

    @Test fun computesMovingAveragesOnlyWhenEnoughRowsExist() {
        val data = (1..50).map { candle("2026-01-${it.toString().padStart(2, '0')}", it.toDouble()) }
        val result = TechnicalAnalysis.calculate(data)!!
        assertEquals(40.5, result.sma20!!, 0.000001)
        assertEquals(25.5, result.sma50!!, 0.000001)
    }

    @Test fun rsiIsBoundedAndDefinedAfterFifteenCloses() {
        val rising = (1..20).map { candle("2026-02-${it.toString().padStart(2, '0')}", it.toDouble()) }
        val rsi = TechnicalAnalysis.calculate(rising)!!.rsi14
        assertNotNull(rsi)
        assertEquals(100.0, rsi!!, 0.000001)
    }

    @Test fun rsiIsZeroForStrictlyFallingSeries() {
        val falling = (1..20).map { candle("2026-03-${it.toString().padStart(2, '0')}", (21 - it).toDouble()) }
        assertEquals(0.0, TechnicalAnalysis.calculate(falling)!!.rsi14!!, 0.000001)
    }

    @Test fun maxDrawdownTracksDeclineFromPriorPeak() {
        val data = listOf(candle("2026-04-01", 100.0), candle("2026-04-02", 80.0), candle("2026-04-03", 90.0))
        assertEquals(-20.0, TechnicalAnalysis.calculate(data)!!.maxDrawdown!!, 0.000001)
    }


    @Test fun volumeRatioRequiresTwentyPriorSessions() {
        val short = (1..10).map { candle("2026-05-${it.toString().padStart(2, '0')}", it.toDouble()) }
        assertNull(TechnicalAnalysis.calculate(short)!!.volumeVs20DayAverage)
        val full = (1..21).map { index ->
            val base = candle("2026-06-${index.toString().padStart(2, '0')}", index.toDouble())
            if (index == 21) base.copy(volume = 2000.0) else base
        }
        assertEquals(2.0, TechnicalAnalysis.calculate(full)!!.volumeVs20DayAverage!!, 0.000001)
    }

    @Test fun computesLongMovingAverageAndExponentialAverages() {
        val data = (1..220).map { candle("2026-01-${it.toString().padStart(3, '0')}", it.toDouble()) }
        val result = TechnicalAnalysis.calculate(data)!!
        assertEquals(120.5, result.sma200!!, 0.000001)
        assertNotNull(result.ema12)
        assertNotNull(result.ema26)
        assertNotNull(result.macd)
        assertNotNull(result.macdSignal)
        assertEquals(true, result.macd!! > 0.0)
    }

    @Test fun computesBollingerBandsAndAtrWhenEnoughCandlesExist() {
        val data = (1..30).map { index ->
            val close = 100.0 + index
            Candle("2026-07-${index.toString().padStart(2, '0')}", close, close + 1.0, close - 1.0, close, 1000.0)
        }
        val result = TechnicalAnalysis.calculate(data)!!
        assertNotNull(result.bollingerUpper)
        assertNotNull(result.bollingerLower)
        assertEquals(2.0, result.atr14!!, 0.000001)
        assertNotNull(result.stochasticK14)
    }

    private fun candle(date: String, close: Double) = Candle(date, close, close, close, close, 1000.0)
}
