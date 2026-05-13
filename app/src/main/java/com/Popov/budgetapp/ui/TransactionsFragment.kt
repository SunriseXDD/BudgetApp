package com.Popov.budgetapp.ui

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.TransactionItem
import com.Popov.budgetapp.data.TransactionType
import com.Popov.budgetapp.databinding.DialogTransactionBinding
import com.Popov.budgetapp.databinding.FragmentTransactionsBinding
import com.Popov.budgetapp.pdf.BankStatementParsers
import com.Popov.budgetapp.pdf.ParsedStatementOperation
import com.Popov.budgetapp.pdf.PdfTextExtractor
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {
    private var _binding: FragmentTransactionsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    private val filterCal: Calendar = Calendar.getInstance()
    private var fullList: List<TransactionItem> = emptyList()
    private var transactionsListener: ListenerRegistration? = null

    private val adapter = TransactionAdapter(
        currentUid = { repo.currentUid().orEmpty() },
        onClick = { showTransactionDialog(it) },
        onLongClick = { item ->
            AlertDialog.Builder(requireContext())
                .setTitle("Удалить транзакцию?")
                .setPositiveButton("Удалить") { _, _ ->
                    repo.deleteTransaction(item.id) { result ->
                        result.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        },
    )

    private val moneyFormat = NumberFormat.getNumberInstance(Locale("ru", "RU"))
    private val monthYearFormat = SimpleDateFormat("LLLL yyyy", Locale("ru", "RU"))

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPdfPicked(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTransactionsBinding.bind(view)

        binding.tvToolbarTitle.text = SessionStore.selectedBudgetName.ifBlank { "Транзакции" }
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        binding.btnMembers.setOnClickListener {
            findNavController().navigate(R.id.budgetMembersFragment)
        }
        binding.btnAddTransactionWide.setOnClickListener { showTransactionDialog(null) }
        binding.btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Фильтры скоро появятся", Toast.LENGTH_SHORT).show()
        }

        binding.rowMonthFilter.setOnClickListener {
            val y = filterCal.get(Calendar.YEAR)
            val m = filterCal.get(Calendar.MONTH)
            DatePickerDialog(requireContext(), { _, year, month, _ ->
                filterCal.set(Calendar.YEAR, year)
                filterCal.set(Calendar.MONTH, month)
                filterCal.set(Calendar.DAY_OF_MONTH, 1)
                updateMonthLabel()
                refreshFromStored()
            }, y, m, 1).show()
        }

        binding.btnImportPdf.setOnClickListener {
            pickPdf.launch(arrayOf("application/pdf"))
        }

        if (SessionStore.selectedBudgetId.isBlank()) {
            Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
            return
        }

        updateMonthLabel()
        transactionsListener?.remove()
        transactionsListener = repo.subscribeTransactions(SessionStore.selectedBudgetId) { result ->
            if (_binding == null) return@subscribeTransactions
            result.onSuccess { list ->
                if (_binding == null) return@onSuccess
                fullList = list
                refreshFromStored()
            }.onFailure { err ->
                if (_binding == null) return@onFailure
                Toast.makeText(requireContext(), err.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshFromStored() {
        val filtered = filterByMonth(fullList)
        adapter.submitList(filtered)
        updateSummary(filtered)
    }

    private fun filterByMonth(list: List<TransactionItem>): List<TransactionItem> {
        val y = filterCal.get(Calendar.YEAR)
        val m = filterCal.get(Calendar.MONTH)
        return list.filter { tx ->
            val c = Calendar.getInstance().apply { timeInMillis = tx.createdAt }
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
        }
    }

    private fun updateSummary(list: List<TransactionItem>) {
        val income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val balance = income - expense
        binding.tvBalanceAmount.text = "${moneyFormat.format(kotlin.math.round(balance).toLong())} ₽"
        binding.tvIncomeLine.text =
            "Доходы ${moneyFormat.format(kotlin.math.round(income).toLong())} ₽"
        binding.tvExpenseLine.text =
            "Расходы ${moneyFormat.format(kotlin.math.round(expense).toLong())} ₽"
    }

    private fun updateMonthLabel() {
        var s = monthYearFormat.format(filterCal.time)
        if (s.isNotEmpty()) {
            s = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
        }
        binding.tvMonthYear.text = s
    }

    private fun showTransactionDialog(item: TransactionItem?) {
        val dialogBinding = DialogTransactionBinding.inflate(layoutInflater)
        item?.let {
            dialogBinding.etTitle.setText(it.title)
            dialogBinding.etAmount.setText(String.format(Locale.US, "%.2f", it.amount))
            setSpinnerByValue(dialogBinding.spCategory, R.array.transaction_categories, it.category)
            dialogBinding.spType.setSelection(if (it.type == TransactionType.INCOME) 0 else 1)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (item == null) "Новая транзакция" else "Редактирование транзакции")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = dialogBinding.etTitle.text.toString().trim()
                val amountRaw = dialogBinding.etAmount.text.toString().trim().replace(',', '.')
                val amount = amountRaw.toDoubleOrNull() ?: 0.0
                val category = dialogBinding.spCategory.selectedItem?.toString().orEmpty()
                if (title.isBlank() || amount <= 0.0 || category.isBlank()) {
                    Toast.makeText(requireContext(), "Заполните описание, сумму и категорию", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val type = if (dialogBinding.spType.selectedItemPosition == 0) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                }
                val uid = repo.currentUid().orEmpty()
                val tx = TransactionItem(
                    id = item?.id.orEmpty(),
                    budgetId = SessionStore.selectedBudgetId,
                    title = title,
                    amount = amount,
                    category = category,
                    type = type,
                    createdBy = uid
                )
                if (item == null) {
                    repo.addTransaction(tx) { result ->
                        result.onSuccess {
                            dialog.dismiss()
                        }
                        result.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    repo.updateTransaction(tx) { result ->
                        result.onSuccess {
                            dialog.dismiss()
                        }
                        result.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun setSpinnerByValue(spinner: android.widget.Spinner, arrayResId: Int, value: String) {
        val values = resources.getStringArray(arrayResId)
        val index = values.indexOfFirst { it.equals(value, ignoreCase = true) }.coerceAtLeast(0)
        spinner.setSelection(index)
    }

    private fun onPdfPicked(uri: Uri) {
        val uid = repo.currentUid().orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), "Войдите в аккаунт", Toast.LENGTH_SHORT).show()
            return
        }
        if (SessionStore.selectedBudgetId.isBlank()) {
            Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(requireContext(), getString(R.string.import_pdf_adding), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = PdfTextExtractor.extractText(requireContext(), uri)
                    BankStatementParsers.parse(text)
                }
            }
            result.fold(
                onSuccess = { operations ->
                    if (operations.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.import_pdf_empty), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val budgetName = SessionStore.selectedBudgetName.ifBlank { "…" }
                    AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.import_pdf_confirm, operations.size, budgetName))
                        .setPositiveButton("Добавить") { _, _ ->
                            importOperations(operations, uid)
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                },
                onFailure = {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.import_pdf_read_error) + ": ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                },
            )
        }
    }

    private fun importOperations(operations: List<ParsedStatementOperation>, uid: String) {
        val budgetId = SessionStore.selectedBudgetId
        val category = getString(R.string.category_bank_statement)
        val items = operations.map { op ->
            val amount = abs(op.signedAmountRub)
            val type = if (op.signedAmountRub < 0) TransactionType.EXPENSE else TransactionType.INCOME
            TransactionItem(
                budgetId = budgetId,
                title = op.title,
                amount = amount,
                category = category,
                type = type,
                createdAt = op.bookingDateMillis,
                createdBy = uid,
            )
        }
        repo.addTransactionsBatch(items) { result ->
            result.onSuccess { n ->
                if (!isAdded) return@addTransactionsBatch
                applyMonthFilterForImportedDates(operations)
                Toast.makeText(requireContext(), getString(R.string.import_pdf_done, n), Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** После импорта выписки показываем месяц операций, иначе фильтр «текущий месяц» скрывает прошлые даты. */
    private fun applyMonthFilterForImportedDates(operations: List<ParsedStatementOperation>) {
        val latest = operations.maxOfOrNull { it.bookingDateMillis } ?: return
        filterCal.timeInMillis = latest
        filterCal.set(Calendar.DAY_OF_MONTH, 1)
        if (_binding != null) {
            updateMonthLabel()
            refreshFromStored()
        }
    }

    override fun onDestroyView() {
        transactionsListener?.remove()
        transactionsListener = null
        super.onDestroyView()
        _binding = null
    }
}
