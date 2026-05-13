package com.Popov.budgetapp.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.Popov.budgetapp.R
import com.Popov.budgetapp.data.FirebaseRepository
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
            // Иначе navigate() может вызваться до полной инициализации NavController у NavHost
            view.post {
                if (!isAdded) return@post
                findNavController().navigate(R.id.budgetsFragment)
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
                    Toast.makeText(requireContext(), it.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
                }
        }

        binding.btnLogin.setOnClickListener {
            auth(isRegister = false)
        }

        binding.btnRegister.setOnClickListener {
            auth(isRegister = true)
        }
    }

    private fun auth(isRegister: Boolean) {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        if (email.isBlank() || password.length < 6) {
            Toast.makeText(requireContext(), "Введите email и пароль (минимум 6 символов)", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnLogin.isEnabled = false
        binding.btnRegister.isEnabled = false
        val callback: (Result<Unit>) -> Unit = { result ->
            binding.btnLogin.isEnabled = true
            binding.btnRegister.isEnabled = true
            result.onSuccess {
                findNavController().navigate(R.id.budgetsFragment)
            }.onFailure {
                Toast.makeText(requireContext(), it.message ?: "Ошибка авторизации", Toast.LENGTH_SHORT).show()
            }
        }
        if (isRegister) repo.register(email, password, callback) else repo.login(email, password, callback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
