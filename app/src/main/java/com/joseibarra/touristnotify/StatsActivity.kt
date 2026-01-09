package com.joseibarra.touristnotify

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.joseibarra.touristnotify.databinding.ActivityStatsBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        loadStats()
    }

    private fun loadStats() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            NotificationHelper.error(binding.root, "Usuario no autenticado")
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.statsContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Obtener estadísticas
                val statsDoc = db.collection("users")
                    .document(userId)
                    .collection("stats")
                    .document("summary")
                    .get()
                    .await()

                val stats = statsDoc.toObject(UserStats::class.java) ?: UserStats(userId = userId)

                // Obtener check-ins
                val checkInsSnapshot = db.collection("checkIns")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()

                val checkInsCount = checkInsSnapshot.size()

                // Obtener favoritos
                val favoritesSnapshot = db.collection("users")
                    .document(userId)
                    .collection("favorites")
                    .get()
                    .await()

                val favoritesCount = favoritesSnapshot.size()

                // Actualizar UI
                binding.progressBar.visibility = View.GONE
                binding.statsContainer.visibility = View.VISIBLE

                displayStats(stats, checkInsCount, favoritesCount)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                NotificationHelper.error(binding.root, "Error al cargar estadísticas: ${e.message}")
            }
        }
    }

    private fun displayStats(stats: UserStats, checkInsCount: Int, favoritesCount: Int) {
        // Estadísticas básicas
        binding.totalCheckInsTextView.text = checkInsCount.toString()
        binding.totalFavoritesTextView.text = favoritesCount.toString()
        binding.placesVisitedTextView.text = stats.categoriesExplored.values.sum().toString()

        // Gráfico de categorías exploradas
        if (stats.categoriesExplored.isNotEmpty()) {
            setupCategoryChart(stats.categoriesExplored)
        } else {
            binding.categoryChartCard.visibility = View.GONE
        }

        // Badges
        val badgesText = if (stats.badges.isNotEmpty()) {
            stats.badges.joinToString("\n") { "🏆 $it" }
        } else {
            "Aún no has ganado insignias. ¡Sigue explorando!"
        }
        binding.badgesTextView.text = badgesText
    }

    private fun setupCategoryChart(categories: Map<String, Int>) {
        val entries = mutableListOf<PieEntry>()

        categories.forEach { (category, count) ->
            entries.add(PieEntry(count.toFloat(), category))
        }

        val dataSet = PieDataSet(entries, "Categorías Exploradas")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.WHITE

        val pieData = PieData(dataSet)
        binding.categoryPieChart.data = pieData
        binding.categoryPieChart.description.isEnabled = false
        binding.categoryPieChart.centerText = "Tus\nExploraciones"
        binding.categoryPieChart.setEntryLabelColor(Color.BLACK)
        binding.categoryPieChart.animateY(1000)
        binding.categoryPieChart.invalidate()
    }
}
