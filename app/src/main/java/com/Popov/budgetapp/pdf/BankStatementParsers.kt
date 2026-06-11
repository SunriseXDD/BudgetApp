package com.Popov.budgetapp.pdf

/**
 * По тексту PDF разбирает выписку: Яндекс, Т-Банк, Альфа-Банк или Сбербанк.
 */
object BankStatementParsers {

    fun parse(text: String): List<ParsedStatementOperation> {
        if (YandexBankStatementParser.looksLikeStatement(text)) {
            val yandex = YandexBankStatementParser.parse(text)
            if (yandex.isNotEmpty()) return yandex
        }
        if (TBankStatementParser.looksLikeStatement(text)) {
            val tbank = TBankStatementParser.parse(text)
            if (tbank.isNotEmpty()) return tbank
        }
        val alfa = AlfaBankStatementParser.parse(text)
        if (alfa.isNotEmpty()) return alfa
        val sber = SberbankStatementParser.parse(text)
        if (sber.isNotEmpty()) return sber
        val yandexFallback = YandexBankStatementParser.parse(text)
        if (yandexFallback.isNotEmpty()) return yandexFallback
        val tbankFallback = TBankStatementParser.parse(text)
        if (tbankFallback.isNotEmpty()) return tbankFallback
        return emptyList()
    }
}
