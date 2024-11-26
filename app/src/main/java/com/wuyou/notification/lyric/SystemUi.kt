package com.wuyou.notification.lyric

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.service.notification.StatusBarNotification
import android.view.Choreographer
import android.view.View
import android.widget.TextView
import cn.lyric.getter.api.data.LyricData
import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createAfterHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createBeforeHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.ConstructorFinder.`-Static`.constructorFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.wuyou.notification.lyric.LogUtil.log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("StaticFieldLeak")
object SystemUi : BaseHook() {
    private val focusTextViewList = mutableListOf<TextView>()

    private var mStatusBarLeftContainer: View? = null
    private var mFocusedNotLine: View? = null
    private var mClockSeat: View? = null
    private var mBigTime: TextView? = null

    // ui上正在显示焦点通知(可能是其他应用的通知)
    private val isShowingFocused = MutableStateFlow(false)

    // 有歌词的通知(但是歌词可能没有显示在ui上,比如在音乐app中)
    private val isLyric = MutableStateFlow(false)

    // 正在显示焦点通知歌词(有歌词并且正在ui上显示)
    private var isShowingFocusedLyric: Boolean = false

    private fun updateLayout() {
        if (isShowingFocused.value && isLyric.value && !showCLock) {
            isShowingFocusedLyric = true
            // 如果在显示歌词,就隐藏时钟,占位布局和竖线
            mStatusBarLeftContainer?.visibility = View.INVISIBLE
            mClockSeat?.visibility = View.GONE
            mFocusedNotLine?.visibility = View.GONE
            // 设置大时钟颜色
            mBigTime?.setTextColor(Color.WHITE)
        } else {
            mStatusBarLeftContainer?.visibility = View.VISIBLE
            mClockSeat?.visibility = View.VISIBLE
            mFocusedNotLine?.visibility = View.VISIBLE
            isShowingFocusedLyric = false
        }

    }

