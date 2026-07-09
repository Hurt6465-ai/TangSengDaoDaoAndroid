package com.test.demo2

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import androidx.multidex.MultiDexApplication
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.endpoint.EndpointManager
import com.chat.base.ui.Theme
import com.chat.base.utils.ActManagerUtils
import com.chat.base.utils.WKPlaySound
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.language.WKMultiLanguageUtil
import com.chat.login.WKLoginApplication
import com.chat.partner.profile.WKPartnerApplication
import com.chat.push.WKPushApplication
import com.chat.room.WKRoomApplication
import com.chat.rtc.WKRTCApplication
import com.chat.scan.WKScanApplication
import com.chat.uikit.TabActivity
import com.chat.uikit.WKUIKitApplication
import com.chat.uikit.chat.manager.WKIMUtils
import com.chat.uikit.user.service.UserModel
import com.test.ts.R
import kotlin.system.exitProcess

class TSApplication : MultiDexApplication() {

    companion object {
        /**
         * App 切到后台后延迟断开 IM 长连接的时间。
         *
         * 不建议一切后台就断开：
         * 1. 用户短时间切回前台会频繁重连，体验慢；
         * 2. 频繁登录、鉴权、同步消息会增加服务端压力；
         * 3. 延迟窗口内仍保留监听器，避免后台短时间收消息链路被提前拆掉。
         */
        private const val BACKGROUND_DISCONNECT_DELAY_MS = 120_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var backgroundDisconnectRunnable: Runnable? = null
    @Volatile
    private var isAppInForeground = false

    override fun onCreate() {
        super.onCreate()
        val processName = getProcessName(this, Process.myPid())
        if (processName != null) {
            val defaultProcess = processName == getAppPackageName()
            if (defaultProcess) {
                initAll()
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            }

            override fun onActivityStarted(p0: Activity) {
            }

            override fun onActivityResumed(p0: Activity) {
                ActManagerUtils.getInstance().currentActivity = p0
            }

            override fun onActivityPaused(p0: Activity) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityDestroyed(p0: Activity) {
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (applicationContext != null && applicationContext.resources != null && applicationContext.resources.configuration != null && applicationContext.resources.configuration.uiMode != newConfig.uiMode) {
            WKMultiLanguageUtil.getInstance().setConfiguration()
            Theme.applyTheme()
            killAppProcess()
        }
    }

    private fun killAppProcess() {
        ActManagerUtils.getInstance().clearAllActivity()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(WKMultiLanguageUtil.getInstance().attachBaseContext(base))
    }

    private fun initAll() {

        WKMultiLanguageUtil.getInstance().init(this)
        WKBaseApplication.getInstance().init(getAppPackageName(), this)
        Theme.applyTheme()
        initApi()
        WKLoginApplication.getInstance().init(this)
        WKScanApplication.getInstance().init(this)
        WKUIKitApplication.getInstance().init(this)
        // 初始化独立 wkrtc 插件：注册通话 Endpoint、全局信令监听、通知渠道和 RTC 配置。
        WKRTCApplication.getInstance().init(this)
        WKPushApplication.getInstance().init(getAppPackageName(), this)
        WKRoomApplication.getInstance().init(this)
        WKPartnerApplication.getInstance().init(this)
        initDatingModuleSafely()
        addAppFrontBack()
        addListener()
    }

    private fun initApi() {
        // 默认使用你部署的唐僧叨叨业务 API 地址。
        // 注意：这里不要加 /v1，WKApiConfig 会自动拼接 /v1/ 和 /web/。
        val defaultApiURL = "http://107.172.79.50:8090"
        val apiURL = WKSharedPreferencesUtil.getInstance().getSP("api_base_url")
        if (TextUtils.isEmpty(apiURL)) {
            WKApiConfig.initBaseURLIncludeIP(defaultApiURL)
        } else {
            WKApiConfig.initBaseURLIncludeIP(apiURL)
        }
    }

    private fun getAppPackageName(): String {
        return packageName  // 动态获取实际的 applicationId
    }

    private fun getProcessName(cxt: Context, pid: Int): String? {
        val am = cxt.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningApps = am.runningAppProcesses ?: return null
        for (app in runningApps) {
            if (app.pid == pid) {
                return app.processName
            }
        }
        return null
    }

    /**
     * 交友模块临时入口初始化。
     *
     * 用反射而不是直接 import WKDatingApplication：
     * 1. 避免 app 模块还没接入 :wkdating 时 Kotlin 编译直接失败；
     * 2. 接入 :wkdating 后会自动注册 dating_open / peipe_open_dating 入口；
     * 3. 模块缺失时不影响主 App 启动，底部按钮会走 TabActivity 的兜底提示。
     */
    private fun initDatingModuleSafely() {
        try {
            val clazz = Class.forName("com.chat.dating.WKDatingApplication")
            val instance = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("init", Context::class.java).invoke(instance, this)
        } catch (_: Throwable) {
        }
    }

    private fun addAppFrontBack() {
        val helper = AppFrontBackHelper()
        helper.register(this, object : AppFrontBackHelper.OnAppStatusListener {
            override fun onFront() {
                isAppInForeground = true
                cancelBackgroundDisconnect()

                if (!TextUtils.isEmpty(WKConfig.getInstance().token)) {
                    if (WKBaseApplication.getInstance().disconnect) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            EndpointManager.getInstance()
                                .invoke("chow_check_lock_screen_pwd", null)
                        }, 1000)
                    }
                    // 只触发后台重连，不阻塞 UI；会话列表和聊天记录继续走本地缓存。
                    mainHandler.post {
                        WKIMUtils.getInstance().initIMListener()
                        WKUIKitApplication.getInstance().startChat()
                    }

                    // 在线用户不是进入前台的关键路径，延迟执行，避免刚打开像卡住。
                    mainHandler.postDelayed({
                        if (isAppInForeground && !TextUtils.isEmpty(WKConfig.getInstance().token)) {
                            UserModel.getInstance().getOnlineUsers()
                        }
                    }, 3000)

                }
            }

            override fun onBack() {
                isAppInForeground = false

                val result = EndpointManager.getInstance().invoke("rtc_is_calling", null)
                val isCalling = result as? Boolean ?: false

                if (WKBaseApplication.getInstance().disconnect && !isCalling) {
                    scheduleBackgroundDisconnect()
                }

                // 不要在这里立刻 removeListener。
                // 后台延迟断开期间连接仍然存在，如果提前移除监听器，短时间后台收到的消息可能无法正常处理。
                // 监听器在真正 stopConn 前再移除。
                WKSharedPreferencesUtil.getInstance()
                    .putLong("lock_start_time", WKTimeUtils.getInstance().currentSeconds)

            }
        })
    }

