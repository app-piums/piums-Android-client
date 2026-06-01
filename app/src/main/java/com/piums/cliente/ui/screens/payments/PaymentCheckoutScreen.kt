package com.piums.cliente.ui.screens.payments

import android.net.Uri
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.remote.PiumsApiService
import com.piums.cliente.data.remote.dto.*
import com.piums.cliente.utils.toUserMessage
import com.piums.cliente.ui.theme.PiumsOrange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ─── Phase ────────────────────────────────────────────────────────────────────

sealed class PaymentPhase {
    object Ready      : PaymentPhase()
    object Loading    : PaymentPhase()
    object Processing : PaymentPhase()
    object Confirmed  : PaymentPhase()
    object Declined   : PaymentPhase()
    data class Error(val message: String) : PaymentPhase()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class PaymentCheckoutViewModel @Inject constructor(
    private val api: PiumsApiService,
    private val tokenStorage: TokenStorage,
    savedState: SavedStateHandle
) : ViewModel() {

    val bookingId: String = checkNotNull(savedState["bookingId"])

    var booking by mutableStateOf<BookingDto?>(null)
        private set
    var artist by mutableStateOf<ArtistDto?>(null)
        private set
    var phase by mutableStateOf<PaymentPhase>(PaymentPhase.Ready)
        private set
    var showWebView by mutableStateOf(false)
        private set
    var redirectUrl by mutableStateOf<String?>(null)
        private set
    var confirmedBooking by mutableStateOf<BookingDto?>(null)
        private set

    val amountToPay: Int get() {
        val b = booking ?: return 0
        return if (b.anticipoRequired == true && b.anticipoAmount != null)
            b.anticipoAmount else b.totalPrice
    }

    val isBusy: Boolean get() =
        phase == PaymentPhase.Loading || phase == PaymentPhase.Processing

    val errorText: String? get() = (phase as? PaymentPhase.Error)?.message

    init { loadBooking() }

    private fun loadBooking() {
        viewModelScope.launch {
            runCatching { api.getBooking(bookingId) }.onSuccess { b ->
                booking = b
                // Load artist to get countryCode (needed for Tilopay routing)
                runCatching { api.getArtist(b.artistId) }.onSuccess { artist = it.resolved }
            }
        }
    }

    private fun splitName(fullName: String?): Pair<String?, String?> {
        val name = fullName?.trim()?.takeIf { it.isNotEmpty() } ?: return Pair(null, null)
        val parts = name.split(" ").filter { it.isNotEmpty() }
        if (parts.size == 1) return Pair(parts[0], null)
        return Pair(parts.dropLast(1).joinToString(" "), parts.last())
    }

    fun startPayment() {
        val b = booking ?: return
        val (billingFirst, billingLast) = splitName(tokenStorage.userName)
        viewModelScope.launch {
            phase = PaymentPhase.Loading
            runCatching {
                api.createPaymentIntent(PaymentIntentRequest(
                    bookingId    = bookingId,
                    amount       = amountToPay.takeIf { it > 0 },
                    currency     = b.currency ?: "USD",
                    countryCode  = artist?.country,
                    billingFirst = billingFirst,
                    billingLast  = billingLast
                ))
            }.onSuccess { response ->
                val intent = response.resolved
                when {
                    intent == null ->
                        phase = PaymentPhase.Error("No se pudo iniciar el pago. Intenta de nuevo.")
                    (intent.isTilopay || intent.provider?.uppercase() == "TILOPAY") && intent.redirectUrl != null -> {
                        redirectUrl = intent.redirectUrl
                        phase = PaymentPhase.Ready
                        showWebView = true
                    }
                    intent.redirectUrl != null -> {
                        // fallback: cualquier redirect URL se abre en WebView
                        redirectUrl = intent.redirectUrl
                        phase = PaymentPhase.Ready
                        showWebView = true
                    }
                    else ->
                        phase = PaymentPhase.Error("Método de pago no disponible en este momento.")
                }
            }.onFailure {
                phase = PaymentPhase.Error(it.toUserMessage())
            }
        }
    }

    fun handleCallback(params: TilopayCallbackParams) {
        showWebView = false
        if (params.isApproved) {
            phase = PaymentPhase.Processing
            viewModelScope.launch { confirmAndPoll(params) }
        } else {
            phase = PaymentPhase.Declined
        }
    }

    fun dismissWebView() {
        showWebView = false
        phase = PaymentPhase.Ready
    }

    fun retryPayment() {
        phase = PaymentPhase.Ready
        startPayment()
    }

    private suspend fun confirmAndPoll(params: TilopayCallbackParams) {
        runCatching {
            api.confirmTilopayRedirect(TilopayConfirmRequest(
                bookingId    = params.bookingId,
                responseCode = params.responseCode,
                orderNumber  = params.orderNumber,
                amount       = params.amount,
                auth         = params.auth,
                currency     = params.currency,
                orderHash    = params.orderHash
            ))
        }
        pollUntilPaid(params.bookingId)
    }

    private suspend fun pollUntilPaid(bId: String, attempt: Int = 0) {
        if (attempt >= 10) { phase = PaymentPhase.Confirmed; return }
        delay(3_000)
        val b = runCatching { api.getBooking(bId) }.getOrNull()
        if (b != null) {
            val paid = b.paymentStatus == "ANTICIPO_PAID" ||
                       b.paymentStatus == "FULLY_PAID"    ||
                       b.paymentStatus == "COMPLETED"     ||
                       b.status == "CONFIRMED"            ||
                       b.status == "COMPLETED"
            if (paid) { confirmedBooking = b; phase = PaymentPhase.Confirmed; return }
        }
        pollUntilPaid(bId, attempt + 1)
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun PaymentCheckoutScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: PaymentCheckoutViewModel = hiltViewModel()
) {
    if (vm.showWebView) {
        vm.redirectUrl?.let { url ->
            TilopayWebSheet(
                url         = url,
                onCallback  = vm::handleCallback,
                onDismiss   = vm::dismissWebView
            )
            return
        }
    }

    when (vm.phase) {
        is PaymentPhase.Confirmed  -> PaymentSuccessScreen(vm.confirmedBooking ?: vm.booking, onDone = onDone, onBack = onBack)
        is PaymentPhase.Declined   -> PaymentDeclinedScreen(onRetry = vm::retryPayment, onBack = onBack)
        is PaymentPhase.Processing -> ProcessingScreen()
        else -> CheckoutContent(vm = vm, onBack = onBack)
    }
}

// ─── Checkout main ────────────────────────────────────────────────────────────

@Composable
private fun CheckoutContent(vm: PaymentCheckoutViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Confirmar Pago",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val booking = vm.booking

            // Booking summary card
            if (booking != null) {
                BookingSummaryCard(booking)
                PriceBreakdownCard(booking, vm.amountToPay)
            } else {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PiumsOrange, modifier = Modifier.size(28.dp))
                }
            }

