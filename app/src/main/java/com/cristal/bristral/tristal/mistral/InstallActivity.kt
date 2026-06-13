package com.cristal.bristral.tristal.mistral

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.InputStream

class InstallActivity : AppCompatActivity() {

    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)
        progressBar = findViewById(R.id.progress_bar_install)
        tvStatus = findViewById(R.id.tv_status)
        progressBar?.visibility = View.VISIBLE
        tvStatus?.text = getString(R.string.starting_installation)
        Thread { beginSetup() }.start()
    }

    private fun beginSetup() {
        try {
            val apkStream = fetchPackageStream() ?: run {
                showMessage("Unable to load package")
                return
            }
            runOnUiThread { launchSessionSetup(apkStream) }
        } catch (e: Exception) {
            showMessage("Setup error: ${e.message}")
        }
    }

    private fun launchSessionSetup(apkStream: InputStream) {
        try {
            val pkgInstaller = packageManager.packageInstaller

            val sessionConfig = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            }

            val activeSessionId = pkgInstaller.createSession(sessionConfig)
            val activeSession = pkgInstaller.openSession(activeSessionId)

            activeSession.use { session ->
                session.openWrite("package.apk", 0, -1).use { output ->
                    apkStream.copyTo(output)
                    session.fsync(output)
                }

                val callbackIntent = Intent(this, InstallReceiver::class.java)
                val pendingCallback = PendingIntent.getBroadcast(
                    this,
                    activeSessionId,
                    callbackIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                session.commit(pendingCallback.intentSender)
            }

        } catch (e: Exception) {
            showMessage("Session setup failed: ${e.message}")
        }
    }

    private fun fetchPackageStream(): InputStream? {
        return try {
            assets.open("companion.apk")
        } catch (e: Exception) {
            null
        }
    }

    private fun showMessage(msg: String) {
        runOnUiThread {
            progressBar?.visibility = View.GONE
            tvStatus?.text = msg
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