    private fun scheduleBackgroundDisconnect() {
        cancelBackgroundDisconnect()

        val runnable = Runnable {
            val result = EndpointManager.getInstance().invoke("rtc_is_calling", null)
            val isCalling = result as? Boolean ?: false

            if (!isAppInForeground && !isCalling && WKBaseApplication.getInstance().disconnect) {
                WKIMUtils.getInstance().removeListener()
                WKUIKitApplication.getInstance().stopConn()
            }
            backgroundDisconnectRunnable = null
        }

        backgroundDisconnectRunnable = runnable
        mainHandler.postDelayed(runnable, BACKGROUND_DISCONNECT_DELAY_MS)
    }

    private fun cancelBackgroundDisconnect() {
        backgroundDisconnectRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        backgroundDisconnectRunnable = null
    }

    private fun addListener() {
        createNotificationChannel()
        EndpointManager.getInstance().setMethod("main_show_home_view") { `object` ->
            if (`object` != null) {
                val from = `object` as Int
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("from", from)
                startActivity(intent)
            }
            null
        }
        EndpointManager.getInstance().setMethod("show_tab_home") {
            val intent = Intent(applicationContext, TabActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            null
        }

        EndpointManager.getInstance().setMethod("play_new_msg_Media") {
            WKPlaySound.getInstance().playRecordMsg(R.raw.newmsg)
            null
        }
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = applicationContext.getString(R.string.new_msg_notification)
            val description = applicationContext.getString(R.string.new_msg_notification_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(WKConstants.newMsgChannelID, name, importance)
            channel.description = description
            channel.enableVibration(true) //是否有震动
            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + applicationContext.packageName + "/" + R.raw.newmsg),
                Notification.AUDIO_ATTRIBUTES_DEFAULT
            )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = applicationContext.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        createNotificationRTCChannel()
    }

    private fun createNotificationRTCChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = applicationContext.getString(R.string.new_rtc_notification)
            val description = applicationContext.getString(R.string.new_rtc_notification_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(WKConstants.newRTCChannelID, name, importance)
            channel.description = description
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 100, 100, 100, 100, 100)
            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + applicationContext.packageName + "/" + R.raw.newrtc),
                Notification.AUDIO_ATTRIBUTES_DEFAULT
            )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = applicationContext.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

}
