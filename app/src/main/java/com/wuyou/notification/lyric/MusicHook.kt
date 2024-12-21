package com.wuyou.notification.lyric

import cn.lyric.getter.api.data.LyricData
import cn.lyric.getter.api.data.type.OperateType
import com.wuyou.notification.lyric.LogUtil.log

object MusicHook : BaseHook() {
    override fun init() {
    }

    override fun onUpdate(lyricData: LyricData) {
        if (lyricData.type == OperateType.UPDATE){
            val pkgName = lyricData.extraData.packageName
            if (pkgName == context.packageName) {
                try {
                    sendNotification(lyricData.lyric,lyricData.extraData)
                } catch (e: Throwable) {
                    log(e)
                }
            }
        }
    }

    override fun onStop() {
        cancelNotification()
    }

}