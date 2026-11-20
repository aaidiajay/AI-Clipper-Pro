package com.aiclipperpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val BASE_URL = "https://YOUR-BACKEND-HOST"
private val http = OkHttpClient()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ClipperScreen() } }
    }
}

@Composable
fun ClipperScreen() {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Paste a video link, or upload an authorized video in the next version.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("AI CLIPPER PRO", style = MaterialTheme.typography.headlineMedium)
            Text("Find standout moments • Generate clip ideas • Prepare Shorts metadata")
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("YouTube / Twitch link") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                enabled = !busy && url.isNotBlank(),
                onClick = {
                    busy = true
                    status = "Sending link for validation…"
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            try {
                                val body = JSONObject().put("source_url", url).toString()
                                    .toRequestBody("application/json".toMediaType())
                                val req = Request.Builder().url("$BASE_URL/analyze-link").post(body).build()
                                http.newCall(req).execute().use { it.body?.string() ?: "Empty response" }
                            } catch (e: Exception) { "Error: ${e.message}" }
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Working…" else "Analyze link") }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Result / status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(status)
                }
            }
            Text("Note: links are not downloaded automatically. Use footage you own or are authorized to process.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
