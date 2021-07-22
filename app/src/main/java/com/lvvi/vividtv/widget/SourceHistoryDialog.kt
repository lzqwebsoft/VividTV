package com.lvvi.vividtv.widget

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lvvi.vividtv.R
import com.lvvi.vividtv.model.CustomSourceHistory
import com.lvvi.vividtv.ui.adapter.SourceHistoryAdapter
import com.lvvi.vividtv.utils.MySharePreferences

/**
 * 自定义播放历史对话框
 */
class SourceHistoryDialog(private val context: Context) {

    var onClickHistoryItem: ((link: String) -> Unit)? = null

    private val dialog: Dialog = Dialog(context)
    private val view: View
    private val histories: Array<CustomSourceHistory>
    private val historyList: RecyclerView
    private val historyAdapter: SourceHistoryAdapter
    private val preferences: MySharePreferences = MySharePreferences.getInstance(context)
    private val emptyHitLabel: TextView

    init {
        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.dialog_source_history, null)
        dialog.setContentView(view)

        historyList = view.findViewById(R.id.history_list)
        emptyHitLabel = view.findViewById(R.id.empty_hit)
        // 初始化自定义历史播放足迹
        historyList.layoutManager = LinearLayoutManager(context)
        histories = preferences.getHistoryItems()
        historyAdapter = SourceHistoryAdapter(histories.toMutableList())
        historyList.adapter = historyAdapter
        val btnCancel = view.findViewById<Button>(R.id.cancel)
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        historyAdapter.onItemDeleteListener = { position ->
            if (preferences.deleteHistoryItem(position)) {
                historyAdapter.removeAt(position)
                // 判断是否是空的列表
                if (historyAdapter.itemCount == 0) {
                    historyList.visibility = View.GONE
                    emptyHitLabel.visibility = View.VISIBLE
                }
            }

        }
        historyAdapter.onItemClickListener = { link ->
            dialog.dismiss()
            onClickHistoryItem?.invoke(link)
        }
    }

    fun show() {
        if (histories.isEmpty()) {
            historyList.visibility = View.GONE
            emptyHitLabel.visibility = View.VISIBLE
        } else {
            historyList.visibility = View.VISIBLE
            emptyHitLabel.visibility = View.GONE
        }

        val dialogWindow = dialog.window
        dialogWindow?.setBackgroundDrawableResource(R.drawable.dialog_source_link_bg)
        val lp = dialogWindow!!.attributes
        val d = context.resources.displayMetrics // 获取屏幕宽、高用
        lp.width = (d.widthPixels * 0.5).toInt() // 宽度设置为屏幕的0.8
        lp.height = (d.heightPixels * 0.8).toInt() // 宽度设置为屏幕的0.8
        dialogWindow.attributes = lp
        dialog.show()
    }
}