package com.wuyou.notification.lyric

import android.service.notification.StatusBarNotification
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XposedHelpers

object SystemUi : BaseHook() {

    override fun init() {
//        // 禁止折叠通知
//        loadClass("com.miui.systemui.notification.MiuiBaseNotifUtil").methodFinder()
//            .first { name == "shouldSuppressFold" }.createHook {
//                returnConstant(true)
//            }

        // 拦截构建通知的函数
        loadClass("com.android.systemui.statusbar.notification.row.NotifBindPipeline").methodFinder()
            .first { name == "requestPipelineRun" }.createHook {
                before {
                    val statusBarNotification =
                        XposedHelpers.getObjectField(it.args[0], "mSbn")
                                as StatusBarNotification
                    if (statusBarNotification.notification.channelId == CHANNEL_ID) {
                        it.result = null
                    }
                }
            }

        // 构建通知栏通知函数
//        loadClass("com.android.systemui.statusbar.notification.row.NotificationContentInflaterInjector").methodFinder()
//            .first { name == "createRemoteViews" }

        // 拿到插件的classloader
        loadClass("com.android.systemui.shared.plugins.PluginInstance").methodFinder()
            .first { name == "loadPlugin" }.createHook {
                after { p0 ->
                    val mPlugin = XposedHelpers.getObjectField(p0.thisObject, "mPlugin")
                    val pluginClassLoader = mPlugin::class.java.classLoader
                    try {
                        val cl = loadClass(
                            "miui.systemui.notification.FocusNotificationPluginImpl",
                            pluginClassLoader
                        )
                        // 过滤 系统界面组件
                        if (cl.isInstance(mPlugin)) {
                            loadClass(
                                "miui.systemui.notification.NotificationSettingsManager",
                                pluginClassLoader
                            ).methodFinder().first { name == "canShowFocus" }.createHook {
                                // 允许全部应用发送焦点通知
                                returnConstant(true)
                            }
                        }

                    } catch (e: Exception) {
                        return@after
                    }
                    // 启用debug日志
//                    setStaticObject(
//                        loadClass(
//                            "miui.systemui.notification.NotificationUtil",
//                            pluginClassLoader
//                        ), "DEBUG", true
//                    )

                }
            }
    }
}