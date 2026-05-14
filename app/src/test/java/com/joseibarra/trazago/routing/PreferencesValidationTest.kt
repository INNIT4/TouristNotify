package com.joseibarra.trazago.routing

import com.joseibarra.trazago.RouteInputValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesValidationTest {

    @Test
    fun `validate returns invalid when budget is blank`() {
        val result = RouteInputValidator.validate(budget = "", time = "3")
        assertFalse("Empty budget should be invalid", result.isValid)
        assertTrue("Error message should mention presupuesto", result.errorMessage.contains("presupuesto", ignoreCase = true))
    }

    @Test
    fun `validate returns invalid when budget is negative`() {
        val result = RouteInputValidator.validate(budget = "-100", time = "3")
        assertFalse("Negative budget should be invalid", result.isValid)
    }

    @Test
    fun `validate returns invalid when budget is zero`() {
        val result = RouteInputValidator.validate(budget = "0", time = "3")
        assertFalse("Zero budget should be invalid", result.isValid)
    }

    @Test
    fun `validate returns invalid when budget is non-numeric`() {
        val result = RouteInputValidator.validate(budget = "mucho", time = "3")
        assertFalse("Non-numeric budget should be invalid", result.isValid)
    }

    @Test
    fun `validate returns invalid when time is blank`() {
        val result = RouteInputValidator.validate(budget = "500", time = "")
        assertFalse("Empty time should be invalid", result.isValid)
    }

    @Test
    fun `validate returns invalid when time exceeds 24 hours`() {
        val result = RouteInputValidator.validate(budget = "500", time = "25")
        assertFalse("Time > 24 should be invalid", result.isValid)
    }

    @Test
    fun `validate returns valid for correct inputs`() {
        val result = RouteInputValidator.validate(budget = "500", time = "3")
        assertTrue("Valid inputs should pass validation", result.isValid)
    }

    @Test
    fun `validate accepts decimal budget`() {
        val result = RouteInputValidator.validate(budget = "299.50", time = "2")
        assertTrue("Decimal budget should be valid", result.isValid)
    }
}
