package com.Popov.budgetapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.databinding.FragmentProfileBinding
import com.Popov.budgetapp.databinding.ItemProfileBudgetBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private var budgetsListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email.orEmpty()
        binding.tvEmailFull.text = email.ifBlank { "—" }
        binding.tvDisplayName.text = displayNameFromEmail(email)

        binding.btnProfileSettings.setOnClickListener {
            Toast.makeText(requireContext(), "Настройки приложения — скоро", Toast.LENGTH_SHORT).show()
        }

        binding.rowNotifications.setOnClickListener {
            Toast.makeText(requireContext(), "Уведомления — скоро", Toast.LENGTH_SHORT).show()
        }

        binding.rowSecurity.setOnClickListener {
            Toast.makeText(requireContext(), "Безопасность — скоро", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            repo.logout()
            SessionStore.selectedBudgetId = ""
            SessionStore.selectedBudgetName = ""
            findNavController().navigate(R.id.authFragment)
        }

        budgetsListener?.remove()
        budgetsListener = repo.subscribeBudgets { result ->
            result.onSuccess { budgets ->
                if (_binding == null) return@subscribeBudgets
                binding.containerBudgets.removeAllViews()
                if (budgets.isEmpty()) {
                    val tv = android.widget.TextView(requireContext()).apply {
                        text = "Вы пока не участвуете ни в одном бюджете"
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        textSize = 14f
                    }
                    binding.containerBudgets.addView(tv)
                    return@subscribeBudgets
                }
                if (_binding == null) return@subscribeBudgets
                val uid = repo.currentUid().orEmpty()
                budgets.forEach { budget ->
                    val row = ItemProfileBudgetBinding.inflate(LayoutInflater.from(requireContext()), binding.containerBudgets, false)
                    bindBudgetRow(row, budget, uid)
                    binding.containerBudgets.addView(row.root)
                }
            }.onFailure {
                if (_binding == null) return@subscribeBudgets
                Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindBudgetRow(row: ItemProfileBudgetBinding, budget: Budget, uid: String) {
        row.tvBudgetParticipationName.text = budget.name
        val role = if (budget.owners.contains(uid)) {
            "Администратор"
        } else {
            "Участник"
        }
        row.tvBudgetRole.text = role
    }

    private fun displayNameFromEmail(email: String): String {
        if (email.isBlank()) return "Пользователь"
        val local = email.substringBefore("@").replace(".", " ").replace("_", " ").trim()
        return local.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.ifBlank { email }
    }

    override fun onDestroyView() {
        budgetsListener?.remove()
        budgetsListener = null
        super.onDestroyView()
        _binding = null
    }
}
