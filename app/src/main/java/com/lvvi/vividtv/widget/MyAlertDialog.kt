package com.lvvi.vividtv.widget

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.lvvi.vividtv.R

// 弹出框对话框
class MyAlertDialog(private val context: Context) {
    private val dialog: Dialog = Dialog(context)
    private val view: View
    private val titleView: TextView
    private val messageView: TextView

    init {
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.dialog_alert, null)
        dialog.setContentView(view)

        titleView = view.findViewById(R.id.title)
        messageView = view.findViewById(R.id.message)
    }

    fun show(title: String, message: String) {
        titleView.text = title
        messageView.text = message

        val btnConfirm = view.findViewById<Button>(R.id.confirm)
        btnConfirm.setOnClickListener {
            dialog.dismiss()
        }
        val dialogWindow = dialog.window
        dialogWindow?.setBackgroundDrawableResource(R.drawable.dialog_source_link_bg)
        val lp = dialogWindow!!.attributes
        val d = context.resources.displayMetrics // 获取屏幕宽、高用
        lp.width = (d.widthPixels * 0.5).toInt() // 宽度设置为屏幕的0.8
        dialogWindow.attributes = lp
        dialog.show()
    }
}