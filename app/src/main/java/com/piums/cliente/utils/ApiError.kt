package com.piums.cliente.utils

import org.json.JSONObject
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Converts any Throwable into a user-facing Spanish message.
 * Mirrors iOS AppError.errorDescription — same codes, same strings.
 */
fun Throwable.toUserMessage(): String {
    // Network / connectivity
    if (this is UnknownHostException || this is ConnectException ||
        message?.contains("Unable to resolve host") == true) {
        return "Sin conexión a internet. Verifica tu red e intenta de nuevo."
    }
    if (this is SocketTimeoutException) {
        return "La conexión tardó demasiado. Intenta de nuevo."
    }

    // HTTP errors from Retrofit
    if (this is HttpException) {
        val backendMsg = runCatching {
            response()?.errorBody()?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { JSONObject(it).optString("message").takeIf { m -> m.isNotBlank() } }
        }.getOrNull()

        return when (code()) {
            400  -> backendMsg ?: "Datos inválidos. Verifica la información ingresada."
            401  -> backendMsg ?: "Credenciales incorrectas. Verifica tu correo y contraseña."
            403  -> backendMsg ?: "No tienes permiso para realizar esta acción."
            404  -> backendMsg ?: "Recurso no encontrado."
            409  -> backendMsg ?: "Este correo ya está registrado."
            422  -> backendMsg ?: "Datos inválidos. Verifica la información ingresada."
            429  -> "Demasiados intentos. Espera un momento antes de continuar."
            in 500..599 -> "Error del servidor. Intenta más tarde."
            else -> backendMsg ?: "Error ${code()}. Intenta de nuevo."
        }
    }

    // Generic fallback — avoid leaking raw Java exception messages
    return "Ocurrió un error inesperado. Intenta de nuevo."
}
