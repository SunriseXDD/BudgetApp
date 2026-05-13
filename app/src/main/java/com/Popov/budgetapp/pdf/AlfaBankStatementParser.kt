package com.Popov.budgetapp.pdf

import java.util.Calendar
import java.util.Locale

data class ParsedStatementOperation(
    val bookingDateMillis: Long,
    val title: String,
    /** Отрицательное — списание (расход), положительное — зачисление (доход). */
    val signedAmountRub: Double,
)

object AlfaBankStatementParser {

    private val DATE_LINE = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(\S+)\s*(.*)$""")
    private val AMOUNT_LINE = Regex("""^(-?)([\d\s\u00A0]+),(\d{2})\s*RUR\s*$""")
    private val SAME_LINE = Regex(
        """^(\d{2}\.\d{2}\.\d{4})\s+(\S+)\s+(.*?)\s+(-?[\d\s\u00A0]+,\d{2})\s*RUR\s*$"""
    )
    private val PAGE_MARKER = Regex("""--\s*\d+\s+of\s+\d+\s*--""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<ParsedStatementOperation> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val start = findStartIndex(lines)
        val result = mutableListOf<ParsedStatementOperation>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (shouldSkip(line)) {
                i++
                continue
            }
            val same = SAME_LINE.matchEntire(line)
            if (same != null) {
                val dateStr = same.groupValues[1]
                val titleRest = same.groupValues[3].trim()
                val amountStr = same.groupValues[4].trim()
                val signed = parseInlineAmount(amountStr)
                if (!signed.isNaN() && signed != 0.0 && titleRest.isNotBlank()) {
                    result += ParsedStatementOperation(
                        bookingDateMillis = parseBookingDate(dateStr),
                        title = compressTitle(titleRest),
                        signedAmountRub = signed,
                    )
                }
                i++
                continue
            }
            val dateMatch = DATE_LINE.matchEntire(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.groupValues[1]
                val firstDesc = dateMatch.groupValues[3].trim()
                i++
                val desc = StringBuilder(firstDesc)
                var foundAmount = false
                while (i < lines.size) {
                    val l = lines[i]
                    if (shouldSkip(l)) {
                        i++
                        continue
                    }
                    if (DATE_LINE.matches(l) || SAME_LINE.matches(l)) {
                        break
                    }
                    val am = AMOUNT_LINE.matchEntire(l)
                    if (am != null) {
                        val signed = parseAmountMatch(am)
                        if (!signed.isNaN() && signed != 0.0) {
                            val title = compressTitle(desc.toString())
                            if (title.isNotBlank()) {
                                result += ParsedStatementOperation(
                                    bookingDateMillis = parseBookingDate(dateStr),
                                    title = title,
                                    signedAmountRub = signed,
                                )
                            }
                            foundAmount = true
                        }
                        i++
                        break
                    }
                    if (desc.isNotEmpty()) desc.append(' ')
                    desc.append(l)
                    i++
                }
                if (!foundAmount) {
                    // неполная запись — переходим к следующей строке
                    continue
                }
                continue
            }
            i++
        }
        return result
    }

    private fun findStartIndex(lines: List<String>): Int {
        val marker = lines.indexOfFirst { it.contains("Операции по счету", ignoreCase = true) }
        if (marker < 0) return 0
        var i = marker + 1
        while (i < lines.size && i < marker + 8) {
            val l = lines[i]
            if (DATE_LINE.matches(l) || SAME_LINE.matches(l)) return i
            i++
        }
        return (marker + 3).coerceAtMost(lines.size)
    }

    private fun shouldSkip(line: String): Boolean {
        if (line.startsWith("Страница", ignoreCase = true)) return true
        if (PAGE_MARKER.containsMatchIn(line)) return true
        if (line.equals("Дата проводки", ignoreCase = true)) return true
        if (line.startsWith("Т.Т.", ignoreCase = true) && line.length <= 6) return true
        if (line.startsWith("Входящий остаток", ignoreCase = true)) return true
        if (line.startsWith("Исходящий остаток", ignoreCase = true)) return true
        if (line.contains("Операции по счету", ignoreCase = true)) return true
        return false
    }

    private fun parseAmountMatch(m: MatchResult): Double {
        val sign = m.groupValues[1]
        val intPart = m.groupValues[2].replace(Regex("[\\s\u00A0]"), "")
        val frac = m.groupValues[3]
        val v = "$intPart.$frac".toDoubleOrNull() ?: return Double.NaN
        return if (sign == "-") -v else v
    }

    private fun parseInlineAmount(s: String): Double {
        val t = s.trim()
        val neg = t.startsWith("-")
        val body = if (neg) t.substring(1).trim() else t
        val parts = body.split(',')
        if (parts.size != 2) return Double.NaN
        val intPart = parts[0].replace(Regex("[\\s\u00A0]"), "")
        val frac = parts[1].take(2)
        val v = "$intPart.$frac".toDoubleOrNull() ?: return Double.NaN
        return if (neg) -v else v
    }

    private fun compressTitle(s: String): String {
        val t = s.replace(Regex("\\s+"), " ").trim()
        return if (t.length > 200) t.take(200) else t
    }

    private fun parseBookingDate(ddMmYyyy: String): Long {
        val parts = ddMmYyyy.split('.')
        if (parts.size != 3) return System.currentTimeMillis()
        val day = parts[0].toIntOrNull() ?: return System.currentTimeMillis()
        val month = parts[1].toIntOrNull() ?: return System.currentTimeMillis()
        val year = parts[2].toIntOrNull() ?: return System.currentTimeMillis()
        return Calendar.getInstance(Locale.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
