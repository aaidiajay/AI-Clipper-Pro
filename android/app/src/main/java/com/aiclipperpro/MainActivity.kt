
package com.aiclipperpro

import android.os.Bundle
import android.net.Uri
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException

private const val BASE_URL = "https://ai-clipper-pro-api.onrender.com"
private val http = OkHttpClient()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ClipperScreen()
            }
        }
    }
}

@Composable
fun ClipperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember {
        mutableStateOf("Select a video or paste a YouTube/Twitch link.")
    }
    var busy by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        videoUri = uri
        if (uri != null) {
            status = "Video selected successfully. Ready for AI analysis."
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "AI CLIPPER PRO",
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Find standout gaming moments with AI")

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("YouTube / Twitch link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    status = "Checking video link..."

                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            try {
                                val body = JSONObject()
                                    .put("source_url", url)
                                    .toString()
                                    .toRequestBody(
                                        "application/json".toMediaType()
                                    )

                                val request = Request.Builder()
                                    .url("$BASE_URL/analyze-link")
                                    .post(body)
                                    .build()

                                http.newCall(request).execute().use {
                                    val result = it.body?.string() ?: ""
                                    if (it.isSuccessful) result
                                    else "HTTP ${it.code}: $result"
                                }
                            } catch (e: Exception) {
                                "Error: ${e.message}"
                            }
                        }
                        busy = false
                    }
                }
            ) {
                Text("Validate link")
            }

            HorizontalDivider()

            Text(
                "UPLOAD YOUR VIDEO",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    videoPicker.launch("video/*")
                }
            ) {
                Text("Choose Video from Gallery")
            }

            if (videoUri != null) {
                Text("Video selected: ${getFileName(context, videoUri!!)}")
            }

            Button(
                enabled = !busy && videoUri != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val selectedUri = videoUri!!

                    busy = true
                    status = "Uploading video and analyzing with AI..."

                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            try {
                                val mimeType =
                                    context.contentResolver.getType(selectedUri)
                                        ?: "video/mp4"

                                val videoBody = object : RequestBody() {
                                    override fun contentType() =
                                        mimeType.toMediaTypeOrNull()

                                    override fun writeTo(sink: BufferedSink) {
                                        val stream =
                                            context.contentResolver
                                                .openInputStream(selectedUri)
                                                ?: throw IOException(
                                                    "Cannot open selected video."
                                                )

                                        stream.use {
                                            sink.writeAll(it.source())
                                        }
                                    }
                                }

                                val filename =
                                    getFileName(context, selectedUri)

                                val multipart = MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                        "file",
                                        filename,
                                        videoBody
                                    )
                                    .build()

                                val request = Request.Builder()
                                    .url("$BASE_URL/analyze-video")
                                    .post(multipart)
                                    .build()

                                http.newCall(request).execute().use {
                                    val result = it.body?.string() ?: ""

                                    if (it.isSuccessful) {
                                        "AI ANALYSIS RESULT:\n$result"
                                    } else {
                                        "Server error ${it.code}:\n$result"
                                    }
                                }
                            } catch (e: Exception) {
                                "Upload error: ${e.message}"
                            }
                        }

                        busy = false
                    }
                }
            ) {
                Text(if (busy) "Please wait..." else "Analyze Video with AI")
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "RESULT / STATUS",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(status)
                }
            }

            Text(
                "Note: Upload only videos you own or are authorized to process. AI suggestions are approximate. Actual MP4 clip generation is not included yet.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "video.mp4"

    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (index >= 0) {
                name = cursor.getString(index) ?: name
            }
        }
    }

    return name
}
