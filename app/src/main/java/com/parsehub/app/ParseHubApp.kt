package com.parsehub.app

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * 全局 Application — 崩溃保护底线
 *
 * 捕获未处理异常后自动重启 MainActivity,而非直接闪退退出。
 * 用户会看到 app "闪一下"回到首页,而不是完全退出。
 *
 * 崩溃日志记录到 Logcat(tag=ParseHub-Crash),便于 adb logcat 诊断。
 */
class ParseHubApp : Application() {

    companion object {
        private const val TAG = "ParseHub-Crash"
    }

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "未捕获异常 [${thread.name}]", throwable)

            // 重启 MainActivity,避免直接退出
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                startActivity(intent)
            } catch (_: Exception) {
            }

            // 终止当前进程,让 app 干净重启
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }
}
