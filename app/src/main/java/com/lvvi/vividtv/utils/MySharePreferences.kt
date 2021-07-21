package com.lvvi.vividtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.lvvi.vividtv.model.CustomSourceHistory
import java.util.*

/**
 * Created by lvliheng on 2018/7/12 at 18:38.
 */
class MySharePreferences private constructor() {

    fun putString(key: String, value: String) {
        editor!!.putString(key, value)
        editor!!.apply()
    }

    fun getString(key: String): String? {
        return sharedPreferences!!.getString(key, "")
    }

    fun putInt(key: String, value: Int): Boolean {
        editor!!.putInt(key, value)
        return editor!!.commit()
    }

    fun getInt(key: String): Int? {
        return sharedPreferences!!.getInt(key, 0)
    }

    // 保存自定义的直播源地址
    fun putHistoryItem(source: String): Boolean {
        val item = CustomSourceHistory()
        item.link = source
        item.time = Date().time / 1000
        var arrayValue = sharedPreferences!!.getString(Constant.CUSTOM_HISTORY_KEY, "[]")
        val histories = gson.fromJson(arrayValue, Array<CustomSourceHistory>::class.java).toMutableList()
        val index = histories.indexOf(item)
        if (index != -1) {
            // 删除重复的地址
            histories.removeAt(index)
        }
        histories.add(item)
        arrayValue = gson.toJson(histories)
        editor!!.putString(Constant.CUSTOM_HISTORY_KEY, arrayValue)
        return editor!!.commit()
    }

    // 获取自定义的直播源地址足迹列表
    fun getHistoryItems(): Array<CustomSourceHistory> {
        val arrayValue = sharedPreferences!!.getString(Constant.CUSTOM_HISTORY_KEY, "[]")
        return gson.fromJson(arrayValue, Array<CustomSourceHistory>::class.java)
    }

    // 删除指定索引的自定义的直播源地址
    fun deleteHistoryItem(position: Int): Boolean {
        var arrayValue = sharedPreferences!!.getString(Constant.CUSTOM_HISTORY_KEY, "[]")
        val histories = gson.fromJson(arrayValue, Array<CustomSourceHistory>::class.java).toMutableList()
        if (position >= 0 && position < histories.size) {
            histories.removeAt(position)
        }
        arrayValue = gson.toJson(histories)
        editor!!.putString(Constant.CUSTOM_HISTORY_KEY, arrayValue)
        return editor!!.commit()
    }

    companion object {

        private var sharedPreferences: SharedPreferences? = null
        private var editor: SharedPreferences.Editor? = null
        private var mySharePreferences: MySharePreferences? = null
        private var gson = Gson()

        fun getInstance(context: Context): MySharePreferences {
            if (mySharePreferences == null) {
                mySharePreferences = MySharePreferences()
                sharedPreferences = context.getSharedPreferences(Constant.SHARE_PREFERENCES_NAME, Context.MODE_PRIVATE)
                editor = sharedPreferences!!.edit()
            }
            return mySharePreferences as MySharePreferences
        }
    }

}