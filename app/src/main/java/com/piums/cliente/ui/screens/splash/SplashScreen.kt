package com.piums.cliente.ui.screens.splash

import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.piums.cliente.data.local.TokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenStorage: TokenStorage
) : ViewModel() {
    var destination by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            destination = when {
                !tokenStorage.isLoggedIn      -> "auth"
                tokenStorage.biometricEnabled -> "biometric"
                else                          -> "main"
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SplashScreen(
    onLoggedIn:      () -> Unit,
    onBiometricGate: () -> Unit,
    onNotLoggedIn:   () -> Unit,
    vm: SplashViewModel = hiltViewModel()
) {
    val destination = vm.destination
    var videoEnded by remember { mutableStateOf(false) }

    LaunchedEffect(destination, videoEnded) {
        if (destination != null && videoEnded) {
            when (destination) {
                "main"      -> onLoggedIn()
                "biometric" -> onBiometricGate()
                "auth"      -> onNotLoggedIn()
            }
        }
    }

    // Failsafe de 8s igual que iOS
    LaunchedEffect(Unit) {
        delay(8_000)
        videoEnded = true
    }

    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/raw/piumssplash")
            setMediaItem(MediaItem.fromUri(uri))
            setPlaybackSpeed(2f)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) videoEnded = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        }
    )
}
