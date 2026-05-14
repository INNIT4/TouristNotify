package com.joseibarra.trazago

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.joseibarra.trazago.databinding.ActivityServicesBinding
import com.joseibarra.trazago.ui.BaseActivity
import com.joseibarra.trazago.ui.MotionHelper
import kotlinx.coroutines.launch

class ServicesActivity : BaseActivity() {

    private lateinit var binding: ActivityServicesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerServices.layoutManager = LinearLayoutManager(this)
        binding.recyclerServices.itemAnimator = MotionHelper.fadeSlideItemAnimator()

        lifecycleScope.launch {
            val services = ServicesRepository.load()
            if (services.isEmpty()) {
                binding.emptyState.visibility = android.view.View.VISIBLE
                binding.recyclerServices.visibility = android.view.View.GONE
            } else {
                binding.recyclerServices.adapter = ServicesAdapter(services)
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }
}
