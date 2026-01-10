package com.joseibarra.touristnotify

/**
 * Configuración de permisos administrativos para la aplicación
 */
object AdminConfig {

    /**
     * Emails autorizados de la Oficina de Turismo de Álamos
     * Solo estos usuarios pueden crear y administrar contenido del blog
     */
    private val AUTHORIZED_TOURISM_EMAILS = setOf(
        "turismo@alamos.gob.mx",
        "admin@turismoalamos.gob.mx",
        "info@turismoalamos.gob.mx",
        "comunicacion@alamos.gob.mx",
        "director.turismo@alamos.gob.mx"
    )

    /**
     * Verifica si un email pertenece al personal autorizado de turismo
     */
    fun isTourismOfficeUser(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return AUTHORIZED_TOURISM_EMAILS.contains(email.lowercase().trim())
    }

    /**
     * Verifica si un email tiene permisos de administrador general
     * (para funciones como importar lugares desde Google Places)
     */
    fun isGeneralAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val normalizedEmail = email.lowercase().trim()

        // Admins de turismo tienen todos los permisos
        if (AUTHORIZED_TOURISM_EMAILS.contains(normalizedEmail)) return true

        // También permitir emails que contengan "admin" como fallback
        return normalizedEmail.contains("admin")
    }

    /**
     * Obtiene la lista de emails autorizados (para propósitos de auditoría)
     */
    fun getAuthorizedEmails(): Set<String> {
        return AUTHORIZED_TOURISM_EMAILS.toSet()
    }

    /**
     * Verifica si un usuario puede crear posts en el blog
     */
    fun canCreateBlogPosts(email: String?): Boolean {
        return isTourismOfficeUser(email)
    }

    /**
     * Verifica si un usuario puede editar posts del blog
     */
    fun canEditBlogPosts(email: String?): Boolean {
        return isTourismOfficeUser(email)
    }

    /**
     * Verifica si un usuario puede eliminar posts del blog
     */
    fun canDeleteBlogPosts(email: String?): Boolean {
        return isTourismOfficeUser(email)
    }
}
