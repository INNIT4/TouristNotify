package com.joseibarra.touristnotify.routing

import com.joseibarra.touristnotify.AppConstants
import com.joseibarra.touristnotify.TouristSpot
import com.joseibarra.touristnotify.model.GeneratedRoute
import com.joseibarra.touristnotify.model.RouteSummary
import com.joseibarra.touristnotify.model.RouteStop
import com.joseibarra.touristnotify.model.TipoViaje
import com.joseibarra.touristnotify.model.UserRoutePreferences
import com.joseibarra.touristnotify.routegen.PromptBuilderV2
import com.joseibarra.touristnotify.routegen.RouteValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeneratorTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun defaultPrefs(
        budget: Int = 500,
        hours: Float = 4f,
        interests: List<String> = listOf("Historia", "Gastronomía"),
        travelType: TipoViaje = TipoViaje.PAREJA,
        horaInicio: Int = 540,
        customRequest: String = ""
    ) = UserRoutePreferences(
        tipoViaje = travelType,
        numAdultos = 2,
        horaInicioMin = horaInicio,
        duracionHoras = hours,
        presupuestoMxn = budget,
        intereses = interests,
        temaSolicitudLibre = customRequest
    )

    private fun spot(id: String, nombre: String = "Lugar $id") = TouristSpot(
        id = id, nombre = nombre, categoria = "Atracción Turística"
    )

    private fun route(vararg placeIds: String, titulo: String = "Mi ruta") = GeneratedRoute(
        resumen = RouteSummary(titulo = titulo, descripcion = "desc", temaPrincipal = "cultura"),
        paradas = placeIds.mapIndexed { i, id ->
            RouteStop(placeId = id, ordenSugerido = i + 1, razonSeleccion = "interesante")
        }
    )

    // ─── PromptBuilderV2 ─────────────────────────────────────────────────────

    @Test
    fun `buildPrompt includes Alamos coordinates`() {
        val input = PromptBuilderV2.PromptInput(defaultPrefs(), emptyList())
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Prompt must contain ALAMOS_LAT", prompt.contains(AppConstants.ALAMOS_LAT.toString()))
        assertTrue("Prompt must contain ALAMOS_LNG", prompt.contains(AppConstants.ALAMOS_LNG.toString()))
    }

    @Test
    fun `buildPrompt includes budget`() {
        val input = PromptBuilderV2.PromptInput(defaultPrefs(budget = 750), emptyList())
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Prompt must contain budget", prompt.contains("750"))
        assertTrue("Prompt must contain MXN", prompt.contains("MXN"))
    }

    @Test
    fun `buildPrompt includes user interests`() {
        val input = PromptBuilderV2.PromptInput(
            defaultPrefs(interests = listOf("Fotografía", "Naturaleza")), emptyList()
        )
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Prompt must contain Fotografía", prompt.contains("Fotografía"))
        assertTrue("Prompt must contain Naturaleza", prompt.contains("Naturaleza"))
    }

    @Test
    fun `buildPrompt omits interests line when list is empty`() {
        val input = PromptBuilderV2.PromptInput(defaultPrefs(interests = emptyList()), emptyList())
        val prompt = PromptBuilderV2.build(input)
        assertFalse("Prompt should not have Intereses: when list is empty", prompt.contains("- Intereses:"))
    }

    @Test
    fun `buildPrompt includes anti-hallucination constraint`() {
        val input = PromptBuilderV2.PromptInput(defaultPrefs(), emptyList())
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Must contain anti-invention rule", prompt.contains("nunca inventes placeIds"))
    }

    @Test
    fun `buildPrompt includes place id in catalog`() {
        val spots = listOf(spot("place-abc", "Catedral"))
        val input = PromptBuilderV2.PromptInput(defaultPrefs(), spots)
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Prompt must contain placeId", prompt.contains("place-abc"))
        assertTrue("Prompt must contain place name", prompt.contains("Catedral"))
    }

    @Test
    fun `buildPrompt includes custom request when provided`() {
        val input = PromptBuilderV2.PromptInput(
            defaultPrefs(customRequest = "Quiero ver el atardecer"), emptyList()
        )
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Custom request should appear in prompt", prompt.contains("Quiero ver el atardecer"))
    }

    @Test
    fun `buildPrompt adds rain warning when raining`() {
        val input = PromptBuilderV2.PromptInput(defaultPrefs(), emptyList(), isRaining = true)
        val prompt = PromptBuilderV2.build(input)
        assertTrue("Rain warning must appear", prompt.contains("LLUVIA"))
    }

    // ─── RouteValidator ──────────────────────────────────────────────────────

    @Test
    fun `validation passes for valid route`() {
        val candidates = listOf(spot("a"), spot("b"), spot("c"))
        val result = RouteValidator.validate(route("a", "b", "c"), candidates)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validation fails for unknown placeId`() {
        val candidates = listOf(spot("a"), spot("b"))
        val result = RouteValidator.validate(route("a", "b", "z-unknown"), candidates)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("z-unknown") })
    }

    @Test
    fun `validation fails for duplicate placeIds`() {
        val candidates = listOf(spot("a"), spot("b"), spot("c"))
        val result = RouteValidator.validate(route("a", "b", "a"), candidates)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.lowercase().contains("duplicado") })
    }

    @Test
    fun `sanitize removes unknown placeIds`() {
        val candidates = listOf(spot("a"), spot("b"))
        val sanitized = RouteValidator.sanitize(route("a", "z-unknown", "b"), candidates)
        assertEquals(listOf("a", "b"), sanitized.paradas.map { it.placeId })
    }

    @Test
    fun `sanitize removes duplicates preserving first occurrence`() {
        val candidates = listOf(spot("a"), spot("b"), spot("c"))
        val sanitized = RouteValidator.sanitize(route("a", "b", "a", "c"), candidates)
        assertEquals(listOf("a", "b", "c"), sanitized.paradas.map { it.placeId })
    }
}
