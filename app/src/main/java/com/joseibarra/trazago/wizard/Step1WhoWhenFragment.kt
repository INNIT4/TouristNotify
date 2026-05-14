package com.joseibarra.trazago.wizard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.joseibarra.trazago.databinding.FragmentStep1WhoWhenBinding
import com.joseibarra.trazago.model.TipoViaje
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Paso 1: ¿Quiénes y cuándo?
 * Captura: tipo de viaje, # adultos, # niños, fecha, hora de inicio, duración.
 */
class Step1WhoWhenFragment : Fragment() {

    private var _binding: FragmentStep1WhoWhenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PreferencesViewModel by activityViewModels()

    private val dateFormat = SimpleDateFormat("EEE d MMM yyyy", Locale("es", "MX"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStep1WhoWhenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restoreFromViewModel()
        setupListeners()
    }

    private fun restoreFromViewModel() {
        // Tipo de viaje
        val chipId = when (viewModel.tipoViaje) {
            TipoViaje.SOLO -> binding.chipSolo.id
            TipoViaje.PAREJA -> binding.chipPareja.id
            TipoViaje.FAMILIA -> binding.chipFamilia.id
            TipoViaje.AMIGOS -> binding.chipAmigos.id
        }
        binding.chipGroupTipoViaje.check(chipId)

        // Contadores
        binding.tvAdultos.text = viewModel.numAdultos.toString()
        binding.tvNinos.text = viewModel.numNiños.toString()
        updateNinosAgeVisibility()

        // Fecha
        updateDateDisplay()

        // Hora
        updateTimeDisplay()

        // Duración
        binding.sliderDuracion.value = viewModel.duracionHoras
        updateDuracionLabel()
    }

    private fun setupListeners() {
        // Tipo de viaje
        binding.chipGroupTipoViaje.setOnCheckedStateChangeListener { _, _ ->
            viewModel.tipoViaje = selectedTipoViaje()
        }

        // Adultos ±
        binding.btnAdultosPlus.setOnClickListener {
            if (viewModel.numAdultos < 20) {
                viewModel.numAdultos++
                binding.tvAdultos.text = viewModel.numAdultos.toString()
            }
        }
        binding.btnAdultosMinus.setOnClickListener {
            if (viewModel.numAdultos > 1) {
                viewModel.numAdultos--
                binding.tvAdultos.text = viewModel.numAdultos.toString()
            }
        }

        // Niños ±
        binding.btnNinosPlus.setOnClickListener {
            if (viewModel.numNiños < 10) {
                viewModel.numNiños++
                binding.tvNinos.text = viewModel.numNiños.toString()
                updateNinosAgeVisibility()
            }
        }
        binding.btnNinosMinus.setOnClickListener {
            if (viewModel.numNiños > 0) {
                viewModel.numNiños--
                binding.tvNinos.text = viewModel.numNiños.toString()
                updateNinosAgeVisibility()
            }
        }

        // Edad mínima niños
        binding.chipGroupEdadNinos.setOnCheckedStateChangeListener { _, _ ->
            viewModel.edadMinNiños = selectedEdadNinos()
        }

        // Fecha
        binding.btnFecha.setOnClickListener { showDatePicker() }

        // Hora inicio
        binding.btnHoraInicio.setOnClickListener { showTimePicker() }

        // Duración
        binding.sliderDuracion.addOnChangeListener { _, value, _ ->
            viewModel.duracionHoras = value
            updateDuracionLabel()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = viewModel.fechaViajeMs }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                cal.set(year, month, day)
                viewModel.fechaViajeMs = cal.timeInMillis
                updateDateDisplay()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).also { dialog ->
            dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun showTimePicker() {
        val h = viewModel.horaInicioMin / 60
        val m = viewModel.horaInicioMin % 60
        TimePickerDialog(requireContext(), { _, hour, minute ->
            viewModel.horaInicioMin = hour * 60 + minute
            updateTimeDisplay()
        }, h, m, true).show()
    }

    private fun updateDateDisplay() {
        val cal = Calendar.getInstance().apply { timeInMillis = viewModel.fechaViajeMs }
        binding.btnFecha.text = dateFormat.format(cal.time)
    }

    private fun updateTimeDisplay() {
        val h = viewModel.horaInicioMin / 60
        val m = viewModel.horaInicioMin % 60
        binding.btnHoraInicio.text = "%02d:%02d".format(h, m)
    }

    private fun updateDuracionLabel() {
        val h = viewModel.duracionHoras
        binding.tvDuracionLabel.text = when {
            h < 1f -> "${(h * 60).toInt()} min"
            h == 1f -> "1 hora"
            h % 1f == 0f -> "${h.toInt()} horas"
            else -> "${h.toInt()}h ${((h % 1f) * 60).toInt()}min"
        }
    }

    private fun updateNinosAgeVisibility() {
        binding.layoutEdadNinos.visibility =
            if (viewModel.numNiños > 0) View.VISIBLE else View.GONE
    }

    private fun selectedTipoViaje(): TipoViaje = when (binding.chipGroupTipoViaje.checkedChipId) {
        binding.chipSolo.id -> TipoViaje.SOLO
        binding.chipFamilia.id -> TipoViaje.FAMILIA
        binding.chipAmigos.id -> TipoViaje.AMIGOS
        else -> TipoViaje.PAREJA
    }

    private fun selectedEdadNinos(): Int = when (binding.chipGroupEdadNinos.checkedChipId) {
        binding.chipEdad2.id -> 2
        binding.chipEdad5.id -> 5
        binding.chipEdad10.id -> 10
        else -> 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