            // Security badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null,
                    tint = Color(0xFF22C55E), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pago seguro · Procesado por Tilopay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }

            // Error
            vm.errorText?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(msg, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Pay button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (!vm.isBusy && vm.booking != null) PiumsOrange
                        else MaterialTheme.colorScheme.outline.copy(0.3f)
                    )
                    .clickable(enabled = !vm.isBusy && vm.booking != null, onClick = vm::startPayment),
                contentAlignment = Alignment.Center
            ) {
                if (vm.isBusy) {
                    CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CreditCard, null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            if (vm.booking != null) "Pagar ${formatCents(vm.amountToPay, vm.booking?.currency)}"
                            else "Pagar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

// ─── Cards ────────────────────────────────────────────────────────────────────

@Composable
private fun BookingSummaryCard(booking: BookingDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resumen de reserva",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

        // Artist initials + name
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PiumsOrange.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (booking.resolvedArtistName ?: "AR").take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PiumsOrange
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(booking.resolvedArtistName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                booking.serviceName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }
        }

        // Date + time
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.size(14.dp))
                Text(formatDate(booking.scheduledDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
            booking.scheduledTime?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        modifier = Modifier.size(14.dp))
                    Text(it.take(5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }
        }
    }
}

@Composable
private fun PriceBreakdownCard(booking: BookingDto, amountToPay: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Desglose de pago",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))

        val currency = booking.currency
        if (booking.anticipoRequired == true && booking.anticipoAmount != null) {
            val rest = booking.totalPrice - booking.anticipoAmount
            PriceRow("Servicio", formatCents(booking.totalPrice, currency))
            PriceRow("Anticipo (50%)", formatCents(booking.anticipoAmount, currency),
                highlight = true)
            PriceRow("Saldo restante", formatCents(rest, currency),
                note = "Se cobra automáticamente 72h antes", dimmed = true)
        } else {
            PriceRow("Servicio", formatCents(booking.totalPrice, currency))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total a pagar ahora",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(formatCents(amountToPay, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PiumsOrange)
        }
    }
}

