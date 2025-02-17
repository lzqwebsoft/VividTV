package com.lvvi.vividtv.utils

import android.content.Context
import android.util.Log
import androidx.multidex.MultiDexApplication
import cn.leancloud.AVLogger
import cn.leancloud.AVOSCloud
import cn.leancloud.AVObject
import cn.leancloud.AVQuery
import com.lvvi.vividtv.model.VideoDataModelNew
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

/**
 * Created by lvliheng on 2018/7/12 at 17:08.
 */
class MyApplication : MultiDexApplication() {

    companion object {
        lateinit var context: MyApplication
        private var myApplication: MyApplication? = null
            get() {
                if (field == null) {
                    field = MyApplication()
                }
                return field
            }

        fun get(): MyApplication {
            return myApplication!!
        }
    }

    private var sharedPreferences: MySharePreferences? = null
    private var gson: Gson? = null

    override fun onCreate() {
        super.onCreate()
        init()
        setVideoData()
    }

    private fun init() {
        // 初始化LeanCloud云平台
        AVOSCloud.initialize(this, Constant.LEANCLOUD_APP_ID, Constant.LEANCLOUD_APP_KEY)
        AVOSCloud.setLogLevel(AVLogger.Level.DEBUG)  // 开启DEBUG

        context = this
        sharedPreferences = MySharePreferences.getInstance(context)
        gson = Gson()
    }

    // 从LeanCloud平台中获取电视直播资源
    private fun setVideoData() {
        val query = AVQuery<AVObject>(Constant.AVOBJECT_CLASS_VIDEO_DATA)
        query.addAscendingOrder(Constant.AVOBJECT_ORDER)
        query.whereEqualTo(Constant.IS_SHOW, "1")

        query.findInBackground().subscribe(object : Observer<List<AVObject>> {
            override fun onSubscribe(d: Disposable) {
                Log.e("application", "setVideoData onSubscribe")
            }

            override fun onNext(avObjects: List<AVObject>) {
                Log.e("application", "setVideoData onNext")
                val videoDataList = ArrayList<VideoDataModelNew>()
                var videoData: VideoDataModelNew?
                for (avObject in avObjects) {
                    videoData = VideoDataModelNew()
                    videoData.id = avObject.getString(Constant.AVOBJECT_ID)
                    videoData.name = avObject.getString(Constant.AVOBJECT_NAME)
                    videoData.icon = avObject.getString(Constant.AVOBJECT_ICON)
                    videoData.url1 = avObject.getString(Constant.AVOBJECT_URL1)
                    videoData.url2 = avObject.getString(Constant.AVOBJECT_URL2)
                    videoData.title = ""
                    videoData.startTime = ""
                    videoData.endTime = ""

                    Log.e("---Data--->", videoData.toString())
                    videoDataList.add(videoData)
                }

                if (videoDataList.size > 0) {
                    if (sharedPreferences == null) {
                        sharedPreferences = MySharePreferences.getInstance(context)
                    }

                    sharedPreferences?.putString(Constant.MEDIA_DATA, gson!!.toJson(videoDataList))
                    Log.e("service", "size: ${videoDataList.size}")
                } else {
                    setLocalVideoData()
                }

//                startUpdateChannelInfoService()
            }

            override fun onError(e: Throwable) {
                Log.e("application", "setVideoData onError")
                setLocalVideoData()
//                startUpdateChannelInfoService()
            }

            override fun onComplete() {
                Log.e("application", "setVideoData onComplete")
            }
        })
    }

    private fun setLocalVideoData() {
        if (gson == null) {
            gson = Gson()
        }

        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }

        val mediaData = sharedPreferences!!.getString(Constant.MEDIA_DATA)

        if (mediaData!!.isEmpty()) {
            var result = ""
            try {
                val inputStream = assets.open("video_data.json")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                result = String(buffer, Charset.forName("UTF-8"))
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (result.isNotEmpty()) {
                sharedPreferences?.putString(Constant.MEDIA_DATA, result)
            }
        }

    }

    fun getVideoData(context: Context): List<VideoDataModelNew> {
        var channelsBeans: List<VideoDataModelNew> = ArrayList()
        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }
        if (gson == null) {
            gson = Gson()
        }
        val mediaData = sharedPreferences!!.getString(Constant.MEDIA_DATA)
        if (mediaData != "") {
            channelsBeans = gson!!.fromJson(
                mediaData,
                object : TypeToken<List<VideoDataModelNew>>() {

                }.type
            )
        }
        return channelsBeans
    }

    fun setLastId(context: Context, id: String) {
        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }
        sharedPreferences!!.putString(Constant.LAST_MEDIA_ID, id)
    }

    fun getLastId(context: Context): String? {
        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }
        return sharedPreferences!!.getString(Constant.LAST_MEDIA_ID)
    }

    fun setLastUrl(context: Context, url: String) {
        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }
        sharedPreferences!!.putString(Constant.LAST_MEDIA_URL, url)
    }

    fun getLastUrl(context: Context): String? {
        if (sharedPreferences == null) {
            sharedPreferences = MySharePreferences.getInstance(context)
        }
        return sharedPreferences!!.getString(Constant.LAST_MEDIA_URL)
    }

}
