package com.Popov.budgetapp.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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

    fun register(email: String, password: String, nickname: String, onDone: (Result<Unit>) -> Unit) {
        val trimmedNick = nickname.trim()
        if (trimmedNick.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Укажите никнейм")))
            return
        }
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                saveUserProfile(trimmedNick) { result ->
                    if (result.isSuccess) onDone(Result.success(Unit))
                    else onDone(result)
                }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    fun getCurrentUserProfile(onDone: (Result<AppUser>) -> Unit) {
        val uid = currentUid() ?: return onDone(Result.failure(IllegalStateException("Нет пользователя")))
        runWithFreshAuthToken(
            onReady = {
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        onDone(Result.success(parseUserDoc(uid, doc)))
                    }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            },
            onError = { onDone(Result.failure(it)) },
        )
    }

    fun updateNickname(nickname: String, onDone: (Result<Unit>) -> Unit) {
        val trimmed = nickname.trim()
        if (trimmed.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Никнейм не может быть пустым")))
            return
        }
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        runWithFreshAuthToken(
            onReady = {
                db.collection("users").document(user.uid)
                    .set(
                        mapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: ""),
                            "nickname" to trimmed,
                        ),
                        SetOptions.merge(),
                    )
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            },
            onError = { onDone(Result.failure(it)) },
        )
    }

    /** Создаёт/дополняет документ users после входа (в т.ч. при автологине). */
    fun ensureUserProfile(onDone: ((Result<Unit>) -> Unit)? = null) {
        val user = auth.currentUser
        if (user == null) {
            onDone?.invoke(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        runWithFreshAuthToken(
            onReady = {
                val payload = mapOf(
                    "uid" to user.uid,
                    "email" to (user.email ?: ""),
                )
                db.collection("users").document(user.uid)
                    .set(payload, SetOptions.merge())
                    .addOnSuccessListener { onDone?.invoke(Result.success(Unit)) }
                    .addOnFailureListener { onDone?.invoke(Result.failure(it)) }
            },
            onError = { onDone?.invoke(Result.failure(it)) },
        )
    }

    fun changePassword(currentPassword: String, newPassword: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank()) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        if (newPassword.length < 6) {
            onDone(Result.failure(IllegalArgumentException("Новый пароль — минимум 6 символов")))
            return
        }
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPassword)
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
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
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        user.getIdToken(true).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onDone(Result.failure(task.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            db.collection("budgets").document(id)
                .update(mapOf("name" to name, "category" to category, "limit" to limit))
                .addOnSuccessListener { onDone(Result.success(Unit)) }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
    }

    fun deleteBudget(id: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        user.getIdToken(true).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onDone(Result.failure(task.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            db.collection("budgets").document(id).get()
                .addOnSuccessListener { budget ->
                    val inviteCode = budget.getString("inviteCode").orEmpty()
                    val budgetRef = db.collection("budgets").document(id)

                    fun deleteBudgetDocument() {
                        budgetRef.delete()
                            .addOnSuccessListener { onDone(Result.success(Unit)) }
                            .addOnFailureListener { onDone(Result.failure(it)) }
                    }

                    // Сначала invite: после удаления бюджета get(budgetId) в правилах уже недоступен.
                    if (inviteCode.isBlank()) {
                        deleteBudgetDocument()
                    } else {
                        db.collection("invites").document(inviteCode).delete()
                            .addOnCompleteListener { deleteBudgetDocument() }
                    }
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
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

    /**
     * Присоединение по коду: чтение [invites], затем arrayUnion в [members].
     * В Firestore Rules для update бюджета нужен [isInviteJoinUpdate] без требования
     * «uid уже в resource.data.members» (см. комментарий в консоли Firebase).
     */
    fun joinBudgetByInviteCode(inviteCode: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        val uid = user.uid
        val normalizedCode = inviteCode.trim().lowercase()
        user.getIdToken(true).addOnCompleteListener { tokenTask ->
            if (!tokenTask.isSuccessful) {
                onDone(Result.failure(tokenTask.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
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
     * Транзакции по списку бюджетов (до 10 id за запрос Firestore whereIn).
     */
    fun subscribeTransactionsForBudgets(
        budgetIds: List<String>,
        onResult: (Result<List<TransactionItem>>) -> Unit,
    ): ListenerRegistration {
        val ids = budgetIds.distinct().filter { it.isNotBlank() }.take(10)
        if (ids.isEmpty()) {
            onResult(Result.success(emptyList()))
            return ListenerRegistration { }
        }
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
                    .whereIn("budgetId", ids)
                    .addSnapshotListener { value, error ->
                        if (error != null) {
                            onResult(Result.failure(error))
                            return@addSnapshotListener
                        }
                        onResult(Result.success(parseTransactionDocuments(value?.documents)))
                    },
            )
        }
        return composite
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
                        onResult(Result.success(parseTransactionDocuments(value?.documents)))
                    },
            )
        }
        return composite
    }

    fun getBudget(budgetId: String, onDone: (Result<Budget>) -> Unit) {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onDone(Result.failure(IllegalArgumentException("Бюджет не найден")))
                    return@addOnSuccessListener
                }
                val budget = doc.toObject(Budget::class.java)?.copy(id = doc.id)
                if (budget == null) {
                    onDone(Result.failure(IllegalStateException("Не удалось прочитать бюджет")))
                } else {
                    onDone(Result.success(budget))
                }
            }
            .addOnFailureListener { onDone(Result.failure(it)) }
    }

    /**
     * Выход участника из бюджета (только не-владелец). В Firestore Rules нужно разрешить
     * arrayRemove своего uid из members без изменения owners (см. комментарий к [joinBudgetByInviteCode]).
     */
    fun leaveBudget(budgetId: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        val uid = user.uid
        user.getIdToken(true).addOnCompleteListener { tokenTask ->
            if (!tokenTask.isSuccessful) {
                onDone(Result.failure(tokenTask.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            db.collection("budgets").document(budgetId).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        onDone(Result.failure(IllegalArgumentException("Бюджет не найден")))
                        return@addOnSuccessListener
                    }
                    val owners = (doc.get("owners") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    if (uid !in members) {
                        onDone(Result.failure(IllegalArgumentException("Вы не состоите в этом бюджете")))
                        return@addOnSuccessListener
                    }
                    if (uid in owners) {
                        onDone(Result.failure(IllegalArgumentException("Владелец не может выйти — удалите бюджет")))
                        return@addOnSuccessListener
                    }
                    db.collection("budgets").document(budgetId)
                        .update("members", FieldValue.arrayRemove(uid))
                        .addOnSuccessListener { onDone(Result.success(Unit)) }
                        .addOnFailureListener { onDone(Result.failure(it)) }
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
    }

    /**
     * Удаление участника владельцем. Нельзя удалить владельца или себя.
     */
    fun removeMemberFromBudget(budgetId: String, memberUid: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        val uid = user.uid
        if (memberUid.isBlank()) {
            onDone(Result.failure(IllegalArgumentException("Участник не указан")))
            return
        }
        if (memberUid == uid) {
            onDone(Result.failure(IllegalArgumentException("Нельзя удалить себя — используйте выход из бюджета")))
            return
        }
        user.getIdToken(true).addOnCompleteListener { tokenTask ->
            if (!tokenTask.isSuccessful) {
                onDone(Result.failure(tokenTask.exception ?: IllegalStateException("Не удалось получить токен")))
                return@addOnCompleteListener
            }
            db.collection("budgets").document(budgetId).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        onDone(Result.failure(IllegalArgumentException("Бюджет не найден")))
                        return@addOnSuccessListener
                    }
                    val owners = (doc.get("owners") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    val members = (doc.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    if (uid !in owners) {
                        onDone(Result.failure(IllegalArgumentException("Только владелец может удалять участников")))
                        return@addOnSuccessListener
                    }
                    if (memberUid !in members) {
                        onDone(Result.failure(IllegalArgumentException("Участник не найден в бюджете")))
                        return@addOnSuccessListener
                    }
                    if (memberUid in owners) {
                        onDone(Result.failure(IllegalArgumentException("Нельзя удалить владельца бюджета")))
                        return@addOnSuccessListener
                    }
                    db.collection("budgets").document(budgetId)
                        .update("members", FieldValue.arrayRemove(memberUid))
                        .addOnSuccessListener { onDone(Result.success(Unit)) }
                        .addOnFailureListener { onDone(Result.failure(it)) }
                }
                .addOnFailureListener { onDone(Result.failure(it)) }
        }
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
                            users += parseUserDoc(
                                memberId,
                                userDoc,
                                fallbackEmail = fallbackEmail,
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

    private fun parseTransactionDocuments(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>?,
    ): List<TransactionItem> =
        documents?.mapNotNull { doc ->
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
                createdBy = doc.getString("createdBy").orEmpty(),
            )
        }.orEmpty()

    private fun saveUserProfile(nickname: String, onDone: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onDone(Result.failure(IllegalStateException("Нет пользователя")))
            return
        }
        val payload = mapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "nickname" to nickname.trim(),
        )
        runWithFreshAuthToken(
            onReady = {
                db.collection("users").document(user.uid)
                    .set(payload, SetOptions.merge())
                    .addOnSuccessListener { onDone(Result.success(Unit)) }
                    .addOnFailureListener { onDone(Result.failure(it)) }
            },
            onError = { onDone(Result.failure(it)) },
        )
    }

    /**
     * Обновляет JWT перед запросом к Firestore. Без этого после автологина возможен PERMISSION_DENIED.
     */
    private fun runWithFreshAuthToken(onReady: () -> Unit, onError: (Exception) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onError(IllegalStateException("Нет пользователя"))
            return
        }
        user.getIdToken(true).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                onError(task.exception ?: IllegalStateException("Не удалось получить токен"))
                return@addOnCompleteListener
            }
            onReady()
        }
    }

    private fun parseUserDoc(
        uid: String,
        doc: com.google.firebase.firestore.DocumentSnapshot,
        fallbackEmail: String = "",
    ): AppUser = AppUser(
        uid = uid,
        email = doc.getString("email").orEmpty().ifBlank { fallbackEmail },
        nickname = doc.getString("nickname").orEmpty(),
    )

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
