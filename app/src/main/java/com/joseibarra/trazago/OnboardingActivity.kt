package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.viewpager2.widget.ViewPager2
import com.joseibarra.trazago.R
import com.joseibarra.trazago.databinding.ActivityOnboardingBinding
import com.joseibarra.trazago.ui.BaseActivity

class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val onboardingPages by lazy {
        listOf(
            OnboardingPage(
                "\uD83D\uDDFA\uFE0F",
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1)
            ),
            OnboardingPage(
                "\uD83D\uDCCD",
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2)
            ),
            OnboardingPage(
                "\u2728",
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3)
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("TrazaGoPrefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_completed", false)) {
            navigateToMain()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.skipButton, applyTop = true, applyBottom = false)
        applyWindowInsets(binding.privacyPolicyLink, applyTop = false, applyBottom = true)

        setupViewPager()
        setupDots(0)
        setupButtons()
        setupPrivacyLink()
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(onboardingPages)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setupDots(position)
                if (position == onboardingPages.size - 1) {
                    binding.nextButton.text = getString(R.string.btn_start)
                    binding.skipButton.visibility = View.GONE
                } else {
                    binding.nextButton.text = getString(R.string.btn_next)
                    binding.skipButton.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupDots(activePage: Int) {
        binding.dotsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val gap = (8 * density).toInt()

        for (i in onboardingPages.indices) {
            val dot = View(this)
            val width = if (i == activePage) (24 * density).toInt() else (8 * density).toInt()
            val height = (8 * density).toInt()
            val params = LinearLayout.LayoutParams(width, height)
            params.setMargins(gap / 2, 0, gap / 2, 0)
            dot.layoutParams = params
            dot.background = if (i == activePage) {
                ContextCompat.getDrawable(this, R.drawable.bg_dot_active)
            } else {
                ContextCompat.getDrawable(this, R.drawable.bg_dot_inactive)
            }
            binding.dotsContainer.addView(dot)
        }
    }

    private fun setupPrivacyLink() {
        val raw = getString(R.string.onboarding_privacy_notice)
        binding.privacyPolicyLink.text = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.privacyPolicyLink.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
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
        val prefs = getSharedPreferences("TrazaGoPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, LoginActivity::class.java)
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
