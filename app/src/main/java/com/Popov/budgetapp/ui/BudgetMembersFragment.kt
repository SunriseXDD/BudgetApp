package com.Popov.budgetapp.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.databinding.FragmentBudgetMembersBinding

class BudgetMembersFragment : Fragment(R.layout.fragment_budget_members) {
    private var _binding: FragmentBudgetMembersBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private val adapter = MembersAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentBudgetMembersBinding.bind(view)

        binding.tvBudgetName.text = "Участники: ${SessionStore.selectedBudgetName}"
        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter

        if (SessionStore.selectedBudgetId.isBlank()) {
            Toast.makeText(requireContext(), "Сначала выберите бюджет", Toast.LENGTH_SHORT).show()
            return
        }

        repo.getBudgetMembers(SessionStore.selectedBudgetId) { result ->
            result.onSuccess { users ->
                adapter.submitList(users)
                if (users.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Не удалось загрузить участников", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
