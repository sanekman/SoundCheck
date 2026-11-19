package com.example.audio

import kotlin.math.log10

data class CalibrationPoint(
    val freqHz: Float,
    val offsetDb: Float
)

data class ParsedCalibration(
    val sensitivityOffsetDb: Float,
    val points: List<CalibrationPoint>
)

object CalibrationParser {

    private val sensFactorRegex = Regex(
        pattern = """Sens\s*Factor\s*=\s*([+-]?\d+(?:\.\d+)?)\s*dB""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Parses standard UMIK-1 / Dayton Audio EMM-6 / iMM-6 / REW .cal / .txt files.
     * Supports space, tab, comma, or semicolon delimiters and ignores header comments.
     */
    fun parse(content: String): ParsedCalibration {
        var sensOffset = 0f
        val rawPoints = mutableListOf<CalibrationPoint>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            sensFactorRegex.find(line)?.let { match ->
                match.groupValues.getOrNull(1)?.toFloatOrNull()?.let { parsed ->
                    sensOffset = parsed
                }
            }

            if (line.startsWith("*") || line.startsWith("#") || line.startsWith("//") || line.startsWith("\"")) {
                return@forEach
            }

            val tokens = line.split(Regex("""[\s,;]+"""))
            if (tokens.size >= 2) {
                val freq = tokens[0].replace(',', '.').toFloatOrNull()
                val db = tokens[1].replace(',', '.').toFloatOrNull()
                if (freq != null && db != null && freq in 5f..48000f && db in -40f..40f) {
                    rawPoints.add(CalibrationPoint(freq, db))
                }
            }
        }

        val sorted = rawPoints
            .distinctBy { it.freqHz }
            .sortedBy { it.freqHz }

        return ParsedCalibration(
            sensitivityOffsetDb = sensOffset,
            points = sorted
        )
    }

    /**
     * Interpolates calibration correction (dB) at [freqHz] in log-frequency space.
     * Standard mic calibration files represent mic response error; we subtract mic error
     * (or add inverse correction) so measured SPL reflects true acoustic level.
     */
    fun interpolateCorrectionDb(points: List<CalibrationPoint>, freqHz: Float): Float {
        if (points.isEmpty()) return 0f
        if (points.size == 1) return -points.first().offsetDb
        if (freqHz <= points.first().freqHz) return -points.first().offsetDb
        if (freqHz >= points.last().freqHz) return -points.last().offsetDb

        var low = 0
        var high = points.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midFreq = points[mid].freqHz
            when {
                midFreq < freqHz -> low = mid + 1
                midFreq > freqHz -> high = mid - 1
                else -> return -points[mid].offsetDb
            }
        }
        val i0 = high.coerceIn(0, points.lastIndex - 1)
        val i1 = (i0 + 1).coerceIn(1, points.lastIndex)
        val p0 = points[i0]
        val p1 = points[i1]
        val logF0 = log10(p0.freqHz.toDouble())
        val logF1 = log10(p1.freqHz.toDouble())
        val logF = log10(freqHz.toDouble())
        val t = if (logF1 > logF0) ((logF - logF0) / (logF1 - logF0)).toFloat().coerceIn(0f, 1f) else 0f
        val micResponseDb = p0.offsetDb + t * (p1.offsetDb - p0.offsetDb)
        return -micResponseDb
    }

    fun encodePoints(points: List<CalibrationPoint>): String {
        return points.joinToString(";") { "${it.freqHz}:${it.offsetDb}" }
    }

    fun decodePoints(encoded: String): List<CalibrationPoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(";").mapNotNull { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val f = parts[0].toFloatOrNull()
                val d = parts[1].toFloatOrNull()
                if (f != null && d != null) CalibrationPoint(f, d) else null
            } else null
        }
    }
}
