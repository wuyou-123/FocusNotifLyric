package com.wuyou.notification.lyric

import android.service.notification.StatusBarNotification
import android.widget.TextView
import cn.lyric.getter.api.data.LyricData
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.ConstructorFinder.`-Static`.constructorFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedHelpers

object SystemUi : BaseHook() {
    private val focusTextViewList = mutableListOf<TextView>()
    private var speed = -0.1f
    override fun init() {
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

        // 拦截初始化状态栏焦点通知文本布局
        var unhook: Unhook? = null
        loadClass("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment").methodFinder()
            .first { name == "onCreateView" }.createHook {
                before {
                    unhook =
                        loadClass("com.android.systemui.statusbar.widget.FocusedTextView").constructorFinder()
                            .first { parameterCount == 3 }.createHook {
                                after {
                                    focusTextViewList += it.thisObject as TextView
                                }
                            }
                }
                after {
                    unhook?.unhook()
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

    override fun onUpdate(lyricData: LyricData) {
        focusTextViewList.forEach {
            XposedHelpers.callMethod(it, "setMarqueeRepeatLimit", 1)
            XposedHelpers.callMethod(it, "startMarqueeLocal")
            val m = XposedHelpers.getObjectField(it, "mMarquee")
            if (speed == -0.1f) {
                speed = XposedHelpers.getFloatField(m, "mPixelsPerMs") * 1.5f
                XposedHelpers.setFloatField(m, "mPixelsPerMs", speed)
            }
        }
    }

    override fun onStop() {
    }
}