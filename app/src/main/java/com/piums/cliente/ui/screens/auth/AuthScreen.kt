package com.piums.cliente.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piums.cliente.R
import com.piums.cliente.ui.theme.PiumsError
import com.piums.cliente.ui.theme.PiumsOrange

// ─── Auth container ───────────────────────────────────────────────────────────

private enum class AuthFlow { LOGIN, REGISTER, FORGOT }

@Composable
fun AuthScreen(
    onSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var flow by remember { mutableStateOf(AuthFlow.LOGIN) }

    LaunchedEffect(state.success) { if (state.success) onSuccess() }

    AuthBackground {
        when (flow) {
            AuthFlow.LOGIN    -> LoginSheet(
                state      = state,
                onLogin    = vm::login,
                onGoogle   = { vm.loginWithGoogle(it) },
                onFacebook = { vm.loginWithOAuth(it, "facebook") },
                onTikTok   = { vm.loginWithOAuth(it, "tiktok") },
                onForgot   = { flow = AuthFlow.FORGOT },
                onRegister = { flow = AuthFlow.REGISTER }
            )
            AuthFlow.REGISTER -> RegisterSheet(
                state      = state,
                onRegister = vm::register,
                onBack     = { flow = AuthFlow.LOGIN }
            )
            AuthFlow.FORGOT   -> ForgotSheet(
                state   = state,
                onSend  = { email -> vm.forgotPassword(email) { flow = AuthFlow.LOGIN } },
                onBack  = { flow = AuthFlow.LOGIN }
            )
        }
    }
}

// ─── Background ───────────────────────────────────────────────────────────────

@Composable
private fun AuthBackground(content: @Composable BoxScope.() -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )

    LaunchedEffect(Unit) { animateIn = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0907))
    ) {
        // Single large glow circle at top — matching iOS
        Box(
            Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .offset(y = 60.dp)
                .blur(60.dp)
                .background(PiumsOrange.copy(alpha = glowAlpha), CircleShape)
        )

        // Logo area
        val logoAlpha  by animateFloatAsState(if (animateIn) 1f else 0f, tween(400), label = "logo")
        val logoScale  by animateFloatAsState(
            if (animateIn) 1f else 0.7f,
            spring(dampingRatio = 0.72f, stiffness = 220f), label = "logoScale"
        )
        val textAlpha  by animateFloatAsState(if (animateIn) 1f else 0f, tween(450, delayMillis = 150), label = "text")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.piums_logo),
                contentDescription = "Piums",
                modifier = Modifier
                    .height(56.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )
            Spacer(Modifier.height(20.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.alpha(textAlpha)
            ) {
                Text(
                    "¡Bienvenido a Piums!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "El artista perfecto para tu próximo evento",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Sliding card from bottom
        val sheetOffset by animateDpAsState(
            targetValue   = if (animateIn) 0.dp else 600.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 190f),
            label         = "sheet"
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
            Box(modifier = Modifier.offset(y = sheetOffset)) { content() }
        }
    }
}

// ─── Login Sheet (multi-step) ─────────────────────────────────────────────────

private enum class LoginStep { EMAIL, PASSWORD, SOCIAL }

@Composable
private fun LoginSheet(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onGoogle: (android.content.Context) -> Unit,
    onFacebook: (android.content.Context) -> Unit,
    onTikTok: (android.content.Context) -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit
) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var step     by remember { mutableStateOf(LoginStep.EMAIL) }
    val context  = LocalContext.current

    AuthCard {
        AnimatedContent(
            targetState  = step,
            transitionSpec = {
                val toRight = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (toRight) it else -it } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally { if (toRight) -it else it } + fadeOut(tween(180)))
            },
            label = "loginStep"
        ) { currentStep ->
            when (currentStep) {
                LoginStep.EMAIL -> EmailPanel(
                    email      = email,
                    onEmail    = { email = it },
                    error      = state.error,
                    onContinue = { step = LoginStep.PASSWORD },
                    onSocial   = { step = LoginStep.SOCIAL },
                    onRegister = onRegister
                )
                LoginStep.PASSWORD -> PasswordPanel(
                    email      = email,
                    password   = password,
                    onPassword = { password = it },
                    isLoading  = state.isLoading,
                    error      = state.error,
                    onBack     = { step = LoginStep.EMAIL },
                    onLogin    = { onLogin(email, password) },
                    onForgot   = onForgot,
                    onRegister = onRegister
                )
                LoginStep.SOCIAL -> SocialPanel(
                    error      = state.error,
                    isLoading  = state.isLoading,
                    onBack     = { step = LoginStep.EMAIL },
                    onGoogle   = { onGoogle(context) },
                    onFacebook = { onFacebook(context) },
                    onTikTok   = { onTikTok(context) },
                    onRegister = onRegister
                )
            }
        }
    }
}

