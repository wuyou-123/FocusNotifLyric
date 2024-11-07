package com.wuyou.notification.lyric

import com.github.kyuubiran.ezxhelper.EzXHelper
import com.wuyou.notification.lyric.LogUtil.log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage


class MainHook : IXposedHookZygoteInit, IXposedHookLoadPackage {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXHelper.initHandleLoadPackage(lpparam)
        when (lpparam.packageName) {
            "com.android.systemui" -> initHooks(SystemUi)
            "com.miui.player" -> initHooks(MusicHook)
            "com.netease.cloudmusic" -> initHooks(MusicHook)
            "com.salt.music" -> initHooks(MusicHook)
            "com.tencent.qqmusic" -> initHooks(MusicHook)
            "com.kugou.android" -> initHooks(MusicHook)
            "com.kugou.android.lite" -> initHooks(MusicHook)
            "cn.kuwo.player" -> initHooks(MusicHook)
            "remix.myplayer" -> initHooks(MusicHook)
            "cmccwm.mobilemusic" -> initHooks(MusicHook)
            "com.meizu.media.music" -> initHooks(MusicHook)
            "com.r.rplayer" -> initHooks(MusicHook)
            "cn.toside.music.mobile" -> initHooks(MusicHook)
            "com.apple.android.music" -> initHooks(MusicHook)
            "com.luna.music" -> initHooks(MusicHook)
            "com.kyant.vanilla" -> initHooks(MusicHook)
            "player.phonograph.plus" -> initHooks(MusicHook)
            "com.xuncorp.suvine.music" -> initHooks(MusicHook)
            "com.caij.puremusic" -> initHooks(MusicHook)
            "com.xuncorp.qinalt.music" -> initHooks(MusicHook)
            "statusbar.finder" -> initHooks(MusicHook)
            "com.hihonor.cloudmusic" -> initHooks(MusicHook)
            "org.akanework.gramophone" -> initHooks(MusicHook)
            "com.netease.cloudmusic.lite" -> initHooks(MusicHook)
            "cn.wenyu.bodian" -> initHooks(MusicHook)
            "fun.upup.musicfree" -> initHooks(MusicHook)
            "com.mimicry.mymusic" -> initHooks(MusicHook)
            "yos.music.player" -> initHooks(MusicHook)
            "org.kde.kdeconnect_tp" -> initHooks(MusicHook)
            "com.huawei.music" -> initHooks(MusicHook)
        }
    }

    private fun initHooks(vararg hook: BaseHook) {
        hook.forEach {
            try {
                if (it.isInit) return@forEach
                it.init()
                it.isInit = true
                log("Inited hook: ${it.javaClass.name}")
            } catch (e: Exception) {
                log(e)
            }
        }
    }
}