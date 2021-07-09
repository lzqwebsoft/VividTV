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
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.lvvi.vividtv.R
import com.lvvi.vividtv.model.VideoDataModelNew
import com.lvvi.vividtv.service.UpdateChannelInfoService
import com.lvvi.vividtv.ui.adapter.ChannelNameAdapter
import com.lvvi.vividtv.utils.MyApplication
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.round

/**
 * 直播视频播放页面
 */
class MediaPlayerActivity : Activity(), SurfaceHolder.Callback, IMediaPlayer.OnCompletionListener, IMediaPlayer.OnPreparedListener,
    IMediaPlayer.OnErrorListener, IMediaPlayer.OnInfoListener, IMediaPlayer.OnBufferingUpdateListener {

    companion object {

        private const val TAG = "MediaPlayerActivity"

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
    }

    private var mediaPlayer: IMediaPlayer? = null
    private var mEnableMediaCodec: Boolean = false    // 是否开启硬解码

    private lateinit var mainRl: RelativeLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var nameRl: RelativeLayout      // 播放源面板
    private lateinit var previewIv: ImageView        // 节目预览图片
    private lateinit var nameLv: ListView            // 播放源列表

    private lateinit var infoLl: LinearLayout        // 节目信息面板
    private lateinit var infoTv: TextView            // 节目名称
    private lateinit var currTv: TextView            // 当前时间
    private lateinit var progressSeekBar: SeekBar    // 节目播放进度
    private lateinit var startTv: TextView           // 节目开始时间
    private lateinit var endTv: TextView             // 节目结束时间

    private lateinit var settingLl: LinearLayout
    private lateinit var settingLottieAnimationView: LottieAnimationView
    private lateinit var settingSeekBar: SeekBar

    private var currNamePosition: Int = 0
    private var currLinePosition: Int = 0
    private lateinit var currUrl: String
    private lateinit var currId: String

    private lateinit var simpleDateFormat: SimpleDateFormat

    private lateinit var channelsBeans: List<VideoDataModelNew>
    private lateinit var nameAdapter: ChannelNameAdapter

    private lateinit var handler: MyHandler
    private lateinit var toast: Toast

    private lateinit var connection: ServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置手机屏幕长亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_media_player)

        initView()
        initData()
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onResume() {
        super.onResume()
        initPlayer()
    }

    // 绑定并启动节目更新服务
    private fun bindService() {
        connection = object : ServiceConnection {
            override fun onServiceDisconnected(p0: ComponentName?) {
            }

            override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {

            }
        }
        Intent(this, UpdateChannelInfoService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
        nameRl = findViewById(R.id.name_rl)   // 播放源面板

        infoLl = findViewById(R.id.info_ll)    // 节目信息面板
        infoTv = findViewById(R.id.info_tv)    // 节目名称
        currTv = findViewById(R.id.curr_tv)    // 当前时间
        progressSeekBar = findViewById(R.id.progress_seek_bar)   // 节目播放进度
        startTv = findViewById(R.id.start_tv)   // 节目开始时间
        endTv = findViewById(R.id.end_tv)       // 节目结束时间

        progressSeekBar.setPadding(0, 0, 0, 0)
        progressSeekBar.max = 100

        infoLl.layoutParams.width = screenWidth / 3

        // 节目预览图片
        previewIv = findViewById(R.id.preview_iv)
        previewIv.layoutParams.width = screenWidth / 3
        previewIv.layoutParams.height = screenWidth / 3 * 9 / 16

        nameLv = findViewById(R.id.name_lv)
        nameAdapter = ChannelNameAdapter(this@MediaPlayerActivity)
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

        //phone
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
    }

    private fun isPermissionEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(this)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) ==
                    PackageManager.PERMISSION_GRANTED
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
        val originalMode =
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mainRl.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.x > 100 && event.x < screenWidth / 2
                        && event.y > 100 && event.y < screenHeight - 100
                    ) {
                        //brightness
                        currentValue = Settings.System.getFloat(
                            contentResolver, Settings.System.SCREEN_BRIGHTNESS
                        )
                        minValue = 1
                        maxValue = 255
                        isLightChanging = true
                        settingLottieAnimationView.setAnimation("player_brightness_icon_lottie.json")
                        percent = 1
                    }
                    if (event.x > screenWidth / 2 && event.x < screenWidth - 100
                        && event.y > 100 && event.y < screenHeight - 100
                    ) {
                        //volume
                        currentValue =
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
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
                            Settings.System.putInt(
                                contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS, round(currentValue).toInt()
                            )
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

                        Settings.System.putInt(
                            contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE, originalMode
                        )

                        handler.sendEmptyMessageDelayed(
                            HANDLER_AUTO_CLOSE_SETTINGS,
                            FINISH_DELAY.toLong()
                        )
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

        toast = Toast.makeText(this@MediaPlayerActivity, R.string.app_name, Toast.LENGTH_LONG)

        currUrl = DEFAULT_VIDEO_URL
        val bundle = intent.extras
        if (bundle != null) {
            currId = bundle.getString(EXTRA_ID, "")
            currUrl = bundle.getString(EXTRA_URL, DEFAULT_VIDEO_URL)
        }

        channelsBeans = MyApplication.get().getVideoData(this@MediaPlayerActivity)

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
        val ijkMediaPlayer = IjkMediaPlayer()
        IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 3)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 1)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "min-frames", 100)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "protocol_whitelist", "rtmp,crypto,file,http,https,tcp,tls,udp");
        ijkMediaPlayer.setVolume(1.0f, 1.0f)
        setEnableMediaCodec(ijkMediaPlayer, mEnableMediaCodec)
        return ijkMediaPlayer
    }

    //设置是否开启硬解码
    private fun setEnableMediaCodec(ijkMediaPlayer: IjkMediaPlayer, isEnable: Boolean) {
        val value = if (isEnable) 1 else 0
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", value.toLong()) //开启硬解码
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", value.toLong())
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", value.toLong())
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
    }

    override fun onPrepared(mediaPlayer: IMediaPlayer) {
        Log.i(TAG, "onPrepared: ")
        if (progressBar.visibility == View.VISIBLE) {
            progressBar.visibility = View.GONE
        }

        Log.e(TAG, "onPrepared: " + mediaPlayer.videoWidth)
        Log.e(TAG, "onPrepared: " + mediaPlayer.videoHeight)

        mediaPlayer.start()
        showInfo()
    }

    override fun onError(mediaPlayer: IMediaPlayer, i: Int, i1: Int): Boolean {
        Log.e(TAG, "onError: i: $i i1: $i1")
//        if (i == -10000) {
//            mediaPlayer.reset()
//        }
        if (currUrl == channelsBeans[currNamePosition].url1) {
            tryOtherLine()
        } else {
            showCantPlayTip()
        }
        return false
    }

    private fun showCantPlayTip() {
        if (progressBar.visibility == View.VISIBLE) {
            progressBar.visibility = View.GONE
        }
        toast.setText(R.string.cant_play_tip)
        toast.show()
    }

    private fun play() {
        if (mediaPlayer != null) {
            if (progressBar.visibility == View.GONE) {
                progressBar.visibility = View.VISIBLE
            }
            mediaPlayer?.reset()
//            mediaPlayer?.release()
//            mediaPlayer = null
//            initPlayer()

            val videoSv = findViewById<SurfaceView>(R.id.video_sv)
            mediaPlayer?.setDisplay(videoSv.holder)
            try {
                if (currNamePosition < channelsBeans.size) {
                    currId = channelsBeans[currNamePosition].id!!
                    currUrl = channelsBeans[currNamePosition].url1!!

                    saveLastData()

                    mediaPlayer?.dataSource = currUrl

                    nameAdapter.setCurrId(currId)
                    nameAdapter.notifyDataSetChanged()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                showCantPlayTip()
            }

            mediaPlayer?.prepareAsync()
        }
    }

    private fun tryOtherLine() {
        try {
            if (channelsBeans[currNamePosition].url2!!.isNotEmpty()) {
                currLinePosition += 1
                currUrl = channelsBeans[currNamePosition].url2!!

                saveLastData()

                val videoSv = findViewById<SurfaceView>(R.id.video_sv)
                mediaPlayer?.setDisplay(videoSv.holder)
                mediaPlayer?.dataSource = currUrl
            } else {
                showCantPlayTip()
            }
        } catch (e: Exception) {
            showCantPlayTip()
        }
    }

    private fun saveLastData() {
        MyApplication.get().setLastId(this@MediaPlayerActivity, currId)
        MyApplication.get().setLastUrl(this@MediaPlayerActivity, currUrl)
    }

    override fun onInfo(mediaPlayer: IMediaPlayer, i: Int, i1: Int): Boolean {
        Log.e(TAG, "onInfo: i: $i i1: $i1")
        return false
    }

    override fun onBufferingUpdate(mediaPlayer: IMediaPlayer, i: Int) {
        Log.i(TAG, "onBufferingUpdate: $i")
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
            KeyEvent.KEYCODE_DPAD_UP -> return false
            KeyEvent.KEYCODE_DPAD_DOWN -> return false
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                mediaPlayer?.seekTo(mediaPlayer?.currentPosition!!.minus(60 * 1000))
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
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

    private class MyHandler(activity: MediaPlayerActivity) : Handler() {

        internal var weakReference: WeakReference<MediaPlayerActivity> = WeakReference(activity)

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
