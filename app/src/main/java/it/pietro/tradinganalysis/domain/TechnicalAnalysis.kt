package it.pietro.tradinganalysis.domain

import kotlin.math.pow
import kotlin.math.sqrt

object TechnicalAnalysis {
    fun calculate(candles: List<Candle>): TechnicalSnapshot? {
        val data = candles.sortedBy { it.date }
        if (data.isEmpty()) return null
        val closes = data.map { it.close }
        val last = closes.last()
        val previous = closes.getOrNull(closes.lastIndex - 1)
        val returns = closes.zipWithNext { a, b -> if (a != 0.0) b / a - 1.0 else 0.0 }
        val volatility = if (returns.size >= 2) {
            val mean = returns.average()
            val variance = returns.sumOf { (it - mean).pow(2) } / (returns.size - 1)
            sqrt(variance) * sqrt(252.0) * 100.0
        } else null
        var runningPeak = Double.NEGATIVE_INFINITY
        val drawdowns = closes.map { value ->
            runningPeak = maxOf(runningPeak, value)
            if (runningPeak > 0) (value / runningPeak - 1.0) * 100.0 else 0.0
        }
        val volumes = data.map { it.volume }
        val previousVolumes = volumes.dropLast(1).takeLast(20)
        val averageVolume = previousVolumes.takeIf { it.size == 20 }?.average()
        val volumeRatio = if (averageVolume != null && averageVolume > 0 && volumes.last() > 0) volumes.last() / averageVolume else null
        val ema12Series = emaSeries(closes, 12)
        val ema26Series = emaSeries(closes, 26)
        val macdSeries = closes.indices.map { i ->
            val fast = ema12Series.getOrNull(i)
            val slow = ema26Series.getOrNull(i)
            if (fast != null && slow != null) fast - slow else null
        }
        val validMacd = macdSeries.mapNotNull { it }
        val signal = emaSeries(validMacd, 9).lastOrNull()
        val bands = bollinger(closes)
        val atr = averageTrueRange(data)
        val stochastic = stochasticK(data)
        return TechnicalSnapshot(
            lastClose = last,
            changePercent = previous?.takeIf { it != 0.0 }?.let { (last / it - 1.0) * 100.0 },
            sma20 = averageLast(closes, 20),
            sma50 = averageLast(closes, 50),
            sma200 = averageLast(closes, 200),
            ema12 = ema12Series.lastOrNull(),
            ema26 = ema26Series.lastOrNull(),
            macd = validMacd.lastOrNull(),
            macdSignal = signal,
            bollingerUpper = bands?.first,
            bollingerLower = bands?.second,
            atr14 = atr,
            stochasticK14 = stochastic,
            rsi14 = calculateRsi(closes),
            annualizedVolatility = volatility,
            volumeVs20DayAverage = volumeRatio,
            maxDrawdown = drawdowns.minOrNull(),
            observations = data.size,
            lastDate = data.last().date
        )
    }

    private fun averageLast(values: List<Double>, period: Int): Double? =
        values.takeLast(period).takeIf { it.size == period }?.average()

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val multiplier = 2.0 / (period + 1.0)
        val output = ArrayList<Double>(values.size - period + 1)
        var ema = values.take(period).average()
        output += ema
        values.drop(period).forEach { value ->
            ema = (value - ema) * multiplier + ema
            output += ema
        }
        return output
    }

    private fun bollinger(closes: List<Double>): Pair<Double, Double>? {
        val window = closes.takeLast(20).takeIf { it.size == 20 } ?: return null
        val mean = window.average()
        val deviation = sqrt(window.sumOf { (it - mean).pow(2) } / window.size)
        return (mean + 2.0 * deviation) to (mean - 2.0 * deviation)
    }

    private fun averageTrueRange(data: List<Candle>): Double? {
        if (data.size < 15) return null
        val ranges = data.zipWithNext().map { (previous, current) ->
            maxOf(
                current.high - current.low,
                kotlin.math.abs(current.high - previous.close),
                kotlin.math.abs(current.low - previous.close)
            )
        }
        return ranges.takeLast(14).takeIf { it.size == 14 }?.average()
    }

    private fun stochasticK(data: List<Candle>): Double? {
        val window = data.takeLast(14).takeIf { it.size == 14 } ?: return null
        val highest = window.maxOf { it.high }
        val lowest = window.minOf { it.low }
        return if (highest > lowest) (window.last().close - lowest) / (highest - lowest) * 100.0 else null
    }

    private fun calculateRsi(closes: List<Double>): Double? {
        if (closes.size < 15) return null
        val changes = closes.zipWithNext { a, b -> b - a }
        var averageGain = changes.take(14).filter { it > 0 }.sum() / 14.0
        var averageLoss = -changes.take(14).filter { it < 0 }.sum() / 14.0
        changes.drop(14).forEach { change ->
            val gain = change.coerceAtLeast(0.0)
            val loss = (-change).coerceAtLeast(0.0)
            averageGain = (averageGain * 13.0 + gain) / 14.0
            averageLoss = (averageLoss * 13.0 + loss) / 14.0
        }
        return when {
            averageLoss == 0.0 && averageGain == 0.0 -> 50.0
            averageLoss == 0.0 -> 100.0
            else -> 100.0 - (100.0 / (1.0 + averageGain / averageLoss))
        }
    }
}