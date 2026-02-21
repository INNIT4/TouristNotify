package com.joseibarra.touristnotify

/**
 * Configuración de permisos administrativos para la aplicación
 *
 * Los emails autorizados ahora se gestionan desde Firebase Remote Config
 * para permitir actualizaciones sin necesidad de release de app.
 *
 * Fallback: Si Remote Config falla, usa emails hardcoded en ConfigManager.
 */
object AdminConfig {

    /**
     * Obtiene los emails autorizados de la Oficina de Turismo de Álamos
     * desde Firebase Remote Config (con fallback a valores hardcoded)
     *
     * Solo estos usuarios pueden crear y administrar contenido del blog
     */
    private fun getAuthorizedTourismEmails(): Set<String> {
        return ConfigManager.getAuthorizedAdminEmails()
    }

    /**
     * Verifica si un email pertenece al personal autorizado de turismo
     */
    fun isTourismOfficeUser(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        return getAuthorizedTourismEmails().contains(email.lowercase().trim())
    }

    /**
     * Verifica si un email tiene permisos de administrador general
     * (para funciones como importar lugares desde Google Places)
     */
    fun isGeneralAdmin(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val normalizedEmail = email.lowercase().trim()

        // SOLO permitir emails explícitamente autorizados
        // Sin fallbacks por seguridad
        return getAuthorizedTourismEmails().contains(normalizedEmail)
    }

    /**
     * Obtiene la lista de emails autorizados (para propósitos de auditoría)
     */
    fun getAuthorizedEmails(): Set<String> {
        return getAuthorizedTourismEmails()
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
