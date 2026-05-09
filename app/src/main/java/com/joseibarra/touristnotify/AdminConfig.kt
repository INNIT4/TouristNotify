package com.joseibarra.touristnotify

/**
 * Configuración de permisos administrativos para la UI.
 *
 * **Esta clase es solo una hint de UX, NO una capa de seguridad.**
 *
 * La seguridad real la enforce Firestore vía el custom claim `admin` (ver
 * `firestore.rules` -> `isAdmin()`). Aunque un atacante falsifique localmente
 * la respuesta de [isTourismOfficeUser] para mostrar la UI de admin, cualquier
 * intento de escribir en colecciones administrativas será rechazado por las
 * reglas a menos que tenga el custom claim `admin == true` en su token.
 *
 * Para verificación verdaderamente segura del lado del cliente (p.ej. esconder
 * UI hasta confirmar el rol), usa [AdminClaims.isAdmin] que lee el custom claim
 * desde el ID token de Firebase Auth.
 */
object AdminConfig {

    /**
     * Lista de emails históricos del personal de turismo. Usada solo como
     * sugerencia visual; el servidor no confía en esta lista.
     */
    private val AUTHORIZED_TOURISM_EMAILS = setOf(
        "turismo@alamos.gob.mx",
        "admin@turismoalamos.gob.mx",
        "info@turismoalamos.gob.mx",
        "comunicacion@alamos.gob.mx",
        "director.turismo@alamos.gob.mx",
        "joselocalseo@gmail.com",
        "joseinnit04@outlook.com",
        "jose@gmail.com"
    )

    /**
     * Verifica si un email pertenece al personal de turismo.
     *
     * Solo para mostrar/esconder UI. Para validación real de permisos, usar
     * [AdminClaims.isAdmin] o dejar que Firestore rechace la operación.
     */
    fun isTourismOfficeUser(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return AUTHORIZED_TOURISM_EMAILS.contains(email.lowercase().trim())
    }

    /** Alias histórico — equivalente a [isTourismOfficeUser]. */
    fun isGeneralAdmin(email: String?): Boolean = isTourismOfficeUser(email)

    fun getAuthorizedEmails(): Set<String> = AUTHORIZED_TOURISM_EMAILS.toSet()

    fun canCreateBlogPosts(email: String?): Boolean = isTourismOfficeUser(email)
    fun canEditBlogPosts(email: String?): Boolean = isTourismOfficeUser(email)
    fun canDeleteBlogPosts(email: String?): Boolean = isTourismOfficeUser(email)
}
