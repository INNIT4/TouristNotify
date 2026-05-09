package com.joseibarra.touristnotify

data class ValidationResult(val isValid: Boolean, val errorMessage: String = "")

object RouteInputValidator {
    fun validate(budget: String, time: String): ValidationResult {
        if (budget.isBlank()) {
            return ValidationResult(false, "El presupuesto no puede estar vacío")
        }
        val budgetValue = budget.toDoubleOrNull()
            ?: return ValidationResult(false, "El presupuesto debe ser un número válido")
        if (budgetValue <= 0) {
            return ValidationResult(false, "El presupuesto debe ser mayor a cero")
        }
        if (time.isBlank()) {
            return ValidationResult(false, "El tiempo disponible no puede estar vacío")
        }
        val timeValue = time.toDoubleOrNull()
            ?: return ValidationResult(false, "El tiempo debe ser un número válido")
        if (timeValue <= 0 || timeValue > 24) {
            return ValidationResult(false, "El tiempo debe estar entre 1 y 24 horas")
        }
        return ValidationResult(true)
    }
}
