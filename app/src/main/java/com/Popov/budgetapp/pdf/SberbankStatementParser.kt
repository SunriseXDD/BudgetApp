package com.Popov.budgetapp.pdf

import java.util.Calendar
import java.util.Locale

/**
 * Выписка по счёту дебетовой карты Сбербанк (PDF из СберБанк Онлайн).
 *
 * Строка операции: дата, время (ЧЧ:ММ), описание, сумма проводки, остаток:
 * `13.03.2026 16:13 Описание … 1 214,37 45 972,47`
 * Зачисления с префиксом «+». Дальше без времени — продолжение (код авторизации 6 цифр).
 */
object SberbankStatementParser {

    private val PRIMARY_HEAD = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2})\s+(.*)$""")

    private val AMOUNT_TOKEN = Regex("""[+-]?(?:[\d\s\u00A0\u202f])+,\d{2}""")

    private val CONT_DATE_AUTH = Regex(
        """^(\d{2}\.\d{2}\.\d{4})\s+(\d{6})\s+(.*)$""",
    )

    private val CARD_MASK_LINE = Regex("""^\*{4}\d{4}$""")

    fun parse(text: String): List<ParsedStatementOperation> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<ParsedStatementOperation>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (shouldSkip(line)) {
                i++
                continue
            }
            val primary = parsePrimaryLine(line) ?: run {
                i++
                continue
            }
            i++
            val desc = StringBuilder(primary.descFirst)
            while (i < lines.size) {
                val l = lines[i]
                if (shouldSkip(l)) {
                    i++
                    continue
                }
                if (parsePrimaryLine(l) != null) {
                    break
                }
                when {
                    CONT_DATE_AUTH.matches(l) -> {
                        desc.append(' ').append(CONT_DATE_AUTH.matchEntire(l)!!.groupValues[3].trim())
                        i++
                    }
                    CARD_MASK_LINE.matches(l.trim()) -> i++
                    looksLikeFooter(l) -> break
                    else -> break
                }
            }
            val title = compressTitle(desc.toString())
            if (title.isNotBlank()) {
                out += ParsedStatementOperation(
                    bookingDateMillis = parseBookingDateTime(primary.dateStr, primary.timeStr),
                    title = title,
                    signedAmountRub = primary.signedAmountRub,
                )
            }
        }
        return out
    }

    private data class PrimaryLine(
        val dateStr: String,
        val timeStr: String,
        val descFirst: String,
        val signedAmountRub: Double,
    )

    private fun parsePrimaryLine(line: String): PrimaryLine? {
        val amounts = extractTrailingOperationAndBalance(line) ?: return null
        val opStr = amounts.first
        val balStr = amounts.second
        val balanceStart = line.lastIndexOf(balStr)
        if (balanceStart < 0) return null
        val beforeBalance = line.substring(0, balanceStart).trimEnd()
        val opStart = beforeBalance.lastIndexOf(opStr)
        if (opStart < 0) return null
        val headPart = beforeBalance.substring(0, opStart).trimEnd()
        val m = PRIMARY_HEAD.matchEntire(headPart) ?: return null
        val dateStr = m.groupValues[1]
        val timeStr = m.groupValues[2]
        val descFirst = m.groupValues[3].trim()
        val signed = signedAmountFromOperationToken(opStr)
        if (signed.isNaN() || signed == 0.0) return null
        return PrimaryLine(dateStr, timeStr, descFirst, signed)
    }

    private fun extractTrailingOperationAndBalance(line: String): Pair<String, String>? {
        val matches = AMOUNT_TOKEN.findAll(line).toList()
        if (matches.size < 2) return null
        return matches[matches.size - 2].value.trim() to matches.last().value.trim()
    }

    private fun signedAmountFromOperationToken(token: String): Double {
        val v = parseEuroAmountUnsigned(token) ?: return Double.NaN
        val t = token.trim()
        return when {
            t.startsWith("+") -> v
            t.startsWith("-") -> -v
            else -> -v
        }
    }

    private fun parseEuroAmountUnsigned(s: String): Double? {
        val t = s.trim().removePrefix("+").removePrefix("-").trim()
        val parts = t.split(',')
        if (parts.size != 2) return null
        val intPart = parts[0].replace(Regex("""[\s\u00A0\u202f]"""), "")
        val frac = parts[1].take(2)
        return "$intPart.$frac".toDoubleOrNull()
    }

    private fun compressTitle(s: String): String {
        val t = s.replace(Regex("\\s+"), " ").trim()
        return if (t.length > 200) t.take(200) else t
    }

    private fun parseBookingDateTime(dateDdMmYyyy: String, timeHhMm: String): Long {
        val dp = dateDdMmYyyy.split('.')
        val tp = timeHhMm.split(':')
        if (dp.size != 3 || tp.size != 2) return System.currentTimeMillis()
        val day = dp[0].toIntOrNull() ?: return System.currentTimeMillis()
        val month = dp[1].toIntOrNull() ?: return System.currentTimeMillis()
        val year = dp[2].toIntOrNull() ?: return System.currentTimeMillis()
        val hour = tp[0].toIntOrNull() ?: 12
        val minute = tp[1].toIntOrNull() ?: 0
        return Calendar.getInstance(Locale.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun shouldSkip(line: String): Boolean {
        if (line.startsWith("Страница", ignoreCase = true)) return true
        if (CARD_MASK_LINE.matches(line.trim())) return true
        if (looksLikeFooter(line)) return true
        return false
    }

    private fun looksLikeFooter(line: String): Boolean {
        if (line.contains("Информация на ", ignoreCase = true)) return true
        if (line.contains("из ", ignoreCase = true) && line.contains("лист", ignoreCase = true)) return true
        if (line.matches(Regex("""^\d{32}$"""))) return true
        if (line.matches(Regex("""^\d{1,2}\.\d{1,2}\.\d{4}\s+-\s+\d{1,2}\.\d{1,2}\.\d{4}"""))) return true
        if (line.contains("по запросу в офисе", ignoreCase = true)) return true
        return false
    }
}
