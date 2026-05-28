package com.piums.cliente.utils

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

class BiometricHelper(private val activity: FragmentActivity) {

    fun canAuthenticate(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        title:              String = "Verificar identidad",
        subtitle:           String = "Usa tu huella digital para acceder a Piums",
        negativeButtonText: String = "Cancelar",
        onSuccess: () -> Unit,
        onError:   (String) -> Unit = {},
        onFailed:  () -> Unit = {}
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        BiometricPrompt(
            activity,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onError(errString.toString())
                override fun onAuthenticationFailed() =
                    onFailed()
            }
        ).authenticate(promptInfo)
    }
}
