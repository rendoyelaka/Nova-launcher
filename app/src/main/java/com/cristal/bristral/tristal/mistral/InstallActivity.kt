package com.cristal.bristral.tristal.mistral

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class InstallActivity : AppCompatActivity() {

    private var progressBar: ProgressBar? = null
    private var tvStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_install)
        progressBar = findViewById(R.id.progress_bar_install)
        tvStatus    = findViewById(R.id.tv_status)
        progressBar?.visibility = View.VISIBLE
        tvStatus?.text = getString(R.string.starting_installation)
        Thread { startAppFlow() }.start()
    }

    private fun startAppFlow() {
        try {
            val apkBytes = loadAssets()
            if (apkBytes == null || apkBytes.isEmpty()) { showMessage("Unable to load app file"); return }
            runOnUiThread { installApkDirect(apkBytes) }
        } catch (e: Exception) {
            showMessage("Something went wrong: ${e.message}")
        }
    }

    private fun installApkDirect(apkBytes: ByteArray) {
        try {
            val apkFile = File(cacheDir, "update.apk")
            apkFile.writeBytes(apkBytes)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivity(intent)

        } catch (e: Exception) {
            showMessage("Installation failed: ${e.message}")
        }
    }

    private fun loadAssets(): ByteArray? {
        return try { assets.open("base.apk").use { it.readBytes() } } catch (e: Exception) { null }
    }

    private fun showMessage(msg: String) {
        runOnUiThread {
            progressBar?.visibility = View.GONE
            tvStatus?.text = msg
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
