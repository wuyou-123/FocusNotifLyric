package com.wuyou.notification.lyric

import android.service.notification.StatusBarNotification
import android.view.Choreographer
import android.view.View
import android.widget.TextView
import cn.lyric.getter.api.data.LyricData
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.ConstructorFinder.`-Static`.constructorFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.wuyou.notification.lyric.LogUtil.log
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedHelpers

object SystemUi : BaseHook() {
    private val focusTextViewList = mutableListOf<TextView>()
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
        // 重设mLastAnimationTime,取消闪烁动画(让代码以为刚播放过动画,所以这次不播放)
        loadClass("com.android.systemui.statusbar.phone.FocusedNotifPromptView").methodFinder()
            .first { name == "setData" }.createHook {
                before {
                    XposedHelpers.setLongField(
                        it.thisObject,
                        "mLastAnimationTime",
                        System.currentTimeMillis()
                    )
                }
            }

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

    private const val MARQUEE_DELAY = 1200L
    private var speed = -0.1f
    private const val SPEED_INCREASE = 1.8f

    private val runnablePool = mutableMapOf<Int, Runnable>()
    override fun onUpdate(lyricData: LyricData) {
        val lyric = lyricData.lyric
        focusTextViewList.forEach {
            it.text = lyric
            if (XposedHelpers.getAdditionalStaticField(it, "is_scrolling") == 1) {
                val m0 = XposedHelpers.getObjectField(it, "mMarquee")
                if (m0 != null) {
                    // 设置速度并且调用停止函数,重置歌词位置
                    XposedHelpers.setFloatField(m0, "mPixelsPerMs", 0f)
                    XposedHelpers.callMethod(m0, "stop")
                }
            }
            val startScroll = runnablePool.getOrPut(it.hashCode()) {
                Runnable { startScroll(it) }
            }
            it.handler?.removeCallbacks(startScroll)
            it.postDelayed(startScroll, MARQUEE_DELAY)
        }
    }

    private fun startScroll(textView: TextView) {
        try {
            // 开始滚动
            XposedHelpers.callMethod(textView, "setMarqueeRepeatLimit", 1)
            XposedHelpers.callMethod(textView, "startMarqueeLocal")

            val m = XposedHelpers.getObjectField(textView, "mMarquee") ?: return
            if (speed == -0.1f) {
                // 初始化滚动速度
                speed = XposedHelpers.getFloatField(m, "mPixelsPerMs") * SPEED_INCREASE
            }
            val width =
                (textView.width - textView.getCompoundPaddingLeft() - textView.getCompoundPaddingRight()).toFloat()
            val lineWidth = textView.layout.getLineWidth(0)
            // 重设最大滚动宽度,只能滚动到文本结束
            XposedHelpers.setFloatField(m, "mMaxScroll", lineWidth - width)
            // 重设速度
            XposedHelpers.setFloatField(m, "mPixelsPerMs", speed)
            // 移除回调,防止滚动结束之后重置滚动位置
            XposedHelpers.setObjectField(m, "mRestartCallback", Choreographer.FrameCallback {})
            XposedHelpers.setAdditionalStaticField(textView, "is_scrolling", 1)
        } catch (e: Throwable) {
            log(e)
        }
    }

    override fun onStop() {
        focusTextViewList.forEach {
            it.visibility = View.GONE
        }
    }
}