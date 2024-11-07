package com.wuyou.notification.lyric

import de.robv.android.xposed.XposedBridge

object LogUtil {
    fun log(obj: Any?) {
        if (obj == null) {
            XposedBridge.log("焦点通知歌词: null")
        }
        if (obj is Throwable) {
            XposedBridge.log("====焦点通知歌词: error====")
            XposedBridge.log(obj)
            XposedBridge.log("========")
            return
        }
        val text = when (obj) {
            is Iterable<*> -> {
                obj.joinToString("\n")
            }

            is Array<*> -> {
                obj.joinToString("\n")
            }

            else -> {
                obj.toString()
            }
        }
        XposedBridge.log("焦点通知歌词: $text")
    }
}