package com.lvvi.vividtv.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lvvi.vividtv.R
import com.lvvi.vividtv.model.CustomSourceHistory

// 自定义播放源列表适配器
class SourceHistoryAdapter(private var list: MutableList<CustomSourceHistory>) : RecyclerView.Adapter<SourceHistoryAdapter.ViewHolder>() {

    var onItemClickListener: ((link: String) -> Unit)? = null
    var onItemDeleteListener: ((position: Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = list[position]
        holder.nameLabel.text = model.link
        holder.deleteBtn.setOnClickListener {
            onItemDeleteListener?.invoke(position)
        }
        holder.itemView.setOnClickListener {
            onItemClickListener?.invoke(model.link)
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun removeAt(position: Int) {
        list.removeAt(position)
        notifyDataSetChanged()
    }

    // 实现LayoutContainer接口
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameLabel = itemView.findViewById<TextView>(R.id.name)
        val deleteBtn = itemView.findViewById<Button>(R.id.delete)
    }
}