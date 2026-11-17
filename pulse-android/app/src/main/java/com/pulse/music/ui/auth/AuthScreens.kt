package com.pulse.music.ui.auth

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.pulse.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    val token by viewModel.token.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    // Saving the token (any kind of login) is what moves us to Home.
    LaunchedEffect(token) {
        if (!token.isNullOrEmpty()) onNavigateToHome()
    }

    fun submit() {
        focus.clearFocus()
        if (email.isBlank() || password.isBlank()) {
            error = "Enter your email and password."
            return
        }
        error = null
        loading = true
        viewModel.login(email.trim(), password, { loading = false }, { msg ->
            loading = false
            error = msg
        })
    }

    fun signInWithGoogle() {
        error = null
        googleLoading = true
        scope.launch {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = result.credential

                val idToken: String? = if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else null

                if (idToken == null) {
                    googleLoading = false
                    error = "Google sign-in returned something unexpected. Please try again."
                    return@launch
                }
                viewModel.socialLogin("google", idToken, { googleLoading = false }, { msg ->
                    googleLoading = false
                    error = msg
                })
            } catch (e: GetCredentialCancellationException) {
                googleLoading = false // the user closed the Google window: no error needed
            } catch (e: NoCredentialException) {
                googleLoading = false
                error = "No Google account was found on this phone. Add one in Settings, then try again."
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "type=${e.type} msg=${e.message}", e)
                googleLoading = false
                error = "Google sign-in isn't available right now. Please use your email for now."
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "unexpected", e)
                googleLoading = false
                error = "Something went wrong with Google sign-in. Please try again."
            }
        }
    }

    // Facebook
    val callbackManager = remember { com.facebook.CallbackManager.Factory.create() }
    DisposableEffect(Unit) {
        com.facebook.login.LoginManager.getInstance().registerCallback(
            callbackManager,
            object : com.facebook.FacebookCallback<com.facebook.login.LoginResult> {
                override fun onSuccess(result: com.facebook.login.LoginResult) {
                    viewModel.socialLogin("facebook", result.accessToken.token, {}, { msg -> error = msg })
                }
                override fun onCancel() {}
                override fun onError(error2: com.facebook.FacebookException) {
                    error = "Facebook sign-in didn't work. Please try again."
                }
            }
        )
        onDispose { com.facebook.login.LoginManager.getInstance().unregisterCallback(callbackManager) }
    }
    val facebookLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = com.facebook.login.LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null)
    ) { }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Log in to pick up right where you left off."
    ) {
        AuthTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = "Email",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = "Password",
            icon = Icons.Default.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = { submit() }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNavigateToForgotPassword) {
                Text("Forgot password?", color = AuthMuted, fontSize = 14.sp)
            }
        }

        ErrorBanner(error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Log in", loading = loading, onClick = { submit() })

        Spacer(Modifier.height(22.dp))
        OrDivider()
        Spacer(Modifier.height(22.dp))

        SocialButton(text = "Continue with Google", light = true, loading = googleLoading, onClick = { signInWithGoogle() })
        Spacer(Modifier.height(12.dp))
        SocialButton(text = "Continue with Facebook", onClick = {
            error = null
            facebookLauncher.launch(listOf("email", "public_profile"))
        })

        Spacer(Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("New to Pulse? ", color = AuthMuted, fontSize = 15.sp)
            Text(
                "Create an account",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onNavigateToRegister).padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun RegisterScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableStateOf(0) }
    val focus = LocalFocusManager.current

    // counts down the "resend code" wait
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    fun sendCode() {
        focus.clearFocus()
        val e = email.trim()
        when {
            username.isBlank() -> error = "Choose a username."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> error = "Enter a valid email address."
            password.length < 6 -> error = "Use at least 6 characters for your password."
            else -> {
                error = null
                loading = true
                viewModel.requestRegisterOtp(e) { msg ->
                    loading = false
                    if (msg.contains("sent", ignoreCase = true)) {
                        step = 1
                        cooldown = 45
                        code = ""
                    } else {
                        error = msg
                    }
                }
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
        viewModel.register(username.trim(), email.trim(), password, code, onNavigateToHome) { msg ->
            loading = false
            error = msg
        }
    }

    AuthScaffold(
        title = if (step == 0) "Create your account" else "Check your email",
        subtitle = if (step == 0) "It takes under a minute." else "We sent a 6-digit code to ${email.trim()}.",
        onBack = if (step == 1) ({ step = 0; error = null }) else null
    ) {
        StepBars(current = step, total = 2)

        if (step == 0) {
            AuthTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = "Username",
                icon = Icons.Default.Person
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = "Email",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Password (6+ characters)",
                icon = Icons.Default.Lock,
                isPassword = true,
                imeAction = ImeAction.Done,
                onDone = { sendCode() }
            )
            ErrorBanner(error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Send me a code", loading = loading, onClick = { sendCode() })
        } else {
            CodeField(value = code, onValueChange = { code = it; error = null }, onDone = { verify() })
            ErrorBanner(error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "Verify and sign up", loading = loading, onClick = { verify() })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    enabled = cooldown == 0 && !loading,
                    onClick = {
                        loading = true
                        viewModel.requestRegisterOtp(email.trim()) { msg ->
                            loading = false
                            if (msg.contains("sent", ignoreCase = true)) cooldown = 45 else error = msg
                        }
                    }
                ) {
                    Text(if (cooldown > 0) "Resend code in ${cooldown}s" else "Resend code", color = AuthMuted)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account? ", color = AuthMuted, fontSize = 15.sp)
            Text(
                "Log in",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onNavigateToLogin).padding(vertical = 8.dp)
            )
        }
    }
}
