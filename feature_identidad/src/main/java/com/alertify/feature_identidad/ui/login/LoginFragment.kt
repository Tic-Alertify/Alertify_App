package com.alertify.feature_identidad.ui.login

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alertify.feature_identidad.R
import com.alertify.feature_identidad.data.auth.AuthUiMessageFactory
import com.alertify.feature_identidad.navigation.LoginNavigator
import com.alertify.feature_identidad.ui.register.RegisterUiEvent
import com.alertify.feature_identidad.ui.register.RegisterUiState
import com.alertify.feature_identidad.ui.register.RegisterViewModel
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.auth_fragment_login) {

    private val loginViewModel: LoginViewModel by viewModels()
    private val registerViewModel: RegisterViewModel by viewModels()

    private lateinit var tvLoginTab: TextView
    private lateinit var tvRegisterTab: TextView
    private lateinit var tilUsername: TextInputLayout
    private lateinit var etUsernameEmail: TextInputEditText
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvForgotPassword: TextView
    private lateinit var btnAction: Button
    private lateinit var ivBackgroundImage: ImageView
    private lateinit var loginCardView: CardView
    private lateinit var llSocialLogins: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var isLoginMode = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvLoginTab = view.findViewById(R.id.tv_login_tab)
        tvRegisterTab = view.findViewById(R.id.tv_register_tab)
        tilUsername = view.findViewById(R.id.til_username)
        etUsernameEmail = view.findViewById(R.id.et_username_email)
        tilEmail = view.findViewById(R.id.til_email)
        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        tilConfirmPassword = view.findViewById(R.id.til_confirm_password)
        etConfirmPassword = view.findViewById(R.id.et_confirm_password)
        tvForgotPassword = view.findViewById(R.id.tv_forgot_password)
        btnAction = view.findViewById(R.id.btn_action)
        ivBackgroundImage = view.findViewById(R.id.iv_background_image)
        loginCardView = view.findViewById(R.id.login_card_view)
        llSocialLogins = view.findViewById(R.id.ll_social_logins)
        progressBar = view.findViewById(R.id.auth_progress_login)

        applyBlurToBackground()
        updateUIMode(true)

        tvLoginTab.setOnClickListener { updateUIMode(true) }
        tvRegisterTab.setOnClickListener { updateUIMode(false) }

        btnAction.setOnClickListener {
            hideKeyboard()
            if (isLoginMode) {
                val email = etUsernameEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString()?.trim().orEmpty()
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.auth_error_empty_fields), Toast.LENGTH_SHORT).show()
                } else {
                    loginViewModel.login(email, password)
                }
            } else {
                val username = etUsernameEmail.text?.toString()?.trim().orEmpty()
                val email = etEmail.text?.toString()?.trim().orEmpty()
                val password = etPassword.text?.toString()?.trim().orEmpty()
                val confirmPassword = etConfirmPassword.text?.toString()?.trim().orEmpty()
                registerViewModel.register(username, email, password, confirmPassword)
            }
        }

        collectUiState()
        collectEvents()
        collectRegisterUiState()
        collectRegisterEvents()

        loginViewModel.checkSession()
    }

    private fun collectUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.uiState.collect { state ->
                    when (state) {
                        is LoginUiState.Loading -> setLoadingState(true)
                        is LoginUiState.Idle,
                        is LoginUiState.Success,
                        is LoginUiState.Error -> setLoadingState(false)
                    }
                }
            }
        }
    }

    private fun collectEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.events.collect { event ->
                    when (event) {
                        is LoginEvent.NavigateToDashboard -> {
                            (activity as? LoginNavigator)?.onLoginSuccess()
                        }

                        is LoginEvent.ShowError -> {
                            val msg = AuthUiMessageFactory.toMessage(requireContext(), event.authError)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            loginViewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    private fun collectRegisterUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                registerViewModel.uiState.collect { state ->
                    when (state) {
                        is RegisterUiState.Loading -> setLoadingState(true)
                        is RegisterUiState.Idle,
                        is RegisterUiState.Success,
                        is RegisterUiState.Error -> setLoadingState(false)
                    }
                }
            }
        }
    }

    private fun collectRegisterEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                registerViewModel.events.collect { event ->
                    when (event) {
                        is RegisterUiEvent.RegistrationSuccess -> {
                            Toast.makeText(requireContext(), getString(R.string.auth_registration_successful), Toast.LENGTH_LONG).show()
                            clearFields()
                            updateUIMode(true)
                        }

                        is RegisterUiEvent.ShowError -> {
                            val msg = AuthUiMessageFactory.toMessage(requireContext(), event.authError)
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            registerViewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        btnAction.isEnabled = !loading
        btnAction.alpha = if (loading) 0.6f else 1.0f
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun clearFields() {
        etUsernameEmail.text?.clear()
        etEmail.text?.clear()
        etPassword.text?.clear()
        etConfirmPassword.text?.clear()
    }

    private fun updateUIMode(toLoginMode: Boolean) {
        isLoginMode = toLoginMode

        if (isLoginMode) {
            tvLoginTab.setBackgroundResource(R.drawable.shape_toggle_button_selected)
            tvLoginTab.setTextColor(resources.getColor(R.color.auth_toggle_unselected, null))
            tvRegisterTab.setBackgroundResource(R.drawable.shape_toggle_button_unselected)
            tvRegisterTab.setTextColor(resources.getColor(R.color.auth_toggle_selected, null))
            etUsernameEmail.hint = getString(R.string.auth_hint_username_email)
        } else {
            tvLoginTab.setBackgroundResource(R.drawable.shape_toggle_button_unselected)
            tvLoginTab.setTextColor(resources.getColor(R.color.auth_toggle_selected, null))
            tvRegisterTab.setBackgroundResource(R.drawable.shape_toggle_button_selected)
            tvRegisterTab.setTextColor(resources.getColor(R.color.auth_toggle_unselected, null))
            etUsernameEmail.hint = getString(R.string.auth_hint_username)
        }

        tilEmail.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        tilConfirmPassword.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        tvForgotPassword.visibility = if (isLoginMode) View.VISIBLE else View.GONE
        llSocialLogins.visibility = if (isLoginMode) View.VISIBLE else View.GONE

        btnAction.text = if (isLoginMode) getString(R.string.auth_button_login) else getString(R.string.auth_register_tab_text)
    }

    @Suppress("DEPRECATION")
    private fun applyBlurToBackground() {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.background_general)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivBackgroundImage.setRenderEffect(
                RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            )
            ivBackgroundImage.setImageBitmap(bitmap)
        } else {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 4, bitmap.height / 4, false)
            val blurred = blurBitmap(requireContext(), scaledBitmap, 20f)
            ivBackgroundImage.setImageBitmap(blurred)
            bitmap.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun blurBitmap(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val rs = RenderScript.create(context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        script.setRadius(radius)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(bitmap)
        rs.destroy()
        return bitmap
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
