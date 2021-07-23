package com.lvvi.vividtv.service

interface DownloadListener {
    fun onProgress(progress: Int)
    fun onSuccess()
    fun onFailed(msg: String? = null)
}