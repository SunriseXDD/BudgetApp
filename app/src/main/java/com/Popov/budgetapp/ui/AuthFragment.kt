package com.Popov.budgetapp.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
import com.Popov.budgetapp.data.userMessage
import com.Popov.budgetapp.databinding.FragmentAuthBinding
import com.google.firebase.auth.FirebaseAuth

class AuthFragment : Fragment(R.layout.fragment_auth) {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    private var passwordVisible = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAuthBinding.bind(view)
        if (repo.currentUid() != null) {
            view.post {
                if (!isAdded) return@post
                repo.ensureUserProfile {
                    if (!isAdded) return@ensureUserProfile
                    navigateAfterAuth(repo)
                }
            }
            return
        }

        binding.btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            binding.etPassword.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
            binding.btnTogglePassword.setImageResource(
                if (passwordVisible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24,
            )
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(requireContext(), "Введите email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Письмо для сброса пароля отправлено", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnLogin.setOnClickListener { login() }

        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_authFragment_to_registerFragment)
        }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        if (email.isBlank() || password.length < 6) {
            Toast.makeText(requireContext(), "Введите email и пароль (минимум 6 символов)", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnLogin.isEnabled = false
        repo.login(email, password) { result ->
            binding.btnLogin.isEnabled = true
            result.onSuccess {
                navigateAfterAuth(repo)
            }.onFailure {
                Toast.makeText(requireContext(), it.userMessage(requireContext()), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
