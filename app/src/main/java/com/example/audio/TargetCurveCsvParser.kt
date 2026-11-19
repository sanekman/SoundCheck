package com.example.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.round

data class ParsedTargetCurve(
    val sourcePointCount: Int,
    val relativeDb31: FloatArray,
    val normalizedTo1kHz: Boolean
)

/**
 * Parses custom Target Curve CSV / TXT files (e.g., REW house curves, AutoEQ CSV,
 * Harman / Audiofrog custom CSV tables) and log-interpolates them onto the 31 ISO 1/3-octave bands.
 */
object TargetCurveCsvParser {

    fun parseCsv(content: String): ParsedTargetCurve? {
        val rawPoints = mutableListOf<Pair<Float, Float>>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#") || line.startsWith("*") || line.startsWith("//") || line.startsWith("\"")) {
                return@forEach
            }

            // Detect delimiter: semicolon (often paired with decimal comma) vs comma/tab/whitespace
            val tokens = if (line.contains(';')) {
                line.split(';').map { it.trim().replace(',', '.') }
            } else if (line.contains(',')) {
                line.split(',').map { it.trim() }
            } else {
                line.split(Regex("""\s+"""))
            }

            if (tokens.size >= 2) {
                val freq = tokens[0].toFloatOrNull()
                val db = tokens[1].toFloatOrNull()
                if (freq != null && db != null && freq in 10f..48000f && db in -60f..140f) {
                    rawPoints.add(freq to db)
                }
            }
        }

        if (rawPoints.size < 2) return null

        val sorted = rawPoints
            .distinctBy { it.first }
            .sortedBy { it.first }

        if (sorted.size < 2) return null

        // Interpolate onto all 31 ISO 1/3-octave center frequencies in log-frequency space
        val raw31 = FloatArray(31) { idx ->
            interpolateLogFreq(sorted, RtaBands.ISO_31_FREQS[idx])
        }

        // If user imported absolute SPL values (e.g., 75..85 dB) or un-centered values,
        // normalize relative to 1 kHz (band index 17) so target curve aligns cleanly with Auto-Level.
        val valAt1k = raw31[17]
        val shouldNormalize = abs(valAt1k) > 2.5f
        val relative31 = FloatArray(31) { idx ->
            val shifted = if (shouldNormalize) raw31[idx] - valAt1k else raw31[idx]
            val clamped = shifted.coerceIn(-24f, 24f)
            round(clamped * 10f) / 10f
        }

        return ParsedTargetCurve(
            sourcePointCount = sorted.size,
            relativeDb31 = relative31,
            normalizedTo1kHz = shouldNormalize
        )
    }

    private fun interpolateLogFreq(points: List<Pair<Float, Float>>, freqHz: Float): Float {
        if (freqHz <= points.first().first) return points.first().second
        if (freqHz >= points.last().first) return points.last().second

        var low = 0
        var high = points.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val mf = points[mid].first
            when {
                mf < freqHz -> low = mid + 1
                mf > freqHz -> high = mid - 1
                else -> return points[mid].second
            }
        }
        val i0 = high.coerceIn(0, points.lastIndex - 1)
        val i1 = (i0 + 1).coerceIn(1, points.lastIndex)
        val (f0, d0) = points[i0]
        val (f1, d1) = points[i1]
        val logF0 = log10(f0.toDouble())
        val logF1 = log10(f1.toDouble())
        val logF = log10(freqHz.toDouble())
        val t = if (logF1 > logF0) ((logF - logF0) / (logF1 - logF0)).toFloat().coerceIn(0f, 1f) else 0f
        return d0 + t * (d1 - d0)
    }
}
