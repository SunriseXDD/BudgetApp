package com.Popov.budgetapp.pdf

/**
 * По тексту PDF пытается разобрать выписку Альфа-Банка, затем Сбербанка.
 */
object BankStatementParsers {

    fun parse(text: String): List<ParsedStatementOperation> {
        val alfa = AlfaBankStatementParser.parse(text)
        if (alfa.isNotEmpty()) return alfa
        val sber = SberbankStatementParser.parse(text)
        if (sber.isNotEmpty()) return sber
        return emptyList()
    }
}
