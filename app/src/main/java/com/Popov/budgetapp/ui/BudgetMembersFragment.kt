package com.Popov.budgetapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.AppUser
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.userMessage
import com.Popov.budgetapp.databinding.FragmentBudgetMembersBinding

class BudgetMembersFragment : Fragment(R.layout.fragment_budget_members) {
    private var _binding: FragmentBudgetMembersBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private var currentBudget: Budget? = null

    private val adapter = MembersAdapter { member -> confirmRemoveMember(member) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBudgetMembersBinding.bind(view)

        binding.tvBudgetName.text = "Участники: ${SessionStore.selectedBudgetName}"
        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter

        binding.btnLeaveBudget.setOnClickListener { confirmLeaveBudget() }

        if (SessionStore.selectedBudgetId.isBlank()) {
            Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
            return
        }

        loadMembers()
    }

    private fun loadMembers() {
        val budgetId = SessionStore.selectedBudgetId
        repo.getBudget(budgetId) { budgetResult ->
            if (_binding == null) return@getBudget
            budgetResult.onSuccess { budget ->
                currentBudget = budget
                val uid = repo.currentUid().orEmpty()
                val isOwner = budget.isOwner(uid)
                binding.btnLeaveBudget.visibility =
                    if (uid.isNotBlank() && uid in budget.members && !isOwner) View.VISIBLE else View.GONE

                repo.getBudgetMembers(budgetId) { result ->
                    if (_binding == null) return@getBudgetMembers
                    result.onSuccess { users ->
                        adapter.submitList(
                            newItems = users,
                            currentUid = uid,
                            ownerUids = budget.owners.toSet(),
                            canRemoveMembers = isOwner,
                        )
                        binding.tvEmpty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
                    }.onFailure {
                        Toast.makeText(
                            requireContext(),
                            it.userMessage(requireContext()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }.onFailure {
                Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRemoveMember(member: AppUser) {
        val budget = currentBudget ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_member_title)
            .setMessage(getString(R.string.remove_member_message, member.displayName, budget.name))
            .setPositiveButton(R.string.remove_member) { _, _ ->
                repo.removeMemberFromBudget(budget.id, member.uid) { result ->
                    result.onSuccess {
                        Toast.makeText(requireContext(), R.string.member_removed, Toast.LENGTH_SHORT).show()
                        loadMembers()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmLeaveBudget() {
        val budget = currentBudget ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.leave_budget_title)
            .setMessage(getString(R.string.leave_budget_message, budget.name))
            .setPositiveButton(R.string.leave_budget) { _, _ ->
                repo.leaveBudget(budget.id) { result ->
                    result.onSuccess {
                        if (SessionStore.selectedBudgetId == budget.id) {
                            SessionStore.selectedBudgetId = ""
                            SessionStore.selectedBudgetName = ""
                        }
                        Toast.makeText(requireContext(), R.string.left_budget, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }.onFailure {
                        Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
