package com.Popov.budgetapp.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SberbankStatementParserTest {

    @Test
    fun expenseAndBalance() {
        val text =
            "13.03.2026 16:13 Покупка в магазине 1 214,37 45 972,47"
        val r = SberbankStatementParser.parse(text)
        assertEquals(1, r.size)
        assertTrue(r[0].signedAmountRub < 0)
        assertEquals(-1214.37, r[0].signedAmountRub, 0.01)
    }

    @Test
    fun incomeWithPlus() {
        val text = "10.03.2026 11:56 Зачисление +6 954,43 47 805,08"
        val r = SberbankStatementParser.parse(text)
        assertEquals(1, r.size)
        assertTrue(r[0].signedAmountRub > 0)
        assertEquals(6954.43, r[0].signedAmountRub, 0.01)
    }

    @Test
    fun continuesWithAuthLine() {
        val text = """
            12.03.2026 08:39 Перевод +2 879,23 38 462,05
            12.03.2026 945860 SBERBANK ONL@IN. Описание
        """.trimIndent()
        val r = SberbankStatementParser.parse(text)
        assertEquals(1, r.size)
        assertTrue(r[0].title.contains("SBERBANK", ignoreCase = true))
    }
}