// ─── Paso 1: Email ────────────────────────────────────────────────────────────

@Composable
private fun EmailPanel(
    email: String,
    onEmail: (String) -> Unit,
    error: String?,
    onContinue: () -> Unit,
    onSocial: () -> Unit,
    onRegister: () -> Unit
) {
    val isValid = isValidEmail(email)
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "Ingresar o crear cuenta",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        PiumsAuthField(
            label    = "CORREO ELECTRÓNICO",
            value    = email,
            onValueChange = onEmail,
            placeholder   = "nombre@ejemplo.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            onDone   = { if (isValid) { focusManager.clearFocus(); onContinue() } }
        )

        if (error != null) ErrorBanner(error)

        // Continuar button with arrow
        ContinueButton(enabled = isValid, onClick = { focusManager.clearFocus(); onContinue() })

        IosStyleDivider()

        // Social shortcut button
        Surface(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            color    = MaterialTheme.colorScheme.surfaceVariant,
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f)),
            onClick  = onSocial
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Continúa con Google, Facebook o TikTok",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        RegisterLink(onRegister)
    }
}

// ─── Paso 2: Password ─────────────────────────────────────────────────────────

@Composable
private fun PasswordPanel(
    email: String,
    password: String,
    onPassword: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onForgot: () -> Unit,
    onRegister: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Back + email header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackCircleButton(onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Bienvenido",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
                Text(
                    email,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        PiumsAuthField(
            label    = "CONTRASEÑA",
            value    = password,
            onValueChange = onPassword,
            placeholder   = "••••••••",
            isPassword    = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onDone   = { if (password.isNotBlank()) onLogin() }
        )

        if (error != null) ErrorBanner(error)

        PiumsGradientButton(
            text      = "Iniciar sesión",
            isLoading = isLoading,
            isEmpty   = password.isBlank(),
            onClick   = onLogin
        )

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onForgot) {
                Text(
                    "¿Olvidaste tu contraseña?",
                    color = PiumsOrange,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        RegisterLink(onRegister)
    }
}

// ─── Paso 3: Social ───────────────────────────────────────────────────────────

@Composable
private fun SocialPanel(
    error: String?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onFacebook: () -> Unit,
    onTikTok: () -> Unit,
    onRegister: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackCircleButton(onClick = onBack)
            Text(
                "Ingresar o crear cuenta con:",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton(
                icon    = { GoogleIcon() },
                label   = "Continuar con Google",
                enabled = !isLoading,
                onClick = onGoogle
            )
            SocialButton(
                icon    = { FacebookIcon() },
                label   = "Continuar con Facebook",
                badge   = "Próximamente",
                enabled = false,
                onClick = onFacebook
            )
            SocialButton(
                icon    = { TikTokIcon() },
                label   = "Continuar con TikTok",
                badge   = "Próximamente",
                enabled = false,
                onClick = onTikTok
            )
        }

        if (error != null) ErrorBanner(error)

        RegisterLink(onRegister)
    }
}

// ─── Shared card container ────────────────────────────────────────────────────

@Composable
private fun AuthCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color    = MaterialTheme.colorScheme.surface,
        border   = BorderStroke(
            width  = 1.dp,
            brush  = Brush.verticalGradient(
                listOf(PiumsOrange.copy(0.30f), Color.Transparent), endY = 200f
            )
        )
    ) {
        Column {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 14.dp), Alignment.Center) {
                Box(
                    Modifier.size(width = 36.dp, height = 4.dp)
                        .background(Color.White.copy(0.18f), RoundedCornerShape(2.dp))
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp)
                    .padding(top = 20.dp, bottom = 50.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

// ─── Reusable components ──────────────────────────────────────────────────────

@Composable
private fun ContinueButton(enabled: Boolean, onClick: () -> Unit) {
    val gradient = if (enabled)
        Brush.linearGradient(listOf(Color(0xFFD96020), Color(0xFFB84712)))
    else
        SolidColor(MaterialTheme.colorScheme.surfaceVariant)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation    = if (enabled) 14.dp else 0.dp,
                shape        = RoundedCornerShape(14.dp),
                ambientColor = PiumsOrange,
                spotColor    = PiumsOrange
            )
            .background(gradient, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Continuar",
                color      = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                fontWeight = FontWeight.Bold,
                style      = MaterialTheme.typography.bodyLarge
            )
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint     = if (enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun BackCircleButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null,
            tint     = PiumsOrange,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun SocialButton(
    icon: @Composable () -> Unit,
    label: String,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.55f),
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f)),
        onClick  = onClick,
        enabled  = enabled
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Text(
                label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (badge != null) {
                Text(
                    badge,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun GoogleIcon() {
    Box(
        modifier         = Modifier.size(26.dp).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4), fontSize = 14.sp)
    }
}

@Composable
private fun FacebookIcon() {
    Box(
        modifier         = Modifier.size(26.dp).background(Color(0xFF1877F2), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("f", fontWeight = FontWeight.Black, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun TikTokIcon() {
    Box(
        modifier         = Modifier.size(26.dp).background(Color.Black, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote, contentDescription = null,
            tint = Color.White, modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun IosStyleDivider() {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(0.2f))
        Box(
            Modifier.size(5.dp)
                .background(MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape)
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(0.2f))
    }
}

@Composable
private fun RegisterLink(onRegister: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            "¿No tienes cuenta? ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
        )
        Text(
            "Regístrate",
            style      = MaterialTheme.typography.bodySmall,
            color      = PiumsOrange,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.clickable { onRegister() }
        )
    }
}

// ─── Register Sheet ───────────────────────────────────────────────────────────

@Composable
private fun RegisterSheet(
    state: AuthUiState,
    onRegister: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var nombre          by remember { mutableStateOf("") }
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted   by remember { mutableStateOf(false) }
    val passwordsMatch  = password == confirmPassword || confirmPassword.isEmpty()
    val canSubmit = nombre.isNotBlank() && email.isNotBlank() &&
                    password.isNotBlank() && confirmPassword.isNotBlank() &&
                    passwordsMatch && termsAccepted && !state.isLoading

    AuthCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackCircleButton(onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Crear cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Únete a la comunidad Piums.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }

        PiumsAuthField(label = "NOMBRE", value = nombre, onValueChange = { nombre = it },
            placeholder = "Tu nombre completo",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next))
        PiumsAuthField(label = "CORREO ELECTRÓNICO", value = email, onValueChange = { email = it },
            placeholder = "nombre@ejemplo.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        PiumsAuthField(label = "CONTRASEÑA", value = password, onValueChange = { password = it },
            placeholder = "Mínimo 8 caracteres", isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next))
        if (password.isNotEmpty()) PasswordStrengthBar(password)
        PiumsAuthField(
            label = "CONFIRMAR CONTRASEÑA", value = confirmPassword,
            onValueChange = { confirmPassword = it },
            placeholder = "Repite tu contraseña", isPassword = true, isError = !passwordsMatch,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
        if (!passwordsMatch) {
            Text("Las contraseñas no coinciden", color = PiumsError,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it },
                colors = CheckboxDefaults.colors(
                    checkedColor  = PiumsOrange,
                    checkmarkColor = Color.White
                ))
            Text("Acepto los Términos y condiciones y la Política de privacidad de Piums.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }

        if (state.error != null) ErrorBanner(state.error)

        PiumsGradientButton(
            text      = "Crear cuenta",
            isLoading = state.isLoading,
            isEmpty   = !canSubmit,
            onClick   = { onRegister(nombre, email, password) }
        )

        Row(Modifier.fillMaxWidth(), Arrangement.Center) {
            Text("¿Ya tienes cuenta? ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Text("Inicia sesión", style = MaterialTheme.typography.bodySmall,
                color = PiumsOrange, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onBack() })
        }
    }
}

// ─── Forgot Password Sheet ────────────────────────────────────────────────────

@Composable
private fun ForgotSheet(
    state: AuthUiState,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    AuthCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackCircleButton(onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recuperar contraseña", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Te enviaremos un enlace a tu correo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }

        PiumsAuthField(label = "CORREO ELECTRÓNICO", value = email, onValueChange = { email = it },
            placeholder = "nombre@ejemplo.com",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            onDone = { if (email.isNotBlank()) onSend(email) })

        if (state.error != null) ErrorBanner(state.error)

        PiumsGradientButton(
            text      = "Enviar enlace",
            isLoading = state.isLoading,
            isEmpty   = email.isBlank(),
            onClick   = { onSend(email) }
        )
    }
}

// ─── Shared field ─────────────────────────────────────────────────────────────

@Composable
fun PiumsAuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null
) {
    var showPassword by remember { mutableStateOf(false) }
    var isFocused    by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isError   -> PiumsError.copy(0.7f)
            isFocused -> PiumsOrange.copy(0.7f)
            else      -> Color.Transparent
        },
        animationSpec = tween(200), label = "border"
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(13.dp))
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val visual = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None
            BasicTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused },
                visualTransformation = visual,
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                    inner()
                }
            )
            if (isPassword) {
                IconButton(onClick = { showPassword = !showPassword }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = if (isFocused) PiumsOrange.copy(0.8f)
                               else MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                }
            }
        }
    }
}

