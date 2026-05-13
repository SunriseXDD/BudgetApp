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
import androidx.recyclerview.widget.LinearLayoutManager
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.databinding.DialogBudgetBinding
import com.Popov.budgetapp.databinding.FragmentBudgetsBinding
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

class BudgetsFragment : Fragment(R.layout.fragment_budgets) {
    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private var budgetsListener: ListenerRegistration? = null
    private var allBudgets: List<Budget> = emptyList()
    private var selectedBudget: Budget? = null

    private val adapter = BudgetAdapter(
        onSelect = { budget ->
            selectedBudget = budget
            SessionStore.selectedBudgetId = budget.id
            SessionStore.selectedBudgetName = budget.name
            applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
        },
        onDelete = { budget ->
            repo.deleteBudget(budget.id) { result ->
                result.onFailure {
                    Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                }
            }
        },
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
                    Toast.makeText(requireContext(), it.message ?: "Не удалось присоединиться", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnEditBudgetScreen.setOnClickListener {
            val budget = selectedBudget
            if (budget == null) {
                Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
            } else {
                showBudgetDialog(budget)
            }
        }
        binding.btnInviteMembersScreen.setOnClickListener {
            val budget = selectedBudget
            if (budget == null) {
                Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val message = "Код приглашения в бюджет '${budget.name}': ${budget.inviteCode}"
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", message))
            Toast.makeText(requireContext(), "Код приглашения скопирован", Toast.LENGTH_SHORT).show()
        }
        binding.btnDeleteBudgetScreen.setOnClickListener {
            val budget = selectedBudget
            if (budget == null) {
                Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Удалить бюджет?")
                .setMessage("Бюджет '${budget.name}' будет удален без возможности восстановления.")
                .setPositiveButton("Удалить") { _, _ ->
                    repo.deleteBudget(budget.id) { result ->
                        result.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
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
                } else if (selectedBudget == null || budgets.none { it.id == selectedBudget?.id }) {
                    selectedBudget = budgets.first()
                    SessionStore.selectedBudgetId = selectedBudget?.id.orEmpty()
                    SessionStore.selectedBudgetName = selectedBudget?.name.orEmpty()
                } else {
                    selectedBudget = budgets.firstOrNull { it.id == selectedBudget?.id }
                }
                applyFilter(binding.etSearchBudget.text?.toString().orEmpty())
            }.onFailure { err ->
                if (_binding == null) return@subscribeBudgets
                val extra = (err as? FirebaseFirestoreException)?.code?.name.orEmpty()
                val msg = buildString {
                    append(err.message ?: err.toString())
                    if (extra.isNotBlank()) append(" [").append(extra).append(']')
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showBudgetDialog(budget: Budget?) {
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
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    repo.updateBudget(budget.id, name, category, limit) { result ->
                        result.onFailure {
                            Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
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
        adapter.submitList(filtered, selectedBudget?.id.orEmpty())
    }

    private fun setSpinnerByValue(spinner: android.widget.Spinner, arrayResId: Int, value: String) {
        val values = resources.getStringArray(arrayResId)
        val index = values.indexOfFirst { it.equals(value, ignoreCase = true) }.coerceAtLeast(0)
        spinner.setSelection(index)
    }

    override fun onDestroyView() {
        budgetsListener?.remove()
        budgetsListener = null
        super.onDestroyView()
        _binding = null
    }
}
