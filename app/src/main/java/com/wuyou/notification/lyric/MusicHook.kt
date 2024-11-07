package com.wuyou.notification.lyric

import cn.lyric.getter.api.API
import cn.lyric.getter.api.data.LyricData
import cn.lyric.getter.api.data.type.OperateType
import cn.lyric.getter.api.listener.LyricListener
import cn.lyric.getter.api.listener.LyricReceiver
import cn.lyric.getter.api.tools.Tools.registerLyricListener
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.wuyou.notification.lyric.LogUtil.log

object MusicHook : BaseHook() {
    override fun init() {
        val receiver = LyricReceiver(object : LyricListener() {
            override fun onUpdate(lyricData: LyricData) {
                fun getLyric(): String {
                    return if (lyricData.type == OperateType.UPDATE) lyricData.lyric else ""
                }

                val pkgName = lyricData.extraData.packageName
                if (pkgName == context.packageName) {
                    try {
                        sendNotification(getLyric())
                    } catch (e: Throwable) {
                        log(e)
                    }
                }
            }

            override fun onStop(lyricData: LyricData) {
                cancelNotification()
            }
        })
        loadClass("android.app.Application").methodFinder().first { name == "onCreate" }
            .createHook {
                after {
                    registerLyricListener(context, API.API_VERSION, receiver)
                    log("registerLyricListener")
                }
            }
    }

}