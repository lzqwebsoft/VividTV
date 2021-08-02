package com.lvvi.vividtv.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import cn.leancloud.AVObject
import cn.leancloud.AVQuery
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvvi.vividtv.BuildConfig
import com.lvvi.vividtv.R
import com.lvvi.vividtv.model.VideoDataModelNew
import com.lvvi.vividtv.service.DownloadService
import com.lvvi.vividtv.service.UpdateChannelInfoService
import com.lvvi.vividtv.ui.adapter.ChannelNameAdapter
import com.lvvi.vividtv.utils.Constant
import com.lvvi.vividtv.utils.MyApplication
import com.lvvi.vividtv.utils.MySharePreferences
import com.lvvi.vividtv.utils.Utils
import com.lvvi.vividtv.widget.ConfirmDialog
import com.lvvi.vividtv.widget.MyAlertDialog
import com.lvvi.vividtv.widget.SourceHistoryDialog
import com.lvvi.vividtv.widget.SourceLinkDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

/**
 * 直播视频播放页面
 */
class MediaPlayerActivity : Activity(), SurfaceHolder.Callback, IMediaPlayer.OnCompletionListener, IMediaPlayer.OnPreparedListener,
    IMediaPlayer.OnErrorListener, IMediaPlayer.OnInfoListener, IMediaPlayer.OnBufferingUpdateListener, IjkMediaPlayer.OnNativeInvokeListener {

    companion object {
        private const val TAG = "MediaPlayerActivity"
        private const val CUSTOM_SOURCE_ID = "custom"
        const val PERMISSION_REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 101

        const val HANDLER_BACK = 0
        const val HANDLER_FINISH = 1
        const val HANDLER_AUTO_CLOSE_MENU = 2
        const val HANDLER_AUTO_CLOSE_INFO = 3
        const val HANDLER_AUTO_CLOSE_SETTINGS = 4

        const val AUTO_CLOSE_MENU_DELAY = 5000
        const val AUTO_CLOSE_INFO_DELAY = 3000
        const val FINISH_DELAY = 2000
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"

        const val DEFAULT_VIDEO_URL =
            "https://www.apple.com/105/media/cn/mac/family/2018/46c4b917_abfd_45a3_9b51_" +
                    "4e3054191797/films/bruce/mac-bruce-tpl-cn-2018_1280x720h.mp4"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0"
    }

    private var mediaPlayer: IMediaPlayer? = null
    private var mEnableMediaCodec: Boolean = true    // 是否开启硬解码

    private var lastCacheTime: Long = 0               // 上次缓存停顿的时间点

    private lateinit var mainRl: RelativeLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var nameRl: RelativeLayout      // 播放源面板
    private lateinit var previewIv: ImageView        // 节目预览图片
    private lateinit var nameLv: ListView            // 播放源列表

    private lateinit var sourceLinkBtn: Button       // 播放源按钮
    private lateinit var sourceHistoryBtn: Button    // 播放足迹按钮
    private lateinit var aboutBtn: Button            // 软件关于按钮

    private lateinit var infoLl: LinearLayout        // 节目信息面板
    private lateinit var infoTv: TextView            // 节目名称
    private lateinit var currTv: TextView            // 当前时间
    private lateinit var progressSeekBar: SeekBar    // 节目播放进度
    private lateinit var startTv: TextView           // 节目开始时间
    private lateinit var endTv: TextView             // 节目结束时间

    private lateinit var noNetworkPanel: RelativeLayout   // 无网络面板
    private lateinit var retryBtn: Button                 // 重试按钮

    private lateinit var settingLl: LinearLayout
    private lateinit var settingLottieAnimationView: LottieAnimationView
    private lateinit var settingSeekBar: SeekBar

    private var currNamePosition: Int = 0           // 当前播放频道索引
    private var currLinePosition: Int = 0           // 当前播放频道的直播源索引
    private lateinit var currUrl: String
    private lateinit var currId: String

    private lateinit var simpleDateFormat: SimpleDateFormat

    private lateinit var channelsBeans: List<VideoDataModelNew>
    private lateinit var nameAdapter: ChannelNameAdapter

    private lateinit var handler: MyHandler
    private lateinit var toast: Toast

    private lateinit var connection: ServiceConnection

    private var downloadBinder: DownloadService.DownloadBinder? = null
    private val connection2: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {}
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            downloadBinder = service as DownloadService.DownloadBinder
            downloadBinder?.setActivity(this@MediaPlayerActivity)
        }
    }
    private var isBinderDownloadUpdateService = false
    private var isBinderCancelInfoService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置手机屏幕长亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_media_player)

        initView()
        initData()

        bindDownloadService()
    }

    private fun bindDownloadService() {
        val intent = Intent(this, DownloadService::class.java)
        startService(intent) // 启动服务
        isBinderDownloadUpdateService = bindService(intent, connection2, BIND_AUTO_CREATE) // 绑定服务

        checkVersion()
    }

    // 检查软件版本
    private fun checkVersion() {
        if (!Utils.isNetworkConnected(this)) return
        val query: AVQuery<AVObject> = AVQuery(Constant.AVOBJECT_CLASS_CURRENT_VERIOSN)
        query.addDescendingOrder(Constant.AVOBJECT_VERSION_ID) // 降序
        val subscribe = query.findInBackground().subscribe({ list ->
            if (list.isNotEmpty() && list.first() !== null) {
                var needUpdate = false
                val data = list.first()
                val last = data.getString(Constant.AVOBJECT_LAST_VERSION)
                val url = data.getString(Constant.AVOBJECT_LAST_VERSION_URL)
                val currentVersion = BuildConfig.VERSION_NAME
                try {
                    val versions = currentVersion.split(".")
                    val lastVersions = last.split(".")
                    lastVersions.forEachIndexed { i, s ->
                        val subVersion = s.toUInt()
                        val cSubVersion = versions[i].toUInt()
                        if (cSubVersion < subVersion) {
                            needUpdate = true
                            return@forEachIndexed
                        }
                    }
                } catch (e: java.lang.Exception) {
                    needUpdate = false
                }
                // 查询服务器检查需要更新软件版本
                if (needUpdate) {
                    val confirmDialog = ConfirmDialog(this)
                    confirmDialog.confirmListener = {
                        // 开始下载APK
                        val fileName = "VividTV_v${last}_release.apk"
                        downloadBinder?.startDownload(url, fileName)
                    }
                    confirmDialog.show(
                        getString(R.string.find_new_version), getString(R.string.confirm_upgrade_apk, currentVersion, last),
                        getString(R.string.dialog_cancel_button), getString(R.string.dialog_ok_button)
                    )
                }
            }
        }, { e ->
            Log.e(TAG, e.stackTraceToString())
        })
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onResume() {
        super.onResume()
        initPlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 解绑服务
        if (isBinderDownloadUpdateService)
            unbindService(connection2)
//        if (isBinderCancelInfoService)
//            unbindService(connection)
    }

    // 绑定并启动节目更新服务
    private fun bindService() {
        connection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                // 服务解绑时执行
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                // 服务绑定时执行
            }
        }
        Intent(this, UpdateChannelInfoService::class.java).also { intent ->
            // 绑定服务
            isBinderCancelInfoService = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    // 初始化界面
    private fun initView() {
        val windowManager = windowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val videoSv = findViewById<SurfaceView>(R.id.video_sv)
        val surfaceHolder = videoSv.holder
        surfaceHolder.addCallback(this)
        videoSv.layoutParams = RelativeLayout.LayoutParams(screenWidth, screenHeight)

        mainRl = findViewById(R.id.main_rl)

        progressBar = findViewById(R.id.progressBar)
        nameRl = findViewById(R.id.name_rl)              // 播放源面板
        sourceLinkBtn = findViewById(R.id.source_link)   // 播放源地址按钮
        sourceHistoryBtn = findViewById(R.id.history)    // 播放源足迹按钮
        aboutBtn = findViewById(R.id.about)              // 播放器关于按钮

        infoLl = findViewById(R.id.info_ll)    // 节目信息面板
        infoTv = findViewById(R.id.info_tv)    // 节目名称
        currTv = findViewById(R.id.curr_tv)    // 当前时间
        progressSeekBar = findViewById(R.id.progress_seek_bar)   // 节目播放进度
        startTv = findViewById(R.id.start_tv)   // 节目开始时间
        endTv = findViewById(R.id.end_tv)       // 节目结束时间

        noNetworkPanel = findViewById(R.id.no_network_panel)
        retryBtn = findViewById(R.id.btn_retry)

        progressSeekBar.setPadding(0, 0, 0, 0)
        progressSeekBar.max = 100

        infoLl.layoutParams.width = screenWidth / 3

        // 节目预览图片
        previewIv = findViewById(R.id.preview_iv)
        previewIv.layoutParams.width = screenWidth / 3
        previewIv.layoutParams.height = screenWidth / 3 * 9 / 16

        nameLv = findViewById(R.id.name_lv)
        nameAdapter = ChannelNameAdapter(this)
        nameLv.adapter = nameAdapter
        nameLv.layoutParams.width = screenWidth / 3

        // 点击即切换播放源
        nameLv.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            // 收起节目面板列表
            if (nameRl.visibility == View.VISIBLE) {
                nameRl.visibility = View.GONE
            }
            // 切换当前播放源
            if (currNamePosition != i) {
                currNamePosition = i
                play()
            }
        }

        // 点击播放源按钮，弹出播放源对话框
        sourceLinkBtn.setOnClickListener {
            val dialog = SourceLinkDialog(this)
            dialog.confirmListener = {
                // 收起节目面板列表
                if (nameRl.visibility == View.VISIBLE) {
                    nameRl.visibility = View.GONE
                }
                play(it)
            }
            dialog.show()
        }

        // 点击足迹按钮，弹出足迹对话框
        sourceHistoryBtn.setOnClickListener {
            val dialog = SourceHistoryDialog(this@MediaPlayerActivity)
            dialog.onClickHistoryItem = {
                // 收起节目面板列表
                if (nameRl.visibility == View.VISIBLE) {
                    nameRl.visibility = View.GONE
                }
                play(it)
            }
            dialog.show()
        }

        // 点击关于按钮，弹出软件关于信息
        aboutBtn.setOnClickListener {
            val alert = MyAlertDialog(this@MediaPlayerActivity)
            alert.show("关于", getString(R.string.about_message, BuildConfig.VERSION_NAME))
        }

        // 选择播放源，显示节目预览信息
        nameLv.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                showPreview(view, screenHeight, i)

                nameAdapter.setCheckedPosition(i)
                nameAdapter.notifyDataSetChanged()

                if (handler.hasMessages(HANDLER_AUTO_CLOSE_MENU)) {
                    handler.removeMessages(HANDLER_AUTO_CLOSE_MENU)
                }
                handler.sendEmptyMessageDelayed(HANDLER_AUTO_CLOSE_MENU, AUTO_CLOSE_MENU_DELAY.toLong())
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        handler = MyHandler(this@MediaPlayerActivity)

        //phone
        nameLv.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (handler.hasMessages(HANDLER_AUTO_CLOSE_MENU)) {
                    handler.removeMessages(HANDLER_AUTO_CLOSE_MENU)
                }
                handler.sendEmptyMessageDelayed(HANDLER_AUTO_CLOSE_MENU, AUTO_CLOSE_MENU_DELAY.toLong())
            }

            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
            }
        })

        // 判断是否获取修改系统设置权限
        if (isPermissionEnabled()) {
            setSettingsListener(screenWidth, screenHeight)
        } else {
            mainRl.setOnClickListener {
                if (nameRl.visibility == View.GONE) {
                    openMenu()
                } else {
                    closeMenu()
                }
            }
        }
        // 重试检查是否有网络
        retryBtn.setOnClickListener {
            if (Utils.isNetworkConnected(this)) {
                progressBar.visibility = View.GONE
                noNetworkPanel.visibility = View.GONE

                // 检查获取得到网络则，重新播放
                initPlayer()
                play()
            } else {
                Toast.makeText(this, "没有获取到网络，请重试...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isPermissionEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(this)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setSettingsListener(screenWidth: Int, screenHeight: Int) {
        settingLl = findViewById(R.id.setting_ll)
        settingLottieAnimationView = findViewById(R.id.setting_lottie_animation_view)
        settingSeekBar = findViewById(R.id.setting_seekbar)
        settingSeekBar.setPadding(0, 0, 0, 0)

        var startX = 0f
        var startY = 0f
        var isLightChanging = false
        var currentValue = -1f
        var minValue = 0
        var maxValue = 0
        var lastMovingDistance = 0f
        var percent = 1
        val originalMode = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mainRl.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.x > 100 && event.x < screenWidth / 2 && event.y > 100 && event.y < screenHeight - 100) {
                        // brightness
                        currentValue = Settings.System.getFloat(
                            contentResolver, Settings.System.SCREEN_BRIGHTNESS
                        )
                        minValue = 1
                        maxValue = 255
                        isLightChanging = true
                        settingLottieAnimationView.setAnimation("player_brightness_icon_lottie.json")
                        percent = 1
                    }
                    if (event.x > screenWidth / 2 && event.x < screenWidth - 100 && event.y > 100 && event.y < screenHeight - 100) {
                        //volume
                        currentValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        minValue = 1 / 10
                        maxValue = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        isLightChanging = false
                        settingLottieAnimationView.setAnimation("player_volume_icon_progress_lottie.json")
                        percent = 20
                    }
                    settingSeekBar.max = maxValue * percent
                    startX = event.x
                    startY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (currentValue >= 0) {
                        if (abs(event.y - startY) > 10) {
                            settingLl.visibility = View.VISIBLE
                            if (handler.hasMessages(HANDLER_AUTO_CLOSE_SETTINGS)) {
                                handler.removeMessages(HANDLER_AUTO_CLOSE_SETTINGS)
                            }
                        }

                        currentValue += if (abs(event.y - startY) > lastMovingDistance) {
                            -(event.y - startY) / 20 * maxValue / screenHeight
                        } else {
                            (event.y - startY) / 20 * maxValue / screenHeight
                        }

                        if (currentValue < minValue) {
                            currentValue = minValue.toFloat()
                            startY = event.y
                        } else if (currentValue > maxValue) {
                            currentValue = maxValue.toFloat()
                            startY = event.y
                        }

                        if (isLightChanging) {
                            Settings.System.putInt(
                                contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                            )
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, round(currentValue).toInt())
                        } else {
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                currentValue.toInt(), AudioManager.FLAG_PLAY_SOUND
                            )
                        }

                        if (settingLl.visibility == View.VISIBLE) {
                            settingLottieAnimationView.progress = currentValue / maxValue
                            settingSeekBar.progress = (currentValue * percent).toInt()
                        }
                    }

                    lastMovingDistance = abs(event.y - startY)
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.x - startX) > 10 || abs(event.y - startY) > 10) {
                        currentValue = -1f

                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, originalMode)

                        handler.sendEmptyMessageDelayed(HANDLER_AUTO_CLOSE_SETTINGS, FINISH_DELAY.toLong())
                    } else {
                        if (nameRl.visibility == View.GONE) {
                            openMenu()
                        } else {
                            closeMenu()
                        }
                    }
                }
            }
            true
        }
    }

    // 显示当前的节目预览图片
    private fun showPreview(view: View, screenHeight: Int, i: Int) {
        if (channelsBeans[i].icon == "") {
            previewIv.visibility = View.GONE
            return
        } else {
            previewIv.visibility = View.VISIBLE
        }

        val params = previewIv.layoutParams as RelativeLayout.LayoutParams
        var top = view.top - (previewIv.height / 2 - view.height / 2)
        if (top < 0) {
            top = 0
        } else if (top + previewIv.height > screenHeight) {
            top = screenHeight - previewIv.height
        }
        params.topMargin = top
        previewIv.layoutParams = params

        Glide.with(this@MediaPlayerActivity)
            .load(channelsBeans[i].icon)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(previewIv)
    }

    private fun initData() {
        simpleDateFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

        toast = Toast.makeText(this, R.string.app_name, Toast.LENGTH_LONG)

        currUrl = DEFAULT_VIDEO_URL
        val bundle = intent.extras
        if (bundle != null) {
            currId = bundle.getString(EXTRA_ID, "")
            currUrl = bundle.getString(EXTRA_URL, DEFAULT_VIDEO_URL)
        }

        channelsBeans = MyApplication.get().getVideoData(this)

        nameAdapter.setCurrId(currId)
        nameAdapter.setData(channelsBeans)

        currNamePosition = 0
        currLinePosition = 0

        if (channelsBeans.isNotEmpty()) {
            if (currId == "") {
                currId = channelsBeans[currNamePosition].id!!
            } else {
                for (i in channelsBeans.indices) {
                    if (currId == channelsBeans[i].id) {
                        currNamePosition = i
                    }
                }
            }
        }
    }

    private fun showInfo() {
        if (currNamePosition >= channelsBeans.size) {
            return
        }

        val startTimeStr = channelsBeans[currNamePosition].startTime
        val endTimeStr = channelsBeans[currNamePosition].endTime
        if (startTimeStr == null || startTimeStr == "") {
            return
        }
        if (endTimeStr == null || endTimeStr == "") {
            return
        }

        infoLl.visibility = View.VISIBLE

        infoTv.text = channelsBeans[currNamePosition].title
        currTv.text = simpleDateFormat.format(Date(System.currentTimeMillis()))

        val curr = System.currentTimeMillis() - (startTimeStr.toLong() * 1000)
        val total = endTimeStr.toLong() * 1000 - startTimeStr.toLong() * 1000

        val progress = curr * 100 / total * 100
        progressSeekBar.progress = progress.toInt() / 100

        val startTime = simpleDateFormat.format(Date(startTimeStr.toLong() * 1000))
        startTv.text = startTime
        val endTime = simpleDateFormat.format(Date(endTimeStr.toLong() * 1000))
        endTv.text = endTime

        if (handler.hasMessages(HANDLER_AUTO_CLOSE_INFO)) {
            handler.removeMessages(HANDLER_AUTO_CLOSE_INFO)
        }
        handler.sendEmptyMessageDelayed(HANDLER_AUTO_CLOSE_INFO, AUTO_CLOSE_INFO_DELAY.toLong())
    }

    private fun initPlayer() {
        mediaPlayer = createPlayer()
        mediaPlayer?.setOnPreparedListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.setOnInfoListener(this)
        mediaPlayer?.setOnBufferingUpdateListener(this)
        mediaPlayer?.setOnCompletionListener(this)

        try {
            mediaPlayer?.dataSource = currUrl
        } catch (e: IOException) {
            e.printStackTrace()
            toast.setText(R.string.cant_play_tip)
            toast.show()
        }
    }

    // 创建一个新的player
    private fun createPlayer(): IMediaPlayer? {
        // init player
        IjkMediaPlayer.loadLibrariesOnce(null)
        IjkMediaPlayer.native_profileBegin("libijkplayer.so")

        val ijkMediaPlayer = IjkMediaPlayer()
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1)    // 不符合规范的优化，默认是关闭
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "loop", Int.MAX_VALUE.toLong())   // 设置循环播放次数，默认是1
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1)  // 不要限制输入缓冲区大小（对实时流很有用）”,默认是0关闭
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 15) // 当CPU过慢时，丢掉的帧数，默认是0，最大是120
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1)  // 准备好后，自动播放
//        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)  // （去块效应）滤波 ，默认为48，设置0后比较清晰
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "min-frames", 500)      // 停止预读的最小帧数，默认是50000
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0)  // 启动精确的跳播
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-fps", 30)          // 最大FPS
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L) // 关闭暂停输出，直到packet包缓存完毕,默认是1
//        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "rtmp,crypto,file,http,https,tcp,tls,udp")
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1)         // 重连打开
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30000000L)   // 30s超时,默认30s
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", USER_AGENT)      // HTTP请求代理
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1)
//        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 100L)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 64L)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L)
        ijkMediaPlayer.setVolume(1.0f, 1.0f)
        setEnableMediaCodec(ijkMediaPlayer, mEnableMediaCodec)

        // 播放器网络回调的监听
        ijkMediaPlayer.setOnNativeInvokeListener(this)
        return ijkMediaPlayer
    }

    //设置是否开启硬解码
    private fun setEnableMediaCodec(ijkMediaPlayer: IjkMediaPlayer, isEnable: Boolean) {
        val value = if (isEnable) 1 else 0
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-all-videos", value.toLong()) // 开启硬解码
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", value.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", value.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-sync", value.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", value.toLong())   // OpenSL ES 开关
//        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", value.toLong())
    }

    fun setEnableMediaCodec(isEnable: Boolean) {
        mEnableMediaCodec = isEnable
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated: ")
        mediaPlayer?.setDisplay(surfaceHolder)
        mediaPlayer?.prepareAsync()
    }

    override fun surfaceChanged(surfaceHolder: SurfaceHolder, i: Int, i1: Int, i2: Int) {
        Log.i(TAG, "surfaceChanged: ")
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        Log.i(TAG, "surfaceDestroyed: ")
    }

    override fun onCompletion(mediaPlayer: IMediaPlayer) {
        Log.i(TAG, "onCompletion: ")
        // 播放结束时回调重播
        mediaPlayer.release()
        initPlayer()
        play(currUrl)
    }

    override fun onPrepared(mediaPlayer: IMediaPlayer) {
        Log.i(TAG, "onPrepared: ----->")
        if (progressBar.visibility == View.VISIBLE) {
            progressBar.visibility = View.GONE
        }

        Log.e(TAG, "onPrepared VideoWidth: " + mediaPlayer.videoWidth)
        Log.e(TAG, "onPrepared VideoHeight: " + mediaPlayer.videoHeight)

        mediaPlayer.start()

        // 如果是custom，自定义的播放源，则将其保存到本地足迹中
        if (currId == CUSTOM_SOURCE_ID) {
            MySharePreferences.getInstance(this).putHistoryItem(currUrl)

            // 将有效的对象保存到云端(首先要去重)
            if (Utils.isNetworkConnected(this)) {
                val query: AVQuery<AVObject> = AVQuery(Constant.AVOBJECT_CLASS_CUSTOM_VIDEO)
                query.whereEqualTo("url", currUrl)
                query.findInBackground().subscribe({ list ->
                    if (list.isEmpty()) {
                        // 找不到则将其保存在服务端
                        val todo = AVObject(Constant.AVOBJECT_CLASS_CUSTOM_VIDEO)
                        todo.put("url", currUrl)
                        todo.put("time", Date().time / 1000)
                        todo.saveInBackground().subscribe({
                            Log.i(TAG, "保存成功。objectId：${it.objectId}")
                        }, { e ->
                            Log.e(TAG, e.stackTraceToString())
                        })
                    }
                }, { e ->
                    Log.e(TAG, e.stackTraceToString())
                })
            }
        } else {
            showInfo()
        }
    }

    // 播放器播放状态回调
    override fun onInfo(mediaPlayer: IMediaPlayer, i: Int, i1: Int): Boolean {
        Log.e(TAG, "onInfo: i: $i i1: $i1")
        noNetworkPanel.visibility = View.GONE   // 有Info说明有动态，视频播放是活着的，则将无网络面板隐藏
        when (i) {
            IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING -> Log.e(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:")
            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> Log.e(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:")
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                // 开始缓冲，暂停
                Log.e(TAG, "MEDIA_INFO_BUFFERING_START:")
                // 延时3秒显示加载进度条
                val current = Date().time
                if (lastCacheTime == 0L || current - lastCacheTime > 3000) {
                    if (progressBar.visibility == View.GONE) {
                        progressBar.visibility = View.VISIBLE
                    }
                }
                lastCacheTime = current
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            }
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                Log.e(TAG, "MEDIA_INFO_BUFFERING_END:")
                // 缓冲结束，重新播放
                if (progressBar.visibility == View.VISIBLE) {
                    progressBar.visibility = View.GONE
                }
                mediaPlayer.start()
            }
            IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH -> Log.e(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: $i")
            IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING -> Log.e(TAG, "MEDIA_INFO_BAD_INTERLEAVING:")
            IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE -> Log.e(TAG, "MEDIA_INFO_NOT_SEEKABLE:")
            IMediaPlayer.MEDIA_INFO_METADATA_UPDATE -> Log.e(TAG, "MEDIA_INFO_METADATA_UPDATE:")
            IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE -> Log.e(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:")
            IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT -> Log.e(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:")
            IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED -> {
                Log.e(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: $i1")
//                findViewById<SurfaceView>(R.id.video_sv)?.setVideoRotation(i1)
            }
            IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START -> Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:")
        }
        return true
    }

    // 播放器缓存状态更新回调
    override fun onBufferingUpdate(mediaPlayer: IMediaPlayer, i: Int) {
        Log.i(TAG, "onBufferingUpdate: $i")
    }

    // 播放器发生错误时回调
    override fun onError(mediaPlayer: IMediaPlayer, framework_err: Int, impl_err: Int): Boolean {
        Log.e(TAG, "onError: i: $framework_err i1: $impl_err")
        if (Utils.isNetworkConnected(this)) {
            if (currUrl == channelsBeans[currNamePosition].url1) {
                tryOtherLine()
            } else {
                showCantPlayTip()
                if (framework_err == -10000) {
                    currUrl = channelsBeans[currNamePosition].url1.toString()
                    currId = channelsBeans[currNamePosition].id!!
                    mediaPlayer.release()
                    initPlayer()
                    play(currUrl)
                }
            }
        } else {
            // 没有网络显示无网络页面
            progressBar.visibility = View.GONE
            noNetworkPanel.visibility = View.VISIBLE
        }
        return true
    }

    // 监听播放器网络播放处理（IJKPlayer播方器独有回调）
    override fun onNativeInvoke(what: Int, args: Bundle?): Boolean {
        Log.i(TAG, "what: $what, $args")
        if (what == IjkMediaPlayer.OnNativeInvokeListener.EVENT_DID_HTTP_OPEN && args != null) {
            val fileSize = args.getLong(IjkMediaPlayer.OnNativeInvokeListener.ARG_FILE_SIZE)
            val error = args.getInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_ERROR)
            val httpCode = args.getInt(IjkMediaPlayer.OnNativeInvokeListener.ARG_HTTP_CODE)
            if (fileSize == -1L && httpCode == 0 && error == -1001) {
                // 连接直播源服务器失败，这个时候检查网络连接问题
                val job = Job()
                val coroutineScope = CoroutineScope(job)
                coroutineScope.launch {
                    var socket: Socket? = null
                    try {
                        socket = Socket()
                        socket.connect(InetSocketAddress("223.5.5.5", 53), 1500) // 阿里公共DNS
                        socket.keepAlive = true
                        socket.soTimeout = 10
                        if (socket.isConnected) {
                            socket.sendUrgentData(0xFF)
                            Log.d(TAG, "连接成功")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "没有网络：${e.message}")
                        CoroutineScope(Dispatchers.Main).launch {
                            progressBar.visibility = View.GONE
                            noNetworkPanel.visibility = View.VISIBLE
                        }
                    } finally {
                        socket?.close()
                    }
                }
            }
        }
        return true
    }

    // 显示不可播放
    private fun showCantPlayTip() {
        progressBar.visibility = View.GONE
        toast.setText(R.string.cant_play_tip)
        toast.show()
    }

    // 播放服务器中设置的播放源
    private fun play() {
        if (currNamePosition < channelsBeans.size) {
            currId = channelsBeans[currNamePosition].id!!
            currUrl = channelsBeans[currNamePosition].url1!!

            currLinePosition = 0

            play(currUrl, currId)
        }
    }

    // 播放用户自定义的播放源
    private fun play(sourceURL: String, sourceID: String = CUSTOM_SOURCE_ID) {
        if (mediaPlayer != null) {
            progressBar.visibility = View.VISIBLE
            mediaPlayer?.reset()
            // 切换直播源需在重新设置一下SurfaceView
            val videoSv = findViewById<SurfaceView>(R.id.video_sv)
            mediaPlayer?.setDisplay(videoSv.holder)
            try {
                currId = sourceID
                currUrl = sourceURL

                saveLastData()

                mediaPlayer?.dataSource = currUrl
                mediaPlayer?.setScreenOnWhilePlaying(true)

                nameAdapter.setCurrId(currId)
                nameAdapter.notifyDataSetChanged()

                mediaPlayer?.prepareAsync()
            } catch (e: IOException) {
                e.printStackTrace()
                showCantPlayTip()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                showCantPlayTip()
            }
        }
    }

    // 切换其他源尝试播放
    private fun tryOtherLine() {
        try {
            if (channelsBeans[currNamePosition].url2!!.isNotEmpty()) {
                currLinePosition += 1
                currUrl = channelsBeans[currNamePosition].url2!!

                play(currUrl, currId)
            } else {
                showCantPlayTip()
            }
        } catch (e: Exception) {
            showCantPlayTip()
        }
    }

    // 记录当前播放器的直播源，方便应用下次还原播放
    private fun saveLastData() {
        MyApplication.get().setLastId(this, currId)
        MyApplication.get().setLastUrl(this, currUrl)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (!closeMenu()) {
                    handler.sendEmptyMessage(HANDLER_BACK)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                openMenu()
                return false
            }
            KeyEvent.KEYCODE_MENU -> return false
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isOpenMenu()) return false
                // 上键上翻频道
                currNamePosition = if (currNamePosition - 1 >= 0) currNamePosition - 1 else channelsBeans.size - 1
                play()
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isOpenMenu()) return false
                // 下键下翻频道
                currNamePosition = if (currNamePosition + 1 < channelsBeans.size) currNamePosition + 1 else 0
                play()
                return false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isOpenMenu()) return false
                mediaPlayer?.seekTo(mediaPlayer?.currentPosition!!.minus(60 * 1000))
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isOpenMenu()) return false
                mediaPlayer?.seekTo(mediaPlayer?.currentPosition!!.plus(60 * 1000))
                return false
            }
            else -> return super.onKeyDown(keyCode, event)
        }
    }

    private fun closeMenu(): Boolean {
        if (handler.hasMessages(HANDLER_AUTO_CLOSE_MENU)) {
            handler.removeMessages(HANDLER_AUTO_CLOSE_MENU)
        }
        return if (nameRl.visibility == View.VISIBLE) {
            nameRl.visibility = View.GONE
            true
        } else {
            false
        }
    }

    private fun isOpenMenu(): Boolean {
        return nameRl.visibility == View.VISIBLE
    }

    private fun openMenu() {
        if (nameRl.visibility == View.GONE) {
            nameRl.visibility = View.VISIBLE
        } else {
            return
        }

        channelsBeans = MyApplication.get().getVideoData(this@MediaPlayerActivity)
        nameAdapter.setCurrId(currId)

        if (currNamePosition >= nameLv.count) {
            nameLv.setSelection(0)
            nameAdapter.setCheckedPosition(0)
        } else {
            nameLv.setSelection(currNamePosition)
            nameAdapter.setCheckedPosition(currNamePosition)
        }
        nameAdapter.setData(channelsBeans)

        if (handler.hasMessages(HANDLER_AUTO_CLOSE_MENU)) {
            handler.removeMessages(HANDLER_AUTO_CLOSE_MENU)
        }
        handler.sendEmptyMessageDelayed(HANDLER_AUTO_CLOSE_MENU, AUTO_CLOSE_MENU_DELAY.toLong())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PERMISSION_REQUEST_MANAGE_UNKNOWN_APP_SOURCES -> {
                downloadBinder?.restartDownload()
            }
        }
    }

    private class MyHandler(activity: MediaPlayerActivity) : Handler() {

        var weakReference: WeakReference<MediaPlayerActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            val mediaPlayerActivity = weakReference.get() ?: return
            when (msg.what) {
                HANDLER_BACK -> if (hasMessages(HANDLER_FINISH)) {
                    mediaPlayerActivity.toast.cancel()
                    mediaPlayerActivity.finish()
                } else {
                    mediaPlayerActivity.toast.setText(mediaPlayerActivity.getString(R.string.exit_tip))
                    mediaPlayerActivity.toast.show()

                    sendEmptyMessageDelayed(HANDLER_FINISH, FINISH_DELAY.toLong())
                }
                HANDLER_FINISH -> removeMessages(HANDLER_FINISH)
                HANDLER_AUTO_CLOSE_MENU -> mediaPlayerActivity.closeMenu()
                HANDLER_AUTO_CLOSE_INFO -> mediaPlayerActivity.infoLl.visibility = View.GONE
                HANDLER_AUTO_CLOSE_SETTINGS -> {
                    mediaPlayerActivity.settingLl.visibility = View.GONE
                    mediaPlayerActivity.settingLottieAnimationView.cancelAnimation()
                    mediaPlayerActivity.settingSeekBar.progress = 0
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer != null) {
            if (mediaPlayer?.isPlaying!!) {
                mediaPlayer?.pause()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        closeMenu()
        if (handler.hasMessages(HANDLER_AUTO_CLOSE_INFO)) {
            handler.removeMessages(HANDLER_AUTO_CLOSE_INFO)
        }
        if (mediaPlayer != null) {
            if (mediaPlayer?.isPlaying!!) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        }
        unbindService(connection)
    }
}
