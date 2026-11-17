package com.pulse.music.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    // 0 = enter email, 1 = enter code, 2 = new password
    var step by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun sendCode(resend: Boolean = false) {
        focus.clearFocus()
        if (email.isBlank()) {
            error = "Enter the email you signed up with."
            return
        }
        error = null
        loading = true
        viewModel.forgotPassword(email.trim()) { msg ->
            loading = false
            if (msg.contains("Code sent", ignoreCase = true)) {
                cooldown = 45
                if (!resend) { step = 1; code = "" }
            } else {
                error = msg
            }
        }
    }

    fun verify() {
        focus.clearFocus()
        if (code.length != 6) {
            error = "Enter the 6-digit code from your email."
            return
        }
        error = null
        loading = true
        viewModel.verifyCode(email.trim(), code) { success, msg ->
            loading = false
            if (success) step = 2 else error = msg
        }
    }

    fun changePassword() {
        focus.clearFocus()
        when {
            newPassword.length < 6 -> error = "Use at least 6 characters."
            newPassword != confirmPassword -> error = "The two passwords don't match."
            else -> {
                error = null
                loading = true
                viewModel.resetPassword(email.trim(), code, newPassword) { success, msg ->
                    loading = false
                    if (success) {
                        Toast.makeText(context, "Password changed. Log in with your new password.", Toast.LENGTH_LONG).show()
                        onNavigateToLogin()
                    } else {
                        error = msg
                    }
                }
            }
        }
    }

    AuthScaffold(
        title = when (step) {
            0 -> "Forgot your password?"
            1 -> "Check your email"
            else -> "Choose a new password"
        },
        subtitle = when (step) {
            0 -> "Enter your email and we'll send you a 6-digit code."
            1 -> "We sent a code to ${email.trim()}. It's valid for 10 minutes."
            else -> "Pick something you haven't used before."
        },
        onBack = { if (step > 0) { step -= 1; error = null } else onNavigateToLogin() }
    ) {
        StepBars(current = step, total = 3)

        when (step) {
            0 -> {
                AuthTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = "Email",
                    icon = Icons.Default.Email,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onDone = { sendCode() }
                )
                ErrorBanner(error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Send code", loading = loading, onClick = { sendCode() })
            }
            1 -> {
                CodeField(value = code, onValueChange = { code = it; error = null }, onDone = { verify() })
                ErrorBanner(error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Verify code", loading = loading, onClick = { verify() })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(enabled = cooldown == 0 && !loading, onClick = { sendCode(resend = true) }) {
                        Text(if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code", color = AuthMuted)
                    }
                }
            }
            else -> {
                AuthTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = "New password",
                    icon = Icons.Default.Lock,
                    isPassword = true
                )
                Spacer(Modifier.height(12.dp))
                AuthTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = "Confirm password",
                    icon = Icons.Default.Lock,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = { changePassword() }
                )
                ErrorBanner(error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Change password", loading = loading, onClick = { changePassword() })
            }
        }
    }
}
