package com.Popov.budgetapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun currentUid(): String? = auth.currentUser?.uid

    fun login(email: String, password: String, onDone: (Result<Unit>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                ensureUserProfile()
                onDone(Result.success(Unit))
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun register(email: String, password: String, onDone: (Result<Unit>) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                ensureUserProfile()
                onDone(Result.success(Unit))
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun logout() = auth.signOut()

    fun createBudget(name: String, category: String, limit: Double, onDone: (Result<Unit>) -> Unit) {
        val uid = currentUid() ?: return onDone(Result.failure(IllegalStateException("Нет пользователя")))
        val ref = db.collection("budgets").document()
        val inviteCode = UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
        val payload = hashMapOf(
            "id" to ref.id,
            "name" to name,
            "category" to category,
            "limit" to limit,
            "owners" to listOf(uid),
            "members" to listOf(uid),
            "inviteCode" to inviteCode
        )
        ref.set(payload)
            .addOnSuccessListener {
                db.collection("invites").document(inviteCode)
                    .set(
                        mapOf(
                            "code" to inviteCode,
                            "budgetId" to ref.id,
                            "createdBy" to uid,
                            "active" to true,
                            "createdAt" to System.currentTimeMillis()
                        )
                    )
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun updateBudget(id: String, name: String, category: String, limit: Double, onDone: (Result<Unit>) -> Unit) {
        db.collection("budgets").document(id)
            .update(mapOf("name" to name, "category" to category, "limit" to limit))
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun deleteBudget(id: String, onDone: (Result<Unit>) -> Unit) {
        db.collection("budgets").document(id).get()
            .addOnSuccessListener { budget ->
                val inviteCode = budget.getString("inviteCode").orEmpty()
                db.collection("budgets").document(id).delete()
                    .addOnSuccessListener {
                        if (inviteCode.isBlank()) {
                            onDone(Result.success(Unit))
                        } else {
                            db.collection("invites").document(inviteCode).delete()
                                .addOnSuccessListener { onDone(Result.success(Unit)) }
                                .addOnFailureListener { onDone(Result.failure(it)) }
                        }
                    }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /**
     * Подписка после [FirebaseUser.getIdToken]: при автологине запрос иначе может уйти без JWT → PERMISSION_DENIED.
     */
    fun subscribeBudgets(onResult: (Result<List<Budget>>) -> Unit): ListenerRegistration {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.success(emptyList()))
            return ListenerRegistration { }
        }
        val composite = CompositeRegistration()
        user.getIdToken(true).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onResult(Result.failure(task.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            composite.attach(
                db.collection("budgets")
                    .whereArrayContains("members", user.uid)
                    .addSnapshotListener { value, error ->
                        if (error != null) {
                            onResult(Result.failure(error))
                            return@addSnapshotListener
                        }
                        val budgets = value?.documents?.mapNotNull { doc ->
                            doc.toObject(Budget::class.java)?.copy(id = doc.id)
                        }.orEmpty()
                        onResult(Result.success(budgets))
                    },
            )
        }
        return composite
    }

    fun joinBudgetByInviteCode(inviteCode: String, onDone: (Result<Unit>) -> Unit) {
        val uid = currentUid() ?: return onDone(Result.failure(IllegalStateException("Нет пользователя")))
        val normalizedCode = inviteCode.trim().lowercase()
        db.collection("invites").document(normalizedCode)
            .get()
            .addOnSuccessListener { inviteDoc ->
                if (!inviteDoc.exists()) {
                    onDone(Result.failure(IllegalArgumentException("Код не найден")))
                    return@addOnSuccessListener
                }
                val active = inviteDoc.getBoolean("active") ?: false
                val budgetId = inviteDoc.getString("budgetId").orEmpty()
                if (!active || budgetId.isBlank()) {
                    onDone(Result.failure(IllegalArgumentException("Приглашение неактивно")))
                    return@addOnSuccessListener
                }
                db.collection("budgets").document(budgetId)
                    .update("members", FieldValue.arrayUnion(uid))
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun addTransaction(item: TransactionItem, onDone: (Result<Unit>) -> Unit) {
        val ref = db.collection("transactions").document()
        ref.set(
            mapOf(
                "id" to ref.id,
                "budgetId" to item.budgetId,
                "title" to item.title,
                "amount" to item.amount,
                "category" to item.category,
                "type" to item.type.name,
                "createdAt" to item.createdAt,
                "createdBy" to item.createdBy
            )
        ).addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /** Пакетная запись (до 500 документов за один batch Firestore). */
    fun addTransactionsBatch(items: List<TransactionItem>, onDone: (Result<Int>) -> Unit) {
        val uid = currentUid() ?: return onDone(Result.failure(IllegalStateException("Нет пользователя")))
        if (items.isEmpty()) return onDone(Result.success(0))
        val chunks = items.chunked(500)
        var committedTotal = 0
        fun commitChunk(index: Int) {
            if (index >= chunks.size) {
                onDone(Result.success(committedTotal))
                return
            }
            val batch = db.batch()
            var n = 0
            for (item in chunks[index]) {
                val ref = db.collection("transactions").document()
                batch.set(
                    ref,
                    mapOf(
                        "id" to ref.id,
                        "budgetId" to item.budgetId,
                        "title" to item.title,
                        "amount" to item.amount,
                        "category" to item.category,
                        "type" to item.type.name,
                        "createdAt" to item.createdAt,
                        "createdBy" to item.createdBy.ifBlank { uid }
                    )
                )
                n++
            }
            batch.commit()
                .addOnSuccessListener {
                    committedTotal += n
                    commitChunk(index + 1)
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
        commitChunk(0)
    }

    fun updateTransaction(item: TransactionItem, onDone: (Result<Unit>) -> Unit) {
        db.collection("transactions").document(item.id)
            .update(
                mapOf(
                    "title" to item.title,
                    "amount" to item.amount,
                    "category" to item.category,
                    "type" to item.type.name
                )
            )
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun deleteTransaction(id: String, onDone: (Result<Unit>) -> Unit) {
        db.collection("transactions").document(id).delete()
            .addOnSuccessListener { onDone(Result.success(Unit)) }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /**
     * Слушатель транзакций бюджета. Обязательно вызвать [ListenerRegistration.remove] в onDestroyView,
     * иначе колбэк может прийти после уничтожения фрагмента и обратиться к view.
     */
    fun subscribeTransactions(budgetId: String, onResult: (Result<List<TransactionItem>>) -> Unit): ListenerRegistration {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Нет пользователя")))
            return ListenerRegistration { }
        }
        val composite = CompositeRegistration()
        user.getIdToken(true).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onResult(Result.failure(task.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            composite.attach(
                db.collection("transactions")
                    .whereEqualTo("budgetId", budgetId)
                    .addSnapshotListener { value, error ->
                        if (error != null) {
                            onResult(Result.failure(error))
                            return@addSnapshotListener
                        }
                        val items = value?.documents?.mapNotNull { doc ->
                            val typeRaw = doc.getString("type") ?: TransactionType.EXPENSE.name
                            val type = runCatching { TransactionType.valueOf(typeRaw) }.getOrDefault(TransactionType.EXPENSE)
                            TransactionItem(
                                id = doc.id,
                                budgetId = doc.getString("budgetId").orEmpty(),
                                title = doc.getString("title").orEmpty(),
                                amount = doc.getDouble("amount") ?: 0.0,
                                category = doc.getString("category").orEmpty(),
                                type = type,
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                createdBy = doc.getString("createdBy").orEmpty()
                            )
                        }.orEmpty()
                        onResult(Result.success(items))
                    },
            )
        }
        return composite
    }

    fun getBudgetMembers(budgetId: String, onDone: (Result<List<AppUser>>) -> Unit) {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { budgetDoc ->
                val memberIds = (budgetDoc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (memberIds.isEmpty()) {
                    onDone(Result.success(emptyList()))
                    return@addOnSuccessListener
                }
                val users = mutableListOf<AppUser>()
                var loaded = 0

                memberIds.forEach { memberId ->
                    db.collection("users").document(memberId).get()
                        .addOnSuccessListener { userDoc ->
                            val fallbackEmail = if (memberId == currentUid()) auth.currentUser?.email.orEmpty() else ""
                            users += AppUser(
                                uid = memberId,
                                email = userDoc.getString("email").orEmpty().ifBlank { fallbackEmail }
                            )
                            loaded++
                            if (loaded == memberIds.size) {
                                onDone(Result.success(users.distinctBy { it.uid }))
                            }
                        }
                        .addOnFailureListener { onDone(Result.failure(it)) }
                }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    private fun ensureUserProfile() {
        val user = auth.currentUser ?: return
        val payload = mapOf(
            "uid" to user.uid,
            "email" to (user.email ?: "")
        )
        db.collection("users").document(user.uid).set(payload)
    }

    /** Снимает Firestore-слушатель и отменяет постановку в очередь, если фрагмент уже уничтожен. */
    private class CompositeRegistration : ListenerRegistration {
        @Volatile private var cancelled = false
        private var inner: ListenerRegistration? = null

        fun attach(reg: ListenerRegistration) {
            if (cancelled) {
                reg.remove()
                return
            }
            inner?.remove()
            inner = reg
        }

        override fun remove() {
            cancelled = true
            inner?.remove()
            inner = null
        }
    }
}
