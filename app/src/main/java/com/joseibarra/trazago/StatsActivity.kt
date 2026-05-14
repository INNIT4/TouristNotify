package com.joseibarra.trazago

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.joseibarra.trazago.databinding.ActivityStatsBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch

class StatsActivity : BaseActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val auth = FirebaseAuth.getInstance()
    private val profileRepository by lazy {
        UserProfileRepository(auth, FirebaseFirestore.getInstance(), FirebaseStorage.getInstance())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.headerRow, applyTop = true, applyBottom = false)

        binding.backButton.setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }

        loadStats()
    }

    private fun loadStats() {
        val userId = auth.currentUser?.uid ?: return

        lifecycleScope.launch {
            profileRepository.loadStats(userId)
                .onSuccess { stats ->
                    binding.routesCountText.text = stats.routesCount.toString()
                    binding.favoritesCountText.text = stats.favoritesCount.toString()
                    binding.checkinsCountText.text = stats.checkInsCount.toString()
                }
                .onFailure {
                    binding.routesCountText.text = "—"
                    binding.favoritesCountText.text = "—"
                    binding.checkinsCountText.text = "—"
                }

            val usage = UsageManager.getUsageStats(this@StatsActivity)
            binding.aiUsedText.text = usage.routesUsedToday.toString()
            binding.aiLimitText.text = getString(R.string.stats_ai_routes_limit, usage.routesLimitToday)
            binding.aiRemainingText.text = getString(R.string.stats_remaining_routes, usage.routesRemainingToday)
            binding.aiUsageProgress.progress = usage.usagePercentage
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
