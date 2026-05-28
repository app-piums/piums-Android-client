package com.piums.cliente.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

class GoogleSignInCancelled : Exception("Inicio de sesión cancelado. Selecciona tu cuenta para continuar.")
class GoogleSignInNoCredential : Exception("No se pudo completar el inicio de sesión con Google. Inténtalo de nuevo.")

/**
 * Google Sign-In via Credential Manager using [GetSignInWithGoogleOption].
 * This flow works with the Web Client ID and does NOT require SHA-1 for debug builds,
 * unlike GetGoogleIdOption.
 */
@Singleton
class GoogleSignInHelper @Inject constructor() {

    suspend fun getFirebaseIdToken(activityContext: Context): String {
        val googleIdToken = getGoogleIdToken(activityContext)

        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()

        return FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("No se pudo obtener el Firebase ID token")
    }

    private suspend fun getGoogleIdToken(activityContext: Context): String {
        val credentialManager = CredentialManager.create(activityContext)

        val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val response = try {
            credentialManager.getCredential(activityContext, request)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Selector de Google descartado: ${e.type} — ${e.message}")
            throw GoogleSignInCancelled()
        } catch (e: NoCredentialException) {
            Log.e(TAG, "NoCredentialException: ${e.message}", e)
            throw GoogleSignInNoCredential()
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.type} — ${e.message}", e)
            throw GoogleSignInNoCredential()
        }

        return extractIdToken(response)
    }

    private fun extractIdToken(response: androidx.credentials.GetCredentialResponse): String {
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Tipo de credencial no compatible: ${credential.type}")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google ID Token inválido", e)
        }
    }

    companion object {
        const val WEB_CLIENT_ID =
            "967320828042-jo8cr62e61a1ho5k8h1aj51okvqudg7q.apps.googleusercontent.com"
        private const val TAG = "GoogleSignInHelper"
    }
}
