package com.joseibarra.trazago.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.joseibarra.trazago.model.AccesibilidadRequerida
import com.joseibarra.trazago.model.Movilidad
import com.joseibarra.trazago.model.OpcionDietetica
import com.joseibarra.trazago.model.Ritmo
import com.joseibarra.trazago.model.TipoViaje
import com.joseibarra.trazago.model.UserRoutePreferences
import java.util.Calendar

/**
 * ViewModel compartido entre todos los fragments del wizard.
 * Excepción documentada a la convención "sin ViewModel":
 * se necesita SavedStateHandle para sobrevivir rotación con estado
 * compartido entre 4 fragments en un ViewPager2.
 *
 * Cada setter actualiza el SavedStateHandle → sobrevive process death.
 */
class PreferencesViewModel(private val state: SavedStateHandle) : ViewModel() {

    // ── Paso 1 ───────────────────────────────────────────────────────────────
    var tipoViaje: TipoViaje
        get() = TipoViaje.valueOf(state[KEY_TIPO_VIAJE] ?: TipoViaje.PAREJA.name)
        set(v) { state[KEY_TIPO_VIAJE] = v.name }

    var numAdultos: Int
        get() = state[KEY_NUM_ADULTOS] ?: 2
        set(v) { state[KEY_NUM_ADULTOS] = v }

    var numNiños: Int
        get() = state[KEY_NUM_NINOS] ?: 0
        set(v) { state[KEY_NUM_NINOS] = v }

    var edadMinNiños: Int
        get() = state[KEY_EDAD_NINOS] ?: 0
        set(v) { state[KEY_EDAD_NINOS] = v }

    var fechaViajeMs: Long
        get() = state[KEY_FECHA] ?: System.currentTimeMillis()
        set(v) { state[KEY_FECHA] = v }

    var horaInicioMin: Int
        get() = state[KEY_HORA_INICIO] ?: defaultStartMinutes()
        set(v) { state[KEY_HORA_INICIO] = v }

    var duracionHoras: Float
        get() = state[KEY_DURACION] ?: 4f
        set(v) { state[KEY_DURACION] = v }

    // ── Paso 2 ───────────────────────────────────────────────────────────────
    var intereses: List<String>
        get() = state[KEY_INTERESES] ?: emptyList()
        set(v) { state[KEY_INTERESES] = ArrayList(v) }

    var ritmo: Ritmo
        get() = Ritmo.valueOf(state[KEY_RITMO] ?: Ritmo.MODERADO.name)
        set(v) { state[KEY_RITMO] = v.name }

    var temaSolicitudLibre: String
        get() = state[KEY_TEMA] ?: ""
        set(v) { state[KEY_TEMA] = v }

    // ── Paso 3 ───────────────────────────────────────────────────────────────
    var presupuestoMxn: Int
        get() = state[KEY_PRESUPUESTO] ?: 500
        set(v) { state[KEY_PRESUPUESTO] = v }

    var movilidad: Movilidad
        get() = Movilidad.valueOf(state[KEY_MOVILIDAD] ?: Movilidad.A_PIE.name)
        set(v) { state[KEY_MOVILIDAD] = v.name }

    var sillaRuedas: Boolean
        get() = state[KEY_SILLA_RUEDAS] ?: false
        set(v) { state[KEY_SILLA_RUEDAS] = v }

    var banoAccesible: Boolean
        get() = state[KEY_BANO_ACCESIBLE] ?: false
        set(v) { state[KEY_BANO_ACCESIBLE] = v }

    var estacionamientoAccesible: Boolean
        get() = state[KEY_ESTACION_ACCESIBLE] ?: false
        set(v) { state[KEY_ESTACION_ACCESIBLE] = v }

    var restriccionesDieteticas: List<OpcionDietetica>
        get() = (state[KEY_DIETA] as? ArrayList<*>)
            ?.mapNotNull { runCatching { OpcionDietetica.valueOf(it as String) }.getOrNull() }
            ?: emptyList()
        set(v) { state[KEY_DIETA] = ArrayList(v.map { it.name }) }

    var incluirComida: Boolean
        get() = state[KEY_INCLUIR_COMIDA] ?: false
        set(v) { state[KEY_INCLUIR_COMIDA] = v }

    // ── Helpers ──────────────────────────────────────────────────────────────

    fun buildPreferences(): UserRoutePreferences = UserRoutePreferences(
        tipoViaje = tipoViaje,
        numAdultos = numAdultos,
        numNiños = numNiños,
        edadMinNiños = edadMinNiños,
        fechaViajeMs = fechaViajeMs,
        horaInicioMin = horaInicioMin,
        duracionHoras = duracionHoras,
        intereses = intereses,
        ritmo = ritmo,
        temaSolicitudLibre = temaSolicitudLibre,
        presupuestoMxn = presupuestoMxn,
        movilidad = movilidad,
        accesibilidadRequerida = AccesibilidadRequerida(
            sillaRuedas = sillaRuedas,
            banoAccesible = banoAccesible,
            estacionamientoAccesible = estacionamientoAccesible
        ),
        restriccionesDieteticas = restriccionesDieteticas,
        incluirComida = incluirComida
    )

    fun validateStep(step: Int): String? = when (step) {
        1 -> if (numAdultos < 1) "Selecciona al menos 1 adulto" else null
        2 -> if (intereses.isEmpty() && temaSolicitudLibre.isBlank())
                "Selecciona al menos un interés o escribe una solicitud" else null
        3 -> if (presupuestoMxn < 100) "El presupuesto mínimo es \$100 MXN" else null
        else -> null
    }

    private fun defaultStartMinutes(): Int {
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Redondear a la próxima media hora
        val rounded = ((now + 30) / 30) * 30
        return rounded.coerceIn(360, 22 * 60) // entre 6:00 y 22:00
    }

    companion object {
        private const val KEY_TIPO_VIAJE = "tipo_viaje"
        private const val KEY_NUM_ADULTOS = "num_adultos"
        private const val KEY_NUM_NINOS = "num_ninos"
        private const val KEY_EDAD_NINOS = "edad_ninos"
        private const val KEY_FECHA = "fecha_viaje"
        private const val KEY_HORA_INICIO = "hora_inicio"
        private const val KEY_DURACION = "duracion_horas"
        private const val KEY_INTERESES = "intereses"
        private const val KEY_RITMO = "ritmo"
        private const val KEY_TEMA = "tema"
        private const val KEY_PRESUPUESTO = "presupuesto"
        private const val KEY_MOVILIDAD = "movilidad"
        private const val KEY_SILLA_RUEDAS = "silla_ruedas"
        private const val KEY_BANO_ACCESIBLE = "bano_accesible"
        private const val KEY_ESTACION_ACCESIBLE = "estacion_accesible"
        private const val KEY_DIETA = "dieta"
        private const val KEY_INCLUIR_COMIDA = "incluir_comida"
    }
}
