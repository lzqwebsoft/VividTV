package com.lvvi.vividtv.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

object Utils {

    private const val START_DATE = "19/08/2016"   // 见面时间
    private const val BIRTH_DATE = "09/01/2019"   // 生日时间

    val meetDays: Int
        get() = getDays(START_DATE)

    val birthDays: Int
        get() = getDays(BIRTH_DATE)

    private fun getDays(date: String): Int {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        var startDate: Date?
        var currDate: Date?
        try {
            startDate = dateFormat.parse(date)
            currDate = dateFormat.parse(dateFormat.format(Date()))
        } catch (e: ParseException) {
            e.printStackTrace()
            startDate = null
            currDate = null
        }

        if (startDate != null && currDate != null) {
            val diff = currDate.time - startDate.time
            val dayCount = diff.toFloat() / (24 * 60 * 60 * 1000)
            return dayCount.toInt() + 1
        }

        return 0
    }

    // 判断是否有网络访问
    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            connectivityManager.activeNetworkInfo?.isAvailable ?: false
        }
    }
}
