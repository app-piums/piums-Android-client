package com.piums.cliente.ui.screens.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.ChangePasswordRequest
import com.piums.cliente.data.remote.dto.UpdateProfileRequest
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.ui.theme.ThemeManager
import com.piums.cliente.utils.BiometricHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.browser.customtabs.CustomTabsIntent
import com.piums.cliente.data.remote.dto.GoogleCalendarStatusResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: PiumsApiService,
    val tokenStorage: TokenStorage,
    private val themeManager: ThemeManager
) : ViewModel() {

    var userName by mutableStateOf(tokenStorage.userName ?: "")
        private set
    var userEmail by mutableStateOf(tokenStorage.userEmail ?: "")
        private set
    var avatarInitial: String get() = userName.firstOrNull()?.uppercase() ?: "P"
        private set(value) {}

    var isEditingName by mutableStateOf(false)
        private set
    var nameInput by mutableStateOf("")
        private set
    var isSavingName by mutableStateOf(false)
        private set
    var nameSaved by mutableStateOf(false)
        private set

    var showChangePassword by mutableStateOf(false)
        private set
    var currentPw by mutableStateOf("")
        private set
    var newPw by mutableStateOf("")
        private set
    var confirmPw by mutableStateOf("")
        private set
    var isSavingPw by mutableStateOf(false)
        private set
    var pwError by mutableStateOf<String?>(null)
        private set
    var pwSaved by mutableStateOf(false)
        private set

    var isLoggingOut by mutableStateOf(false)
        private set

    var biometricEnabled by mutableStateOf(tokenStorage.biometricEnabled)
        private set
    var isDarkMode by mutableStateOf(themeManager.forcedDark)
        private set
    var avatarUrl by mutableStateOf(tokenStorage.avatarUrl)
        private set
    var isUploadingAvatar by mutableStateOf(false)
        private set
    var isDeletingAccount by mutableStateOf(false)
        private set
    var deleteAccountError by mutableStateOf<String?>(null)
        private set

    fun toggleBiometric(enabled: Boolean) {
        tokenStorage.biometricEnabled = enabled
        biometricEnabled = enabled
    }

    fun toggleDarkMode() {
        val next = !isDarkMode
        themeManager.setForceDark(next)
        isDarkMode = next
    }


    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            isUploadingAvatar = true
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestBody)
                val resp = api.uploadAvatar(part)
                resp.url?.let {
                    tokenStorage.avatarUrl = it
                    avatarUrl = it
                }
            }
            isUploadingAvatar = false
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        val userId = tokenStorage.userId ?: return
        viewModelScope.launch {
            isDeletingAccount = true
            deleteAccountError = null
            runCatching { api.deleteUser(userId) }
                .onSuccess {
                    tokenStorage.clear()
                    onDone()
                }
                .onFailure { deleteAccountError = "No se pudo eliminar la cuenta. Intenta de nuevo." }
            isDeletingAccount = false
        }
    }

    fun startEditName() {
        nameInput = userName
        isEditingName = true
        nameSaved = false
    }

    fun onNameInputChange(v: String) { nameInput = v }

    fun saveName() {
        if (nameInput.isBlank()) return
        viewModelScope.launch {
            isSavingName = true
            runCatching { api.updateProfile(UpdateProfileRequest(nombre = nameInput)) }
                .onSuccess {
                    tokenStorage.userName = nameInput
                    userName = nameInput
                    nameSaved = true
                }
            isSavingName = false
            isEditingName = false
        }
    }

    fun cancelEditName() { isEditingName = false }

    fun onCurrentPwChange(v: String) { currentPw = v; pwError = null }
    fun onNewPwChange(v: String) { newPw = v; pwError = null }
    fun onConfirmPwChange(v: String) { confirmPw = v; pwError = null }

    fun showPasswordDialog(show: Boolean) {
        showChangePassword = show
        if (!show) { currentPw = ""; newPw = ""; confirmPw = ""; pwError = null; pwSaved = false }
    }

    fun changePassword() {
        if (newPw != confirmPw) { pwError = "Las contraseñas no coinciden"; return }
        if (newPw.length < 8) { pwError = "Mínimo 8 caracteres"; return }
        viewModelScope.launch {
            isSavingPw = true
            pwError = null
            runCatching {
                api.changePassword(ChangePasswordRequest(currentPassword = currentPw, newPassword = newPw))
            }.onSuccess {
                pwSaved = true
                showChangePassword = false
            }.onFailure {
                pwError = "Contraseña actual incorrecta"
            }
            isSavingPw = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            isLoggingOut = true
            runCatching { api.logout() }
            tokenStorage.clear()
            isLoggingOut = false
            onDone()
        }
    }

    // ── Google Calendar ───────────────────────────────────────────────────────
    private val _calendarState = MutableStateFlow(GoogleCalendarStatusResponse(connected = false))
    val calendarState: StateFlow<GoogleCalendarStatusResponse> = _calendarState.asStateFlow()

    init { loadCalendarStatus() }

    fun loadCalendarStatus() {
        viewModelScope.launch {
            runCatching { api.googleCalendarStatus() }
                .onSuccess { _calendarState.value = it }
        }
    }

    fun connectGoogleCalendar(context: Context) {
        val token = tokenStorage.accessToken ?: return
        val url = "https://backend.piums.io/api/auth/google/calendar-connect?token=$token"
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }

    fun disconnectCalendar() {
        viewModelScope.launch {
            runCatching { api.googleCalendarDisconnect() }
                .onSuccess { _calendarState.value = GoogleCalendarStatusResponse(connected = false) }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onPaymentsClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onCouponsClick: () -> Unit = {},
    onNotifPrefsClick: () -> Unit = {},
    onDisputesClick: () -> Unit = {},
    onTutorialClick: () -> Unit = {},
    onIdentityVerificationClick: () -> Unit = {},
    vm: ProfileViewModel = hiltViewModel()
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val calendarState by vm.calendarState.collectAsState()
    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.uploadAvatar(context, it) } }

    val activity       = LocalContext.current as FragmentActivity
    val biometricHelper = remember { BiometricHelper(activity) }
    val biometricAvailable = remember { biometricHelper.canAuthenticate() || vm.biometricEnabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(PiumsOrange.copy(0.20f), Color.Transparent))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(PiumsOrange.copy(0.15f))
                        .border(2.dp, PiumsOrange, CircleShape)
                        .clickable {
                            avatarLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.avatarUrl != null) {
                        AsyncImage(
                            model = vm.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Text(
                            vm.userName.firstOrNull()?.uppercase() ?: "P",
                            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PiumsOrange
                        )
                    }
                    if (vm.isUploadingAvatar) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White,
                                modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    // Camera overlay hint
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .size(22.dp).clip(CircleShape)
                            .background(PiumsOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, null,
                            tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                if (vm.isEditingName) {
                    OutlinedTextField(
                        value = vm.nameInput,
                        onValueChange = vm::onNameInputChange,
                        singleLine = true,
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth(.7f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PiumsOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::cancelEditName) { Text("Cancelar") }
                        TextButton(
                            onClick = vm::saveName,
                            enabled = vm.nameInput.isNotBlank() && !vm.isSavingName
                        ) {
                            if (vm.isSavingName) CircularProgressIndicator(
                                color = PiumsOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Guardar", color = PiumsOrange)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(vm.userName.ifBlank { "Usuario" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground)
                        IconButton(onClick = vm::startEditName, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null,
                                tint = PiumsOrange, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(vm.userEmail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Account section
        ProfileSectionTitle("Cuenta")
        ProfileItem(
            icon  = Icons.Default.Lock,
            label = "Cambiar contraseña",
            onClick = { vm.showPasswordDialog(true) }
        )
        // Google Calendar
        GoogleCalendarRow(
            isConnected = calendarState.connected,
            email       = calendarState.email ?: calendarState.calendarEmail,
            onConnect   = { vm.connectGoogleCalendar(context) },
            onDisconnect = { vm.disconnectCalendar() }
        )
        ProfileItem(
            icon  = Icons.Default.CreditCard,
            label = "Historial de pagos",
            onClick = onPaymentsClick
        )
        ProfileItem(
            icon  = Icons.Default.CreditCard,
            label = "Tarjetas guardadas",
            onClick = onWalletClick
        )
        ProfileItemWithBadge(
            icon  = Icons.Default.VerifiedUser,
            label = "Verificar identidad",
            badge = when (vm.tokenStorage.identityVerificationStatus) {
                "approved" -> "Verificado" to Color(0xFF22C55E)
                "pending"  -> "Pendiente" to Color(0xFFF59E0B)
                else       -> null
            },
            onClick = onIdentityVerificationClick
        )

        // Biometric toggle — only shown when hardware is available
        if (biometricAvailable) {
            ProfileBiometricRow(
                enabled  = vm.biometricEnabled,
                onToggle = { enable ->
                    if (enable) {
                        biometricHelper.authenticate(
                            title    = "Activar desbloqueo biométrico",
                            subtitle = "Confirma tu identidad para activar esta opción",
                            onSuccess = { vm.toggleBiometric(true) }
                        )
                    } else {
                        vm.toggleBiometric(false)
                    }
                }
            )
        }

        // Dark mode toggle
        ProfileToggleRow(
            icon    = Icons.Default.DarkMode,
            label   = "Modo oscuro",
            enabled = vm.isDarkMode,
            onToggle = { vm.toggleDarkMode() }
        )

        Spacer(Modifier.height(8.dp))

        // App section
        ProfileSectionTitle("Ayuda y soporte")
        ProfileItem(
            icon  = Icons.Default.Gavel,
            label = "Mis quejas",
            onClick = onDisputesClick
        )
        ProfileItem(
            icon  = Icons.Default.NotificationsNone,
            label = "Preferencias de notificaciones",
            onClick = onNotifPrefsClick
        )
        ProfileItem(
            icon  = Icons.Default.HelpOutline,
            label = "¿Cómo funciona Piums?",
            onClick = onTutorialClick
        )
        ProfileItem(
            icon  = Icons.Default.Article,
            label = "Términos y condiciones",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://piums.io/terminos")))
            }
        )
        ProfileItem(
            icon  = Icons.Default.Shield,
            label = "Política de privacidad",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://piums.io/privacidad")))
            }
        )
        ProfileItem(
            icon  = Icons.Default.SupportAgent,
            label = "Soporte",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("mailto:soporte@piums.io")))
            }
        )
        ProfileItem(
            icon  = Icons.Default.Info,
            label = "Versión 1.0.0",
            onClick = {},
            showChevron = false,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.4f)
        )

        Spacer(Modifier.height(16.dp))

        // Logout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error.copy(0.08f))
                .clickable(enabled = !vm.isLoggingOut) { showLogoutConfirm = true }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vm.isLoggingOut) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Logout, null,
                        tint = MaterialTheme.colorScheme.error)
                }
                Text("Cerrar sesión", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Delete account
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error.copy(0.04f))
                .clickable(enabled = !vm.isDeletingAccount) { showDeleteConfirm = true }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (vm.isDeletingAccount) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.DeleteForever, null,
                        tint = MaterialTheme.colorScheme.error.copy(0.6f))
                }
                Text("Eliminar cuenta", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error.copy(0.6f))
            }
        }

        vm.deleteAccountError?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp))
        }

        Spacer(Modifier.height(32.dp))
    }

    // Password dialog
    if (vm.showChangePassword) {
        AlertDialog(
            onDismissRequest = { vm.showPasswordDialog(false) },
            title = { Text("Cambiar contraseña", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PasswordField("Contraseña actual", vm.currentPw, vm::onCurrentPwChange)
                    PasswordField("Nueva contraseña", vm.newPw, vm::onNewPwChange)
                    PasswordField("Confirmar contraseña", vm.confirmPw, vm::onConfirmPwChange)
                    vm.pwError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::changePassword,
                    enabled = vm.currentPw.isNotBlank() && vm.newPw.isNotBlank()
                        && vm.confirmPw.isNotBlank() && !vm.isSavingPw) {
                    if (vm.isSavingPw) CircularProgressIndicator(color = PiumsOrange,
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Cambiar", color = PiumsOrange, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showPasswordDialog(false) }) { Text("Cancelar") }
            }
        )
    }

    // Logout confirm
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("¿Cerrar sesión?") },
            text  = { Text("Se cerrará tu sesión en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    vm.logout(onLogout)
                }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    // Delete account confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar cuenta?", fontWeight = FontWeight.Bold) },
            text  = {
                Text("Esta acción es permanente. Se eliminarán todos tus datos, reservas e historial de pagos.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteAccount(onLogout)
                    },
                    enabled = !vm.isDeletingAccount
                ) {
                    Text("Eliminar permanentemente", color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(0.45f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun ProfileItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onBackground)
            PiumsOrange else tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = tint, modifier = Modifier.weight(1f))
        if (showChevron) {
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
                modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

@Composable
private fun ProfileItemWithBadge(
    icon: ImageVector,
    label: String,
    badge: Pair<String, Color>?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badge.second.copy(alpha = 0.12f)
            ) {
                Text(badge.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = badge.second,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onBackground.copy(0.3f),
            modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

@Composable
private fun ProfileToggleRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(20.dp))
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = PiumsOrange,
                checkedTrackColor = PiumsOrange.copy(alpha = 0.3f))
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

@Composable
private fun ProfileBiometricRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Default.Fingerprint, null,
            tint = PiumsOrange, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Desbloqueo biométrico",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text("Usa tu huella digital al iniciar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.45f))
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = PiumsOrange,
                checkedTrackColor = PiumsOrange.copy(alpha = 0.3f))
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.07f)
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible)
            androidx.compose.ui.text.input.VisualTransformation.None
        else
            androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    null, tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PiumsOrange,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f)
        )
    )
}

@Composable
private fun GoogleCalendarRow(
    isConnected: Boolean,
    email: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.CalendarToday, contentDescription = null,
            tint = PiumsOrange, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Google Calendar", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(
                text = if (isConnected) email ?: "Conectado" else "No conectado",
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected) Color(0xFF22C55E)
                        else MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
        TextButton(
            onClick = if (isConnected) onDisconnect else onConnect,
            colors = ButtonDefaults.textButtonColors(contentColor = PiumsOrange)
        ) {
            Text(if (isConnected) "Desconectar" else "Conectar",
                style = MaterialTheme.typography.labelMedium)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outline.copy(0.12f)
    )
}