@Composable
private fun PriceRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    note: String? = null,
    dimmed: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (dimmed) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        else MaterialTheme.colorScheme.onSurface)
            note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) PiumsOrange
                    else if (dimmed) MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    else MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Success ──────────────────────────────────────────────────────────────────

@Composable
private fun PaymentSuccessScreen(booking: BookingDto?, onDone: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF22C55E).copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, null,
                tint = Color(0xFF22C55E), modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("¡Pago exitoso!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Tu reserva está confirmada. El artista ha sido notificado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
            textAlign = TextAlign.Center)

        booking?.code?.let { code ->
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PiumsOrange.copy(0.08f))
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("CÓDIGO DE RESERVA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    letterSpacing = 1.2.sp)
                Text(code,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PiumsOrange)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text("Ver mis reservas", color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Ir al inicio", color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
        }
    }
}

// ─── Declined ─────────────────────────────────────────────────────────────────

@Composable
private fun PaymentDeclinedScreen(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Cancel, null,
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Pago no procesado",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Tu tarjeta no fue cargada. Verifica los datos e intenta nuevamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(0.55f),
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PiumsOrange)
                .clickable(onClick = onRetry),
            contentAlignment = Alignment.Center
        ) {
            Text("Intentar de nuevo", color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("Cancelar", color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
        }
    }
}

// ─── Processing ───────────────────────────────────────────────────────────────

@Composable
private fun ProcessingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = PiumsOrange, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(20.dp))
        Text("Procesando pago...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text("Esto puede tomar unos segundos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
    }
}

// ─── Tilopay WebView ──────────────────────────────────────────────────────────

@Composable
private fun TilopayWebSheet(
    url: String,
    onCallback: (TilopayCallbackParams) -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Pago Seguro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    @Suppress("DEPRECATION")
                    settings.apply {
                        javaScriptEnabled    = true
                        domStorageEnabled    = true
                        loadWithOverviewMode = true
                        useWideViewPort      = true
                        setSupportZoom(true)
                        builtInZoomControls  = false
                        displayZoomControls  = false
                        mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString      = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = object : WebViewClient() {

                        // Intercept the Tilopay return URL (both old and new API)
                        override fun shouldOverrideUrlLoading(
                            view: WebView, request: WebResourceRequest
                        ): Boolean = interceptUrl(request.url)

                        @Suppress("OverridingDeprecatedMember", "DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, urlStr: String): Boolean =
                            interceptUrl(Uri.parse(urlStr))

                        private fun interceptUrl(uri: Uri?): Boolean {
                            val host = uri?.host ?: return false
                            val path = uri.path  ?: return false
                            if ((host.contains("piums") || host.contains("localhost")) &&
                                path.contains("/booking/confirmation/")
                            ) {
                                val params = uri.queryParameterNames.associateWith {
                                    uri.getQueryParameter(it) ?: ""
                                }
                                val bid = path.split("/").lastOrNull { it.isNotEmpty() } ?: ""
                                val result = TilopayCallbackParams(
                                    bookingId    = bid,
                                    responseCode = params["responseCode"] ?: params["code"] ?: "",
                                    orderNumber  = params["orderNumber"]  ?: params["tpt"]  ?: "",
                                    amount       = params["amount"]       ?: "",
                                    auth         = params["auth"],
                                    currency     = params["currency"],
                                    orderHash    = params["orderHash"]
                                )
                                onCallback(result)
                                return true
                            }
                            return false
                        }

                        // Accept any SSL cert — payment pages sometimes use intermediate certs
                        override fun onReceivedSslError(
                            view: WebView, handler: SslErrorHandler, error: SslError
                        ) = handler.proceed()
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatCents(cents: Int, currency: String?): String {
    val amount = cents / 100.0
    return when (currency?.uppercase()) {
        "USD" -> "$%.2f".format(amount)
        else  -> "$%.2f".format(amount)
    }
}

private fun formatDate(raw: String): String {
    return try {
        val d = LocalDate.parse(raw.take(10))
        val fmt = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "ES"))
        d.format(fmt)
    } catch (_: Exception) { raw.take(10) }
}
