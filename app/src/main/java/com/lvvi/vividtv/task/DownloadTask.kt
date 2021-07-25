package com.lvvi.vividtv.task

import android.util.Log
import cn.leancloud.AVFile
import com.lvvi.vividtv.service.DownloadListener
import java.io.*

private const val TAG = "DownloadTask"

class DownloadTask(downloadListener: DownloadListener, saveFilePath: String) {
    private val downloadListener = downloadListener
    private val saveFilePath = saveFilePath
    var isStart = false

    private fun writeResponseBodyToDisk(data: ByteArray): Boolean {
        val inputStream: InputStream = ByteArrayInputStream(data)
        var outputStream: OutputStream? = null
        return try {
            val file = File(saveFilePath)
            val fileSize = data.size
            val fileReader = ByteArray(4096)
            var fileSizeDownloaded: Long = 0      // 记录已下载的文件长度

            outputStream = FileOutputStream(file)
            while (true) {
                val read: Int = inputStream.read(fileReader)
                if (read == -1) {
                    break
                }
                outputStream.write(fileReader, 0, read)
                fileSizeDownloaded += read.toLong()
                // 计算已下载的百分比

                // 计算已下载的百分比
                val progress = (fileSizeDownloaded * 100 / fileSize).toInt()
                Log.d(TAG, "file download: $fileSizeDownloaded of $fileSize")
                onProgressUpdate(progress)
            }
            outputStream.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, e.stackTraceToString())
            false
        } finally {
            inputStream.close()
            outputStream?.close()
        }
    }

    fun execute(url: String) {
        isStart = true
        try {
            val file = AVFile("app.apk", url)
            val data = file.data
            val result = writeResponseBodyToDisk(data)
            onPostExecute(result)
        } catch (e: Exception) {
            Log.e(TAG, e.stackTraceToString())
            onError(e.message)
        }
    }

    private fun onProgressUpdate(progress: Int) {
        downloadListener.onProgress(progress)
    }

    private fun onError(message: String?) {
        downloadListener.onFailed(message)
        isStart = false
    }

    private fun onPostExecute(result: Boolean) {
        if (result)
            downloadListener.onSuccess()
        else
            downloadListener.onFailed()
        isStart = false
    }

}

