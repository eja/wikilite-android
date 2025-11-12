package it.eja.wikilite

import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.zip.GZIPInputStream

class DatabaseDownloadActivity : AppCompatActivity() {

    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: DatabaseFileAdapter
    private val databaseFiles = mutableListOf<String>()
    private lateinit var preferences: SharedPreferences
    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_download)

        preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setupUI()
        loadDatabaseFiles()
    }

    private fun setupUI() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = DatabaseFileAdapter(databaseFiles) { filePath ->
            showDownloadOptions(filePath)
        }
        recyclerView.adapter = adapter

        val btnRefresh: Button = findViewById(R.id.btnRefresh)
        btnRefresh.setOnClickListener { loadDatabaseFiles() }
    }

    private fun loadDatabaseFiles() {
        CoroutineScope(Dispatchers.Main).launch {
            progressDialog = ProgressDialog(this@DatabaseDownloadActivity).apply {
                setMessage("Loading database files...")
                setCancelable(false)
                show()
            }

            val files = withContext(Dispatchers.IO) {
                loadFilesFromHuggingFace()
            }

            progressDialog?.dismiss()
            databaseFiles.clear()
            databaseFiles.addAll(files)
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadFilesFromHuggingFace(): List<String> {
        val files = mutableListOf<String>()
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://huggingface.co/api/datasets/eja/wikilite")
                .build()

            val response = client.newCall(request).execute()
            val jsonResponse = response.body?.string()

            if (jsonResponse != null) {
                val jsonObject = JSONObject(jsonResponse)
                val siblings = jsonObject.getJSONArray("siblings")

                for (i in 0 until siblings.length()) {
                    val item = siblings.getJSONObject(i)
                    val rfilename = item.getString("rfilename")

                    if (rfilename.endsWith(".db.gz")) {
                        files.add(rfilename)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    private fun showDownloadOptions(filePath: String) {
        val dialog = DownloadDialog(filePath) { selectedFilePath, downloadPath ->
            startDownload(selectedFilePath, downloadPath)
        }
        dialog.show(supportFragmentManager, "download_dialog")
    }

    private fun startDownload(filePath: String, downloadPath: String) {
        DownloadAndExtractTask().execute(filePath to downloadPath)
    }

    private inner class DownloadAndExtractTask : AsyncTask<Pair<String, String>, Int, Boolean>() {
        private lateinit var currentFilePath: String
        private lateinit var finalDbPath: String
        private lateinit var downloadPath: String

        override fun onPreExecute() {
            super.onPreExecute()
            progressDialog = ProgressDialog(this@DatabaseDownloadActivity).apply {
                setMessage("Downloading...")
                setCancelable(false)
                show()
            }
        }

        override fun doInBackground(vararg params: Pair<String, String>): Boolean {
            if (params.isEmpty()) return false

            currentFilePath = params[0].first
            downloadPath = params[0].second

            return try {
                println("Starting download: $currentFilePath")

                // Use the direct download URL
                val url = URL("https://huggingface.co/datasets/eja/wikilite/resolve/main/$currentFilePath")
                println("Download URL: $url")

                val connection = url.openConnection()
                connection.connect()

                val inputStream = connection.getInputStream()
                val fileName = currentFilePath.substringAfterLast("/")
                val downloadFilePath = "$downloadPath/$fileName"
                val downloadFile = File(downloadFilePath)
                val outputStream = FileOutputStream(downloadFile)

                // Download the file
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }

                outputStream.close()
                inputStream.close()

                println("Downloaded $totalBytes bytes to: $downloadFilePath")

                // Extract the .gz file
                val extractedPath = downloadFilePath.replace(".gz", "")
                println("Extracting to: $extractedPath")

                extractGzipFile(downloadFilePath, extractedPath)

                // Delete the .gz file
                downloadFile.delete()
                println("Deleted gz file")

                finalDbPath = extractedPath
                println("Final DB path: $finalDbPath")
                true

            } catch (e: Exception) {
                println("Download error: ${e.message}")
                e.printStackTrace()
                false
            }
        }

        @Throws(IOException::class)
        private fun extractGzipFile(gzipFile: String, outputFile: String) {
            FileInputStream(gzipFile).use { fis ->
                GZIPInputStream(fis).use { gis ->
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (gis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        }

        override fun onPostExecute(success: Boolean) {
            progressDialog?.dismiss()

            if (success) {
                preferences.edit().putString("db_path", finalDbPath).apply()
                Toast.makeText(this@DatabaseDownloadActivity, "Download successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@DatabaseDownloadActivity, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this@DatabaseDownloadActivity, "Download failed!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
