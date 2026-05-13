package com.Popov.budgetapp.data

enum class TransactionType {
    INCOME,
    EXPENSE
}

data class AppUser(
    val uid: String = "",
    val email: String = ""
)

data class Budget(
    val id: String = "",
    val name: String = "",
    val category: String = "Прочее",
    val limit: Double = 0.0,
    val owners: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val inviteCode: String = ""
)

data class TransactionItem(
    val id: String = "",
    val budgetId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "Прочее",
    val type: TransactionType = TransactionType.EXPENSE,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = ""
)