    private var showCLock = false

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun init() {

        loadClass("android.app.Application").methodFinder().first { name == "onCreate" }
            .createHook {
                after {
                    val mFilter = IntentFilter()
                    mFilter.addAction("$CHANNEL_ID.actions.switchClockStatus")
                    context.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            showCLock = !showCLock
                            updateLayout()
                        }
                    }, mFilter)
                }
            }
        loadClass("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView").methodFinder()
            .filterByName("onFinishInflate").first().createAfterHook {
                // 通知栏左边部分(包含时间和通知图标)
                mStatusBarLeftContainer =
                    it.thisObject.getObjectField("mStatusBarLeftContainer") as View
            }
        loadClass("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment").methodFinder()
            .filterByName("onViewCreated").first().createAfterHook {
                // 焦点通知左边竖线
                mFocusedNotLine = it.thisObject.getObjectField("mFocusedNotLine") as View
                // 焦点通知左边占位布局
                mClockSeat = it.thisObject.getObjectField("mClockSeat") as View
            }

        loadClass("com.android.systemui.qs.MiuiNotificationHeaderView").methodFinder()
            .filterByName("onFinishInflate").first().createAfterHook {
                // 大时钟布局
                mBigTime = it.thisObject.getObjectField("mBigTime") as TextView
            }
        loadClass("com.android.systemui.qs.MiuiNotificationHeaderView").methodFinder()
            .filterByName("updateBigTimeColor").first().replaceMethod {
                if (isShowingFocusedLyric) {
                    // 显示歌词的时候取消设置大时钟颜色(假时钟动画会设置颜色,显示歌词的时候取消了假时钟动画,所以可能会下拉通知栏之后时间是黑色)
                    null
                } else {
                    it.invokeOriginalMethod()
                }
            }
        var unhook0: XC_MethodHook.Unhook? = null
        loadClass("com.android.systemui.controlcenter.shade.NotificationHeaderExpandController\$notificationCallback\$1").methodFinder()
            .filterByName("onExpansionChanged").first().createHook {
                before {
                    unhook0 = loadClass("com.miui.utils.configs.MiuiConfigs").methodFinder()
                        .filterByName("isVerticalMode").first().replaceMethod {
                            if (isShowingFocusedLyric) {
                                // 如果在显示歌词,就伪装成横屏,用来取消假时钟动画
                                false
                            } else {
                                it.invokeOriginalMethod()
                            }
                        }
                }
                after {
                    if (isShowingFocusedLyric) {
                        // 在显示歌词的时候固定通知栏顶部时间和日期的位置和缩放
                        val notificationHeaderExpandController =
                            it.thisObject.getObjectField("this\$0")
                        val combinedHeaderController =
                            notificationHeaderExpandController!!.getObjectField("headerController")!!
                                .callMethod("get")
                        val notificationBigTime =
                            combinedHeaderController!!.getObjectFieldAs<TextView>("notificationBigTime")
                        notificationBigTime.translationX = 0f
                        notificationBigTime.translationY = 0f
                        notificationBigTime.scaleX = 1f
                        notificationBigTime.scaleY = 1f
                        notificationBigTime.setTextColor(Color.WHITE)
                        val notificationDateTime =
                            combinedHeaderController.getObjectFieldAs<TextView>("notificationDateTime")
                        notificationDateTime.translationX = 0f
                        notificationDateTime.translationY = 0f
                        // 设置时钟的宽度
                        notificationHeaderExpandController.callMethod("updateWeight", 1.0f)
                        // 设置通知图标位置
                        combinedHeaderController.getObjectField("notificationShortcut")
                            ?.callMethod("setTranslationY", 0f)

                    }
                    unhook0?.unhook()
                }
            }
        loadClass("com.android.systemui.controlcenter.shade.NotificationHeaderExpandController\$notificationCallback\$1").methodFinder()
            .filterByName("onAppearanceChanged").first().createHook {
                before {
                }
                after {
                    if (isShowingFocusedLyric) {
                        // 显示歌词的时候手动调用动画,防止大时钟突然出现
                        val notificationHeaderExpandController =
                            it.thisObject.getObjectField("this\$0")
                        val combinedHeaderController =
                            notificationHeaderExpandController!!.getObjectField("headerController")!!
                                .callMethod("get")
                        loadClass("com.android.systemui.controlcenter.shade.NotificationHeaderExpandController")
                            .callStaticMethod(
                                "access\$startFolmeAnimationAlpha",
                                notificationHeaderExpandController,
                                combinedHeaderController!!.getObjectField("notificationBigTime"),
                                combinedHeaderController.getObjectField("notificationBigTimeFolme"),
                                if (!(it.args[0] as Boolean)) 0f else 1f,
                                true
                            )
                    }
                }
            }

        loadClass("com.android.systemui.statusbar.phone.FocusedNotifPromptController").methodFinder()
            .filterByName("notifyNotifBeanChanged").first().createHook {
                before {
                    // 焦点通知更新的事件,通过这个判断当前展示的焦点通知是不是歌词
                    val sbn = it.args[0]?.getObjectField("sbn") as StatusBarNotification?
                    isLyric.value = sbn?.notification?.channelId == CHANNEL_ID
                    updateLayout()
                }
            }

        loadClass("com.android.systemui.recents.OverviewProxyService").methodFinder()
            .filterByName("onFocusedNotifUpdate").first()
            .createBeforeHook { m ->
                // 代码中的动画目标位置
                val rect = m.args[2] as Rect

                /**
                 * 代码中的20是边距(可能在不同型号设备中会有不同的值,具体要去抓布局的资源文件,在小米15pro中是20)
                 * @return 获取焦点通知正常情况下往右偏移的距离
                 */
                fun getWidth(): Int {
                    return (mClockSeat?.width?.plus(mFocusedNotLine?.width ?: 0) ?: 0) + 20
                }

                /**
                 * 动画的目标位置,只有两种情况,一种是隐藏时间之后的位置,一种是正常位置
                 * 如果目标位置左边减去时间那一块的宽度大于0,则说明在右边
                 * @return 1: 当前动画目标位置在左边(隐藏时间之后的位置) 2:当前动画目标位置在右边(正常情况下的位置)
                 */
                fun getPos() = if (rect.left - getWidth() > 0) 2 else 1

                if (isLyric.value) {
                    // 如果减去左边的位置大于0,就说明当前位置是时间右边,因为现在是在显示歌词,所以把目标位置往左偏移
                    if (getPos() == 2) {
                        rect.left -= getWidth()
                        rect.right -= getWidth()
                    }
                } else {
                    // 如果现在显示的不是歌词,并且隐藏了时钟,那么往右偏移到正常位置
                    // 当音乐在后台播放并且前台应用有焦点通知的时候会触发这种情况
                    if (mClockSeat?.visibility == 8) {
                        rect.left += getWidth()
                        rect.right += getWidth()
                    } else {
                        // 如果已经展示了时间,但是目标位置还是在左边,往右偏移
                        if (getPos() == 1) {
                            rect.left += getWidth()
                            rect.right += getWidth()
                        }
                    }
                }
            }
        loadClass("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment").methodFinder()
            .filterByName("updateStatusBarVisibilities").first().createAfterHook {
                // 获取是否在显示焦点通知
                // 更新一次isShowingFocused
                isShowingFocused.value =
                    it.thisObject.getBooleanField("mIsFocusedNotifyViewShowing")
                updateLayout()
            }

        // 拦截构建通知的函数
        loadClass("com.android.systemui.statusbar.notification.row.NotifBindPipeline").methodFinder()
            .filterByName("requestPipelineRun").first().createBeforeHook {
                val statusBarNotification =
                    it.args[0].getObjectFieldOrNullAs<StatusBarNotification>("mSbn")
                if (statusBarNotification!!.notification.channelId == CHANNEL_ID) {
                    it.result = null
                }
            }

        // 拦截初始化状态栏焦点通知文本布局
        var unhook: XC_MethodHook.Unhook? = null
        loadClass("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment").methodFinder()
            .filterByName("onCreateView")
            .first().createHook {
                before {
                    unhook =
                        loadClass("com.android.systemui.statusbar.widget.FocusedTextView").constructorFinder()
                            .filterByParamCount(3)
                            .first().createAfterHook {
                                focusTextViewList += it.thisObject as TextView
                            }
                }
                after {
                    unhook?.unhook()
                }
            }

        // 构建通知栏通知函数
        // loadClass("com.android.systemui.statusbar.notification.row.NotificationContentInflaterInjector").methodFinder()
        //     .filterByName("createRemoteViews").first())
        // 重设 mLastAnimationTime，取消闪烁动画(让代码以为刚播放过动画，所以这次不播放)
        loadClass("com.android.systemui.statusbar.phone.FocusedNotifPromptView").methodFinder()
            .filterByName("setData")
            .first().createBeforeHook {
                it.thisObject.setLongField("mLastAnimationTime", System.currentTimeMillis())
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
    private var lastLyric = ""
    override fun onUpdate(lyricData: LyricData) {
        val lyric = lyricData.lyric
        focusTextViewList.forEach {
            if (lastLyric == it.text) {
                it.text = lyric
                lastLyric = lyric
            }
            if (XposedHelpers.getAdditionalStaticField(it, "is_scrolling") == 1) {
                val m0 = it.getObjectFieldOrNull("mMarquee")
                if (m0 != null) {
                    // 设置速度并且调用停止函数,重置歌词位置
                    m0.setFloatField("mPixelsPerMs", 0f)
                    m0.callMethod("stop")
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
            textView.callMethod("setMarqueeRepeatLimit", 1)
            textView.callMethod("startMarqueeLocal")

            val m = textView.getObjectFieldOrNull("mMarquee") ?: return
            if (speed == -0.1f) {
                // 初始化滚动速度
                speed = m.getFloatField("mPixelsPerMs") * SPEED_INCREASE
            }
            val width =
                (textView.width - textView.compoundPaddingLeft - textView.compoundPaddingRight).toFloat()
            val lineWidth = textView.layout?.getLineWidth(0)
            if (lineWidth != null) {
                // 重设最大滚动宽度,只能滚动到文本结束
                m.setFloatField("mMaxScroll", lineWidth - width)
                // 重设速度
                m.setFloatField("mPixelsPerMs", speed)
                // 移除回调,防止滚动结束之后重置滚动位置
                m.setObjectField("mRestartCallback", Choreographer.FrameCallback {})
                XposedHelpers.setAdditionalStaticField(textView, "is_scrolling", 1)
            }
        } catch (e: Throwable) {
            log(e)
        }
    }

    override fun onStop() {
    }
}