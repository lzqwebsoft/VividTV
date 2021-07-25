package com.lvvi.vividtv.service

import android.app.*
import android.app.NotificationManager.*
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.lvvi.vividtv.R
import com.lvvi.vividtv.task.DownloadTask
import kotlinx.coroutines.*
import java.io.File

private const val NOTIFICATION_CHANNEL_ID = "com.lvvi.vividtv"

class DownloadService : Service() {
    private var apkUpgradeDirectionPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
    private var saveFilePath: String? = null
    private var downloadUrl: String? = null
    private var downloadTask: DownloadTask? = null
    private val mBinder = DownloadBinder()
    private val downloadJob: Job = Job()
    private val notificationManager: NotificationManager? = null
    private var notificationBuilder: NotificationCompat.Builder? = null

    private val listener: DownloadListener = object : DownloadListener {
        override fun onProgress(progress: Int) {
            showNotification(1, "下载中...", progress)
        }

        override fun onSuccess() {
            Thread.sleep(1500)
            // 下载成功时将前台服务通知关闭，并创建一个下载成功的通知
            showNotification(1, "下载成功", -2)
            stopForeground(true)
            // Toast.makeText(this@DownloadService, "下载成功", Toast.LENGTH_SHORT).show()
            // 下载完成自动跳转至更新画面
            if (saveFilePath != null)
                startActivity(getInstallAppIntent(saveFilePath!!))
        }

        override fun onFailed(msg: String?) {
            // 下载失败时将前台服务通知关闭，并创建一个下载失败的通知
            showNotification(1, "下载失败", -1)
            stopForeground(true)
            // Toast.makeText(this@DownloadService, "下载失败 ${msg ?: ""}" + Thread.currentThread().name, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    inner class DownloadBinder : Binder() {
        fun startDownload(url: String?, saveFileName: String? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    downloadAPK(url, saveFileName)
                } else {
                    // 请求安装未知应用来源的权限
                    startInstallPermissionSettingActivity()
                }
            } else {
                downloadAPK(url, saveFileName)
            }
        }

        /**
         * 开启安装APK权限(适配8.0)
         */
        @RequiresApi(api = Build.VERSION_CODES.O)
        private fun startInstallPermissionSettingActivity() {
            val packageURI = Uri.parse("package:${packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        private fun downloadAPK(url: String?, fileName: String? = null) {
            saveFilePath = if (fileName != null && fileName.isNotBlank()) {
                apkUpgradeDirectionPath + File.separator + fileName
            } else {
                "$apkUpgradeDirectionPath/${fileName}"
            }
            if (downloadTask == null && url != null && url.isNotEmpty()) {
                downloadUrl = url
                downloadTask = DownloadTask(listener, saveFilePath!!)
            }
            if (downloadTask?.isStart == false) {
                Toast.makeText(this@DownloadService, "下载中...", Toast.LENGTH_SHORT).show()
                val coroutineScope = CoroutineScope(downloadJob)
                coroutineScope.async {
                    downloadTask?.execute(downloadUrl!!)
                }

            }
        }

        fun cancelDownload() {
            if (downloadUrl != null && saveFilePath != null) {
                downloadJob.cancel()
                // 取消下载时需将文件删除，并将通知关闭
                val file = File(saveFilePath)
                if (file.exists()) {
                    file.delete()
                }
                getNotificationManager()?.cancel(1)
                stopForeground(true)
                Toast.makeText(this@DownloadService, "取消", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getNotificationManager(): NotificationManager? {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.download_file)
            val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, IMPORTANCE_HIGH)
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            chan.setSound(null, null)
            notificationManager.createNotificationChannel(chan)
        }
        return notificationManager
    }

    private fun getNotification(title: String, progress: Int): Notification {
        val builder: NotificationCompat.Builder = notificationBuilder ?: NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        builder.setSmallIcon(R.mipmap.ic_launcher)
//        builder.setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_avatar))
        if (progress == -2 && saveFilePath != null) {
            val installAppIntent: Intent? = getInstallAppIntent(saveFilePath!!)
            val contentIntent = PendingIntent.getActivity(this, 0, installAppIntent, 0)
            builder.setContentIntent(contentIntent)  // 点击安装
        }
        builder.priority = NotificationCompat.PRIORITY_LOW
        builder.setSound(null)
        builder.setContentTitle(title)
        if (progress >= 0) {
            // 当progress大于或等于0时才需显示下载进度
            builder.setContentText("${progress}%")
            builder.setOngoing(true)
            builder.setProgress(100, progress, false)
        } else if (progress == -1 || progress == -2) {
            builder.setOngoing(false)
            builder.setAutoCancel(true) // 点击通知后通知在通知栏上消失
        }

        return builder.build()
    }

    private fun showNotification(notificationId: Int, title: String, progress: Int) {
        // 判断应用通知是否打开
        val manager = notificationManager ?: getNotificationManager()
        if (manager != null && openNotificationChannel(manager, NOTIFICATION_CHANNEL_ID) != true) {
            return
        }
        manager?.notify(notificationId, getNotification(title, progress))
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun openNotificationChannel(manager: NotificationManager, channelId: String?): Boolean? {
        // 判断通知是否有打开
        if (!isNotificationEnabled()) {
            toNotifySetting(null)
            return false
        }
        // 判断渠道通知是否打开
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel: NotificationChannel? = manager.getNotificationChannel(channelId)
            channel?.setSound(null, null)
            if (channel?.importance == IMPORTANCE_NONE) {
                // 没打开调往设置界面
                toNotifySetting(channel.id)
                return false
            }
        }
        return true
    }

    /**
     * 判断应用通知是否打开
     * @return
     */
    private fun isNotificationEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    /**
     * 手动打开应用通知
     */
    private fun toNotifySetting(channelId: String?) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        // 适配 8.0及8.0以上(8.0需要先打开应用通知，再打开渠道通知)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (TextUtils.isEmpty(channelId)) {
                intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                intent.action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { //适配 5.0及5.0以上
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra("app_package", packageName)
            intent.putExtra("app_uid", applicationInfo.uid)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) { // 适配 4.4及4.4以上
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.data = Uri.fromParts("package", packageName, null)
        } else {
            intent.action = Settings.ACTION_SETTINGS
        }
        startActivity(intent)
    }

    /**
     * 调往系统APK安装界面（适配7.0）
     * @return
     */
    private fun getInstallAppIntent(filePath: String): Intent? {
        // apk文件的本地路F径
        val apkFile = File(filePath)
        if (!apkFile.exists()) {
            return null
        }
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri: Uri? = getUriForFile(apkFile)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        intent.setDataAndType(contentUri, "application/vnd.android.package-archive")
        return intent
    }

    /**
     * 将文件转换成uri
     * @return
     */
    private fun getUriForFile(file: File?): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "$packageName.fileProvider", file!!)
        } else {
            Uri.fromFile(file)
        }
    }
}