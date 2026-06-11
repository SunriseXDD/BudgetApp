package com.Popov.budgetapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.Budget
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.userMessage
import com.Popov.budgetapp.databinding.DialogChangeNicknameBinding
import com.Popov.budgetapp.databinding.DialogChangePasswordBinding
import com.Popov.budgetapp.databinding.DialogNotificationSettingsBinding
import com.Popov.budgetapp.databinding.FragmentProfileBinding
import com.Popov.budgetapp.databinding.ItemProfileBudgetBinding
import com.Popov.budgetapp.notification.NotificationPrefs
import com.google.firebase.firestore.ListenerRegistration
import androidx.appcompat.app.AppCompatActivity

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private var budgetsListener: ListenerRegistration? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        loadProfile()
        setupDarkThemeSwitch()

        binding.rowNotifications.setOnClickListener { showNotificationSettings() }
        binding.rowChangeNickname.setOnClickListener { showChangeNicknameDialog() }
        binding.rowChangePassword.setOnClickListener { showChangePasswordDialog() }

        binding.btnLogout.setOnClickListener {
            budgetsListener?.remove()
            budgetsListener = null
            repo.logout()
            SessionStore.selectedBudgetId = ""
            SessionStore.selectedBudgetName = ""
            findNavController().navigate(R.id.action_profileFragment_to_authFragment)
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
                    val row = ItemProfileBudgetBinding.inflate(
                        LayoutInflater.from(requireContext()),
                        binding.containerBudgets,
                        false,
                    )
                    bindBudgetRow(row, budget, uid)
                    binding.containerBudgets.addView(row.root)
                }
            }.onFailure {
                if (_binding == null) return@subscribeBudgets
                Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDarkThemeSwitch() {
        val prefs = ThemePrefs(requireContext())
        binding.switchDarkTheme.isChecked = prefs.isDarkTheme
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            if (prefs.isDarkTheme == isChecked) return@setOnCheckedChangeListener
            ThemeManager.setDarkTheme(requireContext(), isChecked)
            (requireActivity() as AppCompatActivity).recreate()
        }
    }

    private fun loadProfile() {
        repo.getCurrentUserProfile { result ->
            if (_binding == null) return@getCurrentUserProfile
            result.onSuccess { profile ->
                binding.tvDisplayName.text = profile.displayName
                binding.tvEmailFull.text = profile.email.ifBlank { "—" }
            }
        }
    }

    private fun showChangeNicknameDialog() {
        val dialogBinding = DialogChangeNicknameBinding.inflate(layoutInflater)
        repo.getCurrentUserProfile { result ->
            if (_binding == null) return@getCurrentUserProfile
            result.onSuccess { profile ->
                dialogBinding.etNickname.setText(profile.nickname)
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.change_nickname_title)
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val nickname = dialogBinding.etNickname.text.toString().trim()
                        if (nickname.length < 2) {
                            Toast.makeText(requireContext(), "Минимум 2 символа", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        repo.updateNickname(nickname) { result ->
                            result.onSuccess {
                                dialog.dismiss()
                                binding.tvDisplayName.text = nickname
                                Toast.makeText(requireContext(), R.string.nickname_changed, Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.change_password_title)
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val current = dialogBinding.etCurrentPassword.text.toString()
                        val newPass = dialogBinding.etNewPassword.text.toString()
                        val confirm = dialogBinding.etConfirmPassword.text.toString()
                        if (newPass != confirm) {
                            Toast.makeText(requireContext(), "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        repo.changePassword(current, newPass) { result ->
                            result.onSuccess {
                                dialog.dismiss()
                                Toast.makeText(requireContext(), R.string.password_changed, Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showNotificationSettings() {
        val prefs = NotificationPrefs(requireContext())
        val dialogBinding = DialogNotificationSettingsBinding.inflate(layoutInflater)
        dialogBinding.switchNotifications.isChecked = prefs.notificationsEnabled
        dialogBinding.switchRemindTransactions.isChecked = prefs.remindTransactions
        dialogBinding.switchRemindWeekly.isChecked = prefs.remindWeekly
        dialogBinding.switchRemindMonthly.isChecked = prefs.remindMonthly

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.notification_settings_title)
            .setView(dialogBinding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                prefs.notificationsEnabled = dialogBinding.switchNotifications.isChecked
                prefs.remindTransactions = dialogBinding.switchRemindTransactions.isChecked
                prefs.remindWeekly = dialogBinding.switchRemindWeekly.isChecked
                prefs.remindMonthly = dialogBinding.switchRemindMonthly.isChecked
                Toast.makeText(requireContext(), "Настройки сохранены", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Системные") { _, _ ->
                openAppNotificationSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
        }
        startActivity(intent)
    }

    private fun bindBudgetRow(row: ItemProfileBudgetBinding, budget: Budget, uid: String) {
        row.tvBudgetParticipationName.text = budget.name
        val role = if (budget.owners.contains(uid)) "Администратор" else "Участник"
        row.tvBudgetRole.text = role
        row.root.setOnClickListener {
            SessionStore.selectedBudgetId = budget.id
            SessionStore.selectedBudgetName = budget.name
            findNavController().navigate(R.id.action_profileFragment_to_transactionsFragment)
        }
    }

    override fun onDestroyView() {
        budgetsListener?.remove()
        budgetsListener = null
        super.onDestroyView()
        _binding = null
    }
}
