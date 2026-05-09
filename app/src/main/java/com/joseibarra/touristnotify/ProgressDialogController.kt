package com.joseibarra.touristnotify

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class ProgressDialogController(private val activity: Activity) {
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show(messages: List<String>) {
        val dialogView = activity.layoutInflater
            .inflate(R.layout.dialog_route_generation_progress, null)
        dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            .also {
                it.window?.setBackgroundDrawableResource(android.R.color.transparent)
                it.show()
            }
        val tv = dialog!!.findViewById<TextView>(R.id.progress_message)
        var i = 0
        runnable = object : Runnable {
            override fun run() {
                if (i < messages.size && isShowing) {
                    tv?.text = messages[i++]
                    handler.postDelayed(this, 1500)
                }
            }
        }
        handler.postDelayed(runnable!!, 1500)
    }

    fun dismiss() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
        dialog?.dismiss()
        dialog = null
    }

    fun cleanup(onCleanup: () -> Unit) {
        dismiss()
        onCleanup()
    }
}