// ─── Gradient button ──────────────────────────────────────────────────────────

@Composable
private fun PiumsGradientButton(
    text: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    onClick: () -> Unit
) {
    val gradient = if (isEmpty || isLoading)
        SolidColor(PiumsOrange.copy(0.40f))
    else
        Brush.linearGradient(listOf(Color(0xFFD96020), Color(0xFFB84712)))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation    = if (isEmpty || isLoading) 0.dp else 14.dp,
                shape        = RoundedCornerShape(14.dp),
                ambientColor = PiumsOrange,
                spotColor    = PiumsOrange
            )
            .background(gradient, RoundedCornerShape(14.dp))
            .clickable(enabled = !isEmpty && !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = Color.White,
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Cargando…", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(text, color = if (isEmpty) Color.White.copy(0.5f) else Color.White,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PiumsError.copy(0.12f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Error, null, tint = PiumsError, modifier = Modifier.size(18.dp))
        Text(message, color = PiumsError, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordStrengthBar(password: String) {
    val strength = password.let { p ->
        var s = 0
        if (p.length >= 8) s++
        if (p.length >= 12) s++
        if (p.any { it.isUpperCase() }) s++
        if (p.any { it.isDigit() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        s
    }
    val colors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFF59E0B),
        Color(0xFF84CC16), Color(0xFF22C55E)
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            Box(
                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < strength) colors[strength - 1] else Color.Gray.copy(0.2f))
            )
        }
    }
}

private fun isValidEmail(email: String): Boolean {
    val pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    return pattern.matches(email)
}

@Composable
private fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color>,
    label: String
) = androidx.compose.animation.animateColorAsState(
    targetValue   = targetValue,
    animationSpec = animationSpec,
    label         = label
)

@Composable
private fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float>,
    label: String
) = androidx.compose.animation.core.animateFloatAsState(
    targetValue   = targetValue,
    animationSpec = animationSpec,
    label         = label
)
