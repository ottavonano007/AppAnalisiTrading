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
        return TechnicalSnapshot(
            lastClose = last,
            changePercent = previous?.takeIf { it != 0.0 }?.let { (last / it - 1.0) * 100.0 },
            sma20 = closes.takeLast(20).takeIf { it.size == 20 }?.average(),
            sma50 = closes.takeLast(50).takeIf { it.size == 50 }?.average(),
            rsi14 = calculateRsi(closes),
            annualizedVolatility = volatility,
            volumeVs20DayAverage = volumeRatio,
            maxDrawdown = drawdowns.minOrNull(),
            observations = data.size,
            lastDate = data.last().date
        )
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