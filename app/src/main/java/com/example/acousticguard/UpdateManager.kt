package com.example.acousticguard

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class UpdateManager(private val context: Context) {

    private val GITHUB_API_URL = "https://api.github.com/repos/rahibladex/AcousticGuard/releases/latest"
    private val PREFS_NAME = "NariShaktiSOSUpdatePrefs"
    private val KEY_DOWNLOAD_ID = "download_id"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkForUpdates(
        forceCheck: Boolean = false,
        onUpdateAvailable: (version: String, downloadUrl: String) -> Unit,
        onNoUpdate: (version: String, downloadUrl: String) -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "TEJASHWINI-AcousticGuard-Android")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connectTimeout = 10000
                    readTimeout = 10000
                    connect()
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = json.getString("tag_name").replace("v", "").trim()
                    val currentVersion = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                    } catch (e: Exception) {
                        "1.0.0"
                    }

                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    if (downloadUrl.isEmpty()) {
                        downloadUrl = "https://github.com/rahibladex/AcousticGuard/releases/download/v$latestVersion/NariShaktiSOS.apk"
                    }

                    if (isNewerVersion(latestVersion, currentVersion) || forceCheck) {
                        mainHandler.post {
                            onUpdateAvailable(latestVersion, downloadUrl)
                        }
                    } else {
                        mainHandler.post {
                            onNoUpdate(latestVersion, downloadUrl)
                        }
                    }
                } else {
                    mainHandler.post {
                        onError("Server responded with code $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Error checking for updates", e)
                val msg = e.localizedMessage ?: e.message ?: "Connection error"
                mainHandler.post {
                    onError(msg)
                }
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun downloadAndInstall(downloadUrl: String) {
        mainHandler.post {
            try {
                // Delete previous apk if existing
                val oldFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NariShaktiSOS-Update.apk")
                if (oldFile.exists()) {
                    oldFile.delete()
                }

                val request = DownloadManager.Request(Uri.parse(downloadUrl))
                    .setTitle("TEJASHWINI Update")
                    .setDescription("Downloading latest version APK...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "NariShaktiSOS-Update.apk")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = downloadManager.enqueue(request)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()

                Toast.makeText(context, "Downloading update... (Check notifications)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("UpdateManager", "Failed to start download", e)
                Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleDownloadComplete(intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getLong(KEY_DOWNLOAD_ID, -2)

        if (downloadId == savedId || savedId == -1L) {
            mainHandler.post {
                installApk()
            }
        }
    }

    fun installApk() {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "NariShaktiSOS-Update.apk")
        if (file.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please allow 'Install unknown apps' to complete update", Toast.LENGTH_LONG).show()
                    return
                }
            }

            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("UpdateManager", "Failed to launch package installer", e)
                Toast.makeText(context, "Install error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Update file not found", Toast.LENGTH_SHORT).show()
        }
    }
}
