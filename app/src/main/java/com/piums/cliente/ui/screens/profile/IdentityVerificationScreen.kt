package com.piums.cliente.ui.screens.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class IdentityVerificationViewModel @Inject constructor(
    private val api: PiumsApiService,
    val tokenStorage: TokenStorage
) : ViewModel() {

    var dpiFrontDone  by mutableStateOf(tokenStorage.dpiVerificationFrontDone)  ; private set
    var dpiBackDone   by mutableStateOf(tokenStorage.dpiVerificationBackDone)   ; private set
    var selfieDone    by mutableStateOf(tokenStorage.dpiVerificationSelfieDone) ; private set

    var isUploadingFront  by mutableStateOf(false) ; private set
    var isUploadingBack   by mutableStateOf(false) ; private set
    var isUploadingSelfie by mutableStateOf(false) ; private set

    var errorMessage by mutableStateOf<String?>(null) ; private set

    val verificationStatus: String get() = tokenStorage.identityVerificationStatus
    val allUploaded: Boolean       get() = dpiFrontDone && dpiBackDone && selfieDone

    init {
        viewModelScope.launch {
            runCatching {
                val me = api.getMe().toUserDto()
                if (me.isVerified == true) tokenStorage.identityVerificationStatus = "approved"
            }
        }
    }

    fun uploadDocument(context: Context, uri: Uri, folder: String) {
        viewModelScope.launch {
            errorMessage = null
            when (folder) {
                "dpi_front" -> isUploadingFront = true
                "dpi_back"  -> isUploadingBack  = true
                "selfie"    -> isUploadingSelfie = true
            }
            runCatching {
                // Comprimir a JPEG: el backend solo acepta JPG/PNG/WebP y máximo 5MB
                val bytes = com.piums.cliente.utils.ImageUtils.compressedJpeg(context, uri)
                val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "document.jpg", body)
                api.uploadDocument(folder = folder, file = part)
                when (folder) {
                    "dpi_front" -> { tokenStorage.dpiVerificationFrontDone = true; dpiFrontDone = true }
                    "dpi_back"  -> { tokenStorage.dpiVerificationBackDone  = true; dpiBackDone  = true }
                    "selfie"    -> { tokenStorage.dpiVerificationSelfieDone = true; selfieDone  = true }
                }
            }.onFailure {
                errorMessage = "Error al subir el documento. Intenta de nuevo."
            }
            when (folder) {
                "dpi_front" -> isUploadingFront = false
                "dpi_back"  -> isUploadingBack  = false
                "selfie"    -> isUploadingSelfie = false
            }
        }
    }

    fun submitVerification() {
        if (!allUploaded) return
        tokenStorage.identityVerificationStatus = "pending"
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationScreen(
    onBack: () -> Unit = {},
    vm: IdentityVerificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var pendingFolder by remember { mutableStateOf<String?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { vm.uploadDocument(context, it, pendingFolder ?: return@let) }
        pendingFolder = null
    }

    fun launchPicker(folder: String) {
        pendingFolder = folder
        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verificar identidad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status badge
            val (statusLabel, statusColor) = when (vm.verificationStatus) {
                "approved" -> "Verificado" to Color(0xFF22C55E)
                "pending"  -> "Pendiente de revisión" to Color(0xFFF59E0B)
                else       -> "No enviado" to MaterialTheme.colorScheme.onBackground.copy(0.4f)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusIcon = when (vm.verificationStatus) {
                            "approved" -> Icons.Default.Verified
                            "pending"  -> Icons.Default.HourglassEmpty
                            else       -> Icons.Default.Info
                        }
                        Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(16.dp))
                        Text(statusLabel, color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Info card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PiumsOrange.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, null, tint = PiumsOrange,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp))
                    Text(
                        "Sube una foto del frente y reverso de tu DPI, más una selfie sosteniendo tu DPI. " +
                        "La verificación puede tardar hasta 24 horas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Upload cards
            VerificationDocumentCard(
                icon         = Icons.Default.CreditCard,
                title        = "DPI — Frente",
                description  = "Foto clara del frente de tu documento",
                isDone       = vm.dpiFrontDone,
                isUploading  = vm.isUploadingFront,
                isDisabled   = vm.verificationStatus == "approved" || vm.verificationStatus == "pending",
                onUploadClick = { launchPicker("dpi_front") }
            )
            VerificationDocumentCard(
                icon         = Icons.Default.CreditCard,
                title        = "DPI — Reverso",
                description  = "Foto clara del reverso de tu documento",
                isDone       = vm.dpiBackDone,
                isUploading  = vm.isUploadingBack,
                isDisabled   = vm.verificationStatus == "approved" || vm.verificationStatus == "pending",
                onUploadClick = { launchPicker("dpi_back") }
            )
            VerificationDocumentCard(
                icon         = Icons.Default.Face,
                title        = "Selfie con DPI",
                description  = "Una foto tuya sosteniendo tu documento",
                isDone       = vm.selfieDone,
                isUploading  = vm.isUploadingSelfie,
                isDisabled   = vm.verificationStatus == "approved" || vm.verificationStatus == "pending",
                onUploadClick = { launchPicker("selfie") }
            )

            // Error
            vm.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            // Submit button
            if (vm.verificationStatus != "approved" && vm.verificationStatus != "pending") {
                Button(
                    onClick = { vm.submitVerification() },
                    enabled = vm.allUploaded,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PiumsOrange)
                ) {
                    Text("Enviar verificación", fontWeight = FontWeight.SemiBold)
                }
            }

            if (vm.verificationStatus == "pending") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HourglassEmpty, null,
                            tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Text("Tus documentos están siendo revisados. Recibirás una notificación cuando se complete la verificación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                            lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Document card ────────────────────────────────────────────────────────────

@Composable
private fun VerificationDocumentCard(
    icon: ImageVector,
    title: String,
    description: String,
    isDone: Boolean,
    isUploading: Boolean,
    isDisabled: Boolean,
    onUploadClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isDone) Color(0xFF22C55E).copy(0.12f) else PiumsOrange.copy(0.10f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
                } else {
                    Icon(icon, null, tint = PiumsOrange, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (isDone) "Subido correctamente" else description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDone) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            if (!isDisabled) {
                if (isUploading) {
                    CircularProgressIndicator(color = PiumsOrange,
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedIconButton(
                        onClick = onUploadClick,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDone) Color(0xFF22C55E).copy(0.5f) else PiumsOrange.copy(0.5f)
                        )
                    ) {
                        Icon(
                            if (isDone) Icons.Default.Edit else Icons.Default.Upload,
                            null,
                            tint = if (isDone) Color(0xFF22C55E) else PiumsOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
