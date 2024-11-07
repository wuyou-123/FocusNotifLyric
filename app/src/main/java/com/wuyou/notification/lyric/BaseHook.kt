package com.wuyou.notification.lyric

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.LyricData
import cn.lyric.getter.api.listener.LyricListener
import cn.lyric.getter.api.listener.LyricReceiver
import cn.lyric.getter.api.tools.Tools.registerLyricListener
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.wuyou.notification.lyric.LogUtil.log
import org.json.JSONObject

abstract class BaseHook {
    var isInit: Boolean = false
    val context: Application by lazy { AndroidAppHelper.currentApplication() }

    val receiver = LyricReceiver(object : LyricListener() {
        override fun onUpdate(lyricData: LyricData) {
            try {
                this@BaseHook.onUpdate(lyricData)
            } catch (e: Throwable) {
                log(e)
            }
        }

        override fun onStop(lyricData: LyricData) {
            try {
                this@BaseHook.onStop()
            } catch (e: Throwable) {
                log(e)
            }
        }
    })

    init {
        loadClass("android.app.Application").methodFinder().first { name == "onCreate" }
            .createHook {
                after {
                    registerLyricListener(context, API.API_VERSION, receiver)
                    log("registerLyricListener")
                }
            }
    }

    abstract fun init()
    abstract fun onUpdate(lyricData: LyricData)
    abstract fun onStop()

    @SuppressLint("NotificationPermission")
    fun sendNotification(text: String) {
//        log("sendNotification: " + context.packageName + ": " + text)
        createNotificationChannel()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val bitmap = context.packageManager.getActivityIcon(launchIntent!!).toBitmap()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        builder.setContentTitle(text)
        builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        builder.setTicker(text).setPriority(NotificationCompat.PRIORITY_LOW)
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, 0, launchIntent, PendingIntent.FLAG_MUTABLE
            )
        )
        val jSONObject = JSONObject()
        val jSONObject3 = JSONObject()
        val jSONObject4 = JSONObject()
        jSONObject4.put("type", 1)
        jSONObject4.put("title", text)
        jSONObject3.put("baseInfo", jSONObject4)
        jSONObject3.put("ticker", text)
        jSONObject3.put("tickerPic", "miui.focus.pic_ticker")
        jSONObject3.put("tickerPicDark", "miui.focus.pic_ticker_dark")

        jSONObject.put("param_v2", jSONObject3)
        val bundle = Bundle()
        bundle.putString("miui.focus.param", jSONObject.toString())
        val bundle3 = Bundle()
        bundle3.putParcelable(
            "miui.focus.pic_ticker", Icon.createWithBitmap(bitmap)
        )
        bundle3.putParcelable(
            "miui.focus.pic_ticker_dark", Icon.createWithBitmap(bitmap)
        )
        bundle.putBundle("miui.focus.pics", bundle3)


        builder.addExtras(bundle)
        val notification = builder.build()
        (context.getSystemService("notification") as NotificationManager).notify(
            CHANNEL_ID.hashCode(), notification
        )
    }


    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService("notification") as NotificationManager
        val notificationChannel = NotificationChannel(
            CHANNEL_ID, "焦点通知歌词", NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationChannel.setSound(null, null)
        notificationManager.createNotificationChannel(notificationChannel)
    }


    @SuppressLint("NotificationPermission")
    fun cancelNotification() {
        (context.getSystemService("notification") as NotificationManager).cancel(CHANNEL_ID.hashCode())
    }

    companion object {
        const val CHANNEL_ID: String = "channel_id_focusNotifLyrics"
    }
}