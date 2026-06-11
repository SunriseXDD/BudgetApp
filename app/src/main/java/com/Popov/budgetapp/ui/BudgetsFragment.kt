package com.Popov.budgetapp.ui

import android.os.Bundle
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.data.computeBudgetBalances
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.databinding.DialogBudgetBinding
import com.Popov.budgetapp.databinding.FragmentBudgetsBinding
import com.Popov.budgetapp.data.userMessage
import com.google.firebase.firestore.ListenerRegistration

class BudgetsFragment : Fragment(R.layout.fragment_budgets) {
    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private var budgetsListener: ListenerRegistration? = null
    private var transactionsListener: ListenerRegistration? = null
    private var allBudgets: List<Budget> = emptyList()
    private var selectedBudget: Budget? = null
    private var budgetBalances: Map<String, Double> = emptyMap()

    private val adapter = BudgetAdapter(
        currentUid = { repo.currentUid().orEmpty() },
        onSelect = { budget ->
            selectedBudget = budget
            SessionStore.selectedBudgetId = budget.id
            SessionStore.selectedBudgetName = budget.name
            applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
            findNavController().navigate(R.id.action_budgetsFragment_to_transactionsFragment)
        },
        onEdit = { budget -> showBudgetDialog(budget) },
        onInvite = { budget -> copyInviteCode(budget) },
        onDelete = { budget -> confirmDeleteBudget(budget) },
        onLeave = { budget -> confirmLeaveBudget(budget) },
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBudgetsBinding.bind(view)

        binding.rvBudgets.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBudgets.adapter = adapter

        binding.btnCreateBudget.setOnClickListener { showBudgetDialog(null) }
        binding.btnAddBudgetTop.setOnClickListener { showBudgetDialog(null) }
        binding.btnJoinBudget.setOnClickListener {
            val code = binding.etInviteCode.text.toString().trim()
            if (code.isBlank()) {
                Toast.makeText(requireContext(), "Введите код приглашения", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            repo.joinBudgetByInviteCode(code) { result ->
                result.onSuccess {
                    Toast.makeText(requireContext(), "Вы присоединились к бюджету", Toast.LENGTH_SHORT).show()
                    binding.etInviteCode.text?.clear()
                }.onFailure {
                    Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.etSearchBudget.doAfterTextChanged { text ->
            applyFilter(text?.toString().orEmpty())
        }

        budgetsListener?.remove()
        budgetsListener = repo.subscribeBudgets { result ->
            result.onSuccess { budgets ->
                if (_binding == null) return@subscribeBudgets
                allBudgets = budgets
                if (budgets.isEmpty()) {
                    selectedBudget = null
                    SessionStore.selectedBudgetId = ""
                    SessionStore.selectedBudgetName = ""
                    subscribeBudgetTransactions(emptyList())
                } else if (selectedBudget == null || budgets.none { it.id == selectedBudget?.id }) {
                    selectedBudget = budgets.first()
                    SessionStore.selectedBudgetId = selectedBudget?.id.orEmpty()
                    SessionStore.selectedBudgetName = selectedBudget?.name.orEmpty()
                } else {
                    selectedBudget = budgets.firstOrNull { it.id == selectedBudget?.id }
                }
                applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
                subscribeBudgetTransactions(budgets.map { it.id })
            }.onFailure { err ->
                if (_binding == null) return@subscribeBudgets
                Toast.makeText(requireContext(), err.userMessage(requireContext()), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copyInviteCode(budget: Budget) {
        val message = "Код приглашения в бюджет '${budget.name}': ${budget.inviteCode}"
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", message))
        Toast.makeText(requireContext(), getString(R.string.invite_code_copied), Toast.LENGTH_SHORT).show()
    }

    private fun confirmLeaveBudget(budget: Budget) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.leave_budget_title)
            .setMessage(getString(R.string.leave_budget_message, budget.name))
            .setPositiveButton(R.string.leave_budget) { _, _ ->
                repo.leaveBudget(budget.id) { result ->
                    result.onSuccess {
                        if (selectedBudget?.id == budget.id) {
                            selectedBudget = null
                            SessionStore.selectedBudgetId = ""
                            SessionStore.selectedBudgetName = ""
                        }
                        Toast.makeText(requireContext(), R.string.left_budget, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDeleteBudget(budget: Budget) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_budget_title)
            .setMessage(getString(R.string.delete_budget_message, budget.name))
            .setPositiveButton(R.string.delete_budget) { _, _ ->
                repo.deleteBudget(budget.id) { result ->
                    result.onSuccess {
                        if (selectedBudget?.id == budget.id) {
                            selectedBudget = null
                            SessionStore.selectedBudgetId = ""
                            SessionStore.selectedBudgetName = ""
                        }
                        Toast.makeText(requireContext(), R.string.budget_deleted, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showBudgetDialog(budget: Budget?) {
        val uid = repo.currentUid().orEmpty()
        if (budget != null && !budget.isOwner(uid)) {
            Toast.makeText(requireContext(), R.string.budget_edit_owner_only, Toast.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogBudgetBinding.inflate(layoutInflater)
        dialogBinding.etBudgetName.setText(budget?.name.orEmpty())
        if (budget != null) dialogBinding.etBudgetLimit.setText(budget.limit.toString())
        setSpinnerByValue(dialogBinding.spBudgetCategory, R.array.budget_categories, budget?.category ?: "Прочее")
        AlertDialog.Builder(requireContext())
            .setTitle(if (budget == null) "Новый бюджет" else "Редактирование бюджета")
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = dialogBinding.etBudgetName.text.toString().trim()
                val limit = dialogBinding.etBudgetLimit.text.toString().toDoubleOrNull() ?: 0.0
                val category = dialogBinding.spBudgetCategory.selectedItem?.toString().orEmpty().ifBlank { "Прочее" }
                if (name.isBlank()) return@setPositiveButton
                if (budget == null) {
                    repo.createBudget(name, category, limit) { result ->
                        result.onSuccess {
                            Toast.makeText(requireContext(), "Бюджет создан", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    repo.updateBudget(budget.id, name, category, limit) { result ->
                        result.onFailure {
                            Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun applyFilter(rawQuery: String) {
        val query = rawQuery.trim().lowercase()
        val filtered = if (query.isBlank()) {
            allBudgets
        } else {
            allBudgets.filter {
                it.name.lowercase().contains(query) || it.category.lowercase().contains(query)
            }
        }
        adapter.submitList(filtered, selectedBudget?.id.orEmpty(), budgetBalances)
    }

    private fun subscribeBudgetTransactions(budgetIds: List<String>) {
        transactionsListener?.remove()
        if (budgetIds.isEmpty()) {
            budgetBalances = emptyMap()
            applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
            return
        }
        transactionsListener = repo.subscribeTransactionsForBudgets(budgetIds) { result ->
            if (_binding == null) return@subscribeTransactionsForBudgets
            result.onSuccess { list ->
                if (_binding == null) return@onSuccess
                budgetBalances = computeBudgetBalances(list, budgetIds)
                applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
            }.onFailure {
                // Баланс необязателен — карточки покажут 0 ₽
            }
        }
    }

    private fun setSpinnerByValue(spinner: android.widget.Spinner, arrayResId: Int, value: String) {
        val values = resources.getStringArray(arrayResId)
        val index = values.indexOfFirst { it.equals(value, ignoreCase = true) }.coerceAtLeast(0)
        spinner.setSelection(index)
    }

    override fun onDestroyView() {
        budgetsListener?.remove()
        budgetsListener = null
        transactionsListener?.remove()
        transactionsListener = null
        super.onDestroyView()
        _binding = null
    }
}
