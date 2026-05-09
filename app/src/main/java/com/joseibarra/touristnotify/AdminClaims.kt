package com.joseibarra.touristnotify

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Verifica permisos administrativos vía custom claims de Firebase Auth.
 *
 * El claim `admin` solo puede ser establecido desde un entorno servidor con
 * Firebase Admin SDK (ver `.claude/docs/admin_setup.md`). El cliente no puede
 * falsificarlo.
 *
 * Las llamadas son `suspend` porque el ID token puede requerir refresco de red.
 * Se cachea por defecto (forceRefresh=false) para evitar latencia.
 */
object AdminClaims {

    /**
     * @param forceRefresh si true, fuerza refrescar el token desde Firebase
     *   (útil tras setCustomUserClaims para que el cliente vea el cambio inmediatamente).
     * @return true si el usuario tiene `admin == true` en sus custom claims.
     */
    suspend fun isAdmin(forceRefresh: Boolean = false): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val tokenResult = try {
            user.getIdToken(forceRefresh).await()
        } catch (e: Exception) {
            return false
        }
        return tokenResult.claims["admin"] == true
    }
}
