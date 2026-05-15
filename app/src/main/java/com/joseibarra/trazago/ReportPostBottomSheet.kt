package com.joseibarra.trazago

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.joseibarra.trazago.databinding.BottomSheetReportPostBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReportPostBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportPostBinding? = null
    private val binding get() = _binding!!

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var postId: String
    private lateinit var reporterId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetReportPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postId = requireArguments().getString(ARG_POST_ID) ?: return
        reporterId = requireArguments().getString(ARG_REPORTER_ID) ?: return

        binding.btnSendReport.setOnClickListener { sendReport() }
    }

    private fun sendReport() {
        val reasonKey = when (binding.reportReasonGroup.checkedRadioButtonId) {
            binding.reasonSpam.id -> ReportReason.SPAM.key
            binding.reasonOffensive.id -> ReportReason.OFFENSIVE.key
            binding.reasonFake.id -> ReportReason.FAKE_INFO.key
            binding.reasonOther.id -> ReportReason.OTHER.key
            else -> {
                Snackbar.make(
                    binding.root,
                    R.string.community_report_reason_label,
                    Snackbar.LENGTH_SHORT
                ).show()
                return
            }
        }
        val detail = binding.reportDetailInput.text?.toString().orEmpty().trim()

        binding.btnSendReport.isEnabled = false

        scope.launch {
            val result = CommunityRepository.reportPost(postId, reporterId, reasonKey, detail)
            result.onSuccess {
                Snackbar.make(
                    requireActivity().window.decorView.rootView,
                    R.string.community_report_success,
                    Snackbar.LENGTH_SHORT
                ).show()
                dismiss()
            }.onFailure { e ->
                binding.btnSendReport.isEnabled = true
                val msg = if (e is AlreadyReportedException)
                    getString(R.string.community_report_already)
                else
                    e.localizedMessage ?: getString(R.string.error_generic)
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }

    companion object {
        const val TAG = "ReportPostBottomSheet"
        private const val ARG_POST_ID = "postId"
        private const val ARG_REPORTER_ID = "reporterId"

        fun newInstance(postId: String, reporterId: String) =
            ReportPostBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                    putString(ARG_REPORTER_ID, reporterId)
                }
            }
    }
}
