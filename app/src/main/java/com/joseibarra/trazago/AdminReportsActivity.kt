package com.joseibarra.trazago

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joseibarra.trazago.databinding.ActivityAdminReportsBinding
import com.joseibarra.trazago.databinding.ItemReportBinding
import com.joseibarra.trazago.ui.BaseActivity
import kotlinx.coroutines.launch

class AdminReportsActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminReportsBinding
    private lateinit var adapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets(binding.root, applyTop = true, applyBottom = true)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = ReportAdapter(
            onViewPost = { report ->
                startActivity(Intent(this, CommunityPostDetailActivity::class.java)
                    .putExtra(CommunityPostDetailActivity.EXTRA_POST_ID, report.postId))
            },
            onHidePost = { report -> hidePost(report) },
            onDismiss = { report -> dismissReport(report) }
        )

        binding.reportsRecycler.layoutManager = LinearLayoutManager(this)
        binding.reportsRecycler.adapter = adapter

        loadReports()
    }

    private fun loadReports() {
        lifecycleScope.launch {
            CommunityRepository.getPendingReports().onSuccess { reports ->
                adapter.submitList(reports)
                binding.emptyState.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
                binding.reportsRecycler.visibility =
                    if (reports.isEmpty()) View.GONE else View.VISIBLE
            }.onFailure { e ->
                FirestoreErrorHandler.handleError(this@AdminReportsActivity, binding.root, e,
                    "cargar reportes")
            }
        }
    }

    private fun hidePost(report: PostReport) {
        lifecycleScope.launch {
            CommunityRepository.hidePostAsAdmin(report.postId).onSuccess {
                CommunityRepository.dismissReport(report.id)
                loadReports()
                NotificationHelper.success(binding.root, getString(R.string.community_admin_hide))
            }.onFailure { e ->
                FirestoreErrorHandler.handleError(this@AdminReportsActivity, binding.root, e,
                    "ocultar publicación")
            }
        }
    }

    private fun dismissReport(report: PostReport) {
        lifecycleScope.launch {
            CommunityRepository.dismissReport(report.id).onSuccess {
                loadReports()
            }.onFailure { e ->
                FirestoreErrorHandler.handleError(this@AdminReportsActivity, binding.root, e,
                    "descartar reporte")
            }
        }
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    private class ReportAdapter(
        private val onViewPost: (PostReport) -> Unit,
        private val onHidePost: (PostReport) -> Unit,
        private val onDismiss: (PostReport) -> Unit,
    ) : ListAdapter<PostReport, ReportAdapter.VH>(DiffCB()) {

        inner class VH(private val b: ItemReportBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(report: PostReport) {
                b.reportReason.text = report.reason
                b.reportPostPreview.text = report.postId
                b.reportTimestamp.text = if (report.createdAt != null) {
                    DateUtils.getRelativeTimeSpanString(
                        report.createdAt.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    )
                } else ""

                if (report.detail.isNotBlank()) {
                    b.reportDetail.visibility = View.VISIBLE
                    b.reportDetail.text = report.detail
                } else {
                    b.reportDetail.visibility = View.GONE
                }

                b.btnViewPost.setOnClickListener { onViewPost(report) }
                b.btnHidePost.setOnClickListener { onHidePost(report) }
                b.btnDismiss.setOnClickListener { onDismiss(report) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class DiffCB : DiffUtil.ItemCallback<PostReport>() {
            override fun areItemsTheSame(old: PostReport, new: PostReport) = old.id == new.id
            override fun areContentsTheSame(old: PostReport, new: PostReport) = old == new
        }
    }
}
