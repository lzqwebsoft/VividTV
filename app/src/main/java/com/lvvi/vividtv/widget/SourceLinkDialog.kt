package com.lvvi.vividtv.widget

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.lvvi.vividtv.R

/**
 * 直播源地址输入框对话框
 */
class SourceLinkDialog(private val context: Context) {

    var confirmListener: ((link: String) -> Unit)? = null

    private val dialog: Dialog = Dialog(context)
    private var view: View
    private lateinit var sourceContent: EditText


    init {
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.dialog_source_link, null)
        dialog.setContentView(view)
    }

    fun show() {
        sourceContent = view.findViewById(R.id.source_content)
        val btnConfirm = view.findViewById<Button>(R.id.confirm)
        val btnCancel = view.findViewById<Button>(R.id.cancel)
        btnConfirm.setOnClickListener {
            val link = sourceContent.text.toString()
            if (link.isEmpty()) {
                Toast.makeText(context, "请输入直播源地址！", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            confirmListener?.invoke(link)
        }
        btnCancel.setOnClickListener {
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