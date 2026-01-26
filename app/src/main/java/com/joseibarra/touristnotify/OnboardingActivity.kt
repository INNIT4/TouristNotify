package com.joseibarra.touristnotify

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.joseibarra.touristnotify.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val onboardingPages = listOf(
        OnboardingPage(
            "🗺️",
            "Explora Álamos",
            "Descubre lugares históricos, eventos culturales y experiencias únicas en este Pueblo Mágico"
        ),
        OnboardingPage(
            "📍",
            "Notificaciones Inteligentes",
            "Recibe avisos automáticos cuando estés cerca de lugares turísticos interesantes"
        ),
        OnboardingPage(
            "✨",
            "Crea Rutas Personalizadas",
            "Genera itinerarios con IA o explora rutas temáticas diseñadas por expertos"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding was already shown
        val prefs = getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_completed", false)) {
            navigateToMain()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(onboardingPages)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == onboardingPages.size - 1) {
                    binding.nextButton.text = "Comenzar"
                    binding.skipButton.visibility = View.GONE
                } else {
                    binding.nextButton.text = "Siguiente"
                    binding.skipButton.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupButtons() {
        binding.nextButton.setOnClickListener {
            if (binding.viewPager.currentItem == onboardingPages.size - 1) {
                completeOnboarding()
            } else {
                binding.viewPager.currentItem += 1
            }
        }

        binding.skipButton.setOnClickListener {
            completeOnboarding()
        }
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences("TouristNotifyPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MenuActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String
)
