package com.sharefolder.anas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sharefolder.anas.ui.theme.FolderLauncherTheme
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

private val Context.dataStore by preferencesDataStore(name = "folder_prefs")
private val SelectedUriKey = stringPreferencesKey("selected_uri")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FolderLauncherTheme {
                FolderLauncherScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderLauncherScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val selectedUri by context.dataStore.data
        .map { prefs -> prefs[SelectedUriKey] }
        .collectAsState(initial = null)
    var transientUri by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveUri = transientUri ?: selectedUri
    val folderName = remember(effectiveUri) {
        effectiveUri?.let { uriString ->
            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriString))
                doc?.name
            } catch (_: Exception) {
                null
            }
        }
    }
    var serverRunning by rememberSaveable { mutableStateOf(false) }
    var authEnabled by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var server by remember { mutableStateOf<FolderHttpServer?>(null) }
    val port = 8080
    val ipAddress = getDeviceIp(context)
    val serverUrl = if (ipAddress != null) "http://$ipAddress:$port" else "Connect to Wi-Fi"
    DisposableEffect(Unit) {
        onDispose {
            server?.stopServer()
        }
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && activity != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            var persistable = true
            try {
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: SecurityException) {
                // Ignore if persistable permission is not granted on this device.
                persistable = false
            }
            transientUri = uri.toString()
            if (persistable) {
                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[SelectedUriKey] = uri.toString()
                    }
                }
            } else {
                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs.remove(SelectedUriKey)
                    }
                }
            }
            if (!persistable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.permission_notice),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val gradient = Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        0.5f to MaterialTheme.colorScheme.surface,
        1f to MaterialTheme.colorScheme.background
    )
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSecondary = MaterialTheme.colorScheme.onSecondary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                    titleContentColor = onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = surface
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.headline),
                            style = MaterialTheme.typography.titleLarge,
                            color = onSurface
                        )
                        Text(
                            text = context.getString(R.string.subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { launcher.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primary,
                                contentColor = onPrimary
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderShared,
                                contentDescription = null
                            )
                            Text(
                                modifier = Modifier.padding(start = 10.dp),
                                text = context.getString(R.string.pick_folder),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = surfaceVariant
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = context.getString(R.string.folder_selected),
                            style = MaterialTheme.typography.labelLarge,
                            color = onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = folderName ?: (effectiveUri ?: context.getString(R.string.no_folder)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (effectiveUri != null && folderName != null) {
                            Text(
                                text = effectiveUri ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurface.copy(alpha = 0.65f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = context.getString(R.string.auth_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurface
                            )
                            Switch(
                                checked = authEnabled,
                                onCheckedChange = { authEnabled = it }
                            )
                        }
                        if (authEnabled) {
                            TextField(
                                value = password,
                                onValueChange = { password = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(text = context.getString(R.string.auth_password)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = surface,
                                    unfocusedContainerColor = surface
                                )
                            )
                            Text(
                                text = context.getString(R.string.auth_user_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = context.getString(R.string.server_url),
                            style = MaterialTheme.typography.labelLarge,
                            color = onSurface.copy(alpha = 0.8f)
                        )
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = {
                                if (!serverRunning) {
                                    if (effectiveUri == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.no_folder),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                    if (authEnabled && password.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.password_required),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                    val root = DocumentFile.fromTreeUri(
                                        context,
                                        Uri.parse(effectiveUri)
                                    )
                                    if (root == null || !root.isDirectory) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.invalid_folder),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                    try {
                                        val newServer = FolderHttpServer(
                                            context = context,
                                            root = root,
                                            port = port,
                                            authEnabled = authEnabled,
                                            password = password
                                        )
                                        newServer.start(
                                            NanoHTTPD.SOCKET_READ_TIMEOUT,
                                            false
                                        )
                                        server = newServer
                                        serverRunning = true
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.server_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    server?.stopServer()
                                    server = null
                                    serverRunning = false
                                }
                            },
                            enabled = effectiveUri != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = secondary,
                                contentColor = onSecondary
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null
                            )
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                text = if (serverRunning) {
                                    context.getString(R.string.server_stop)
                                } else {
                                    context.getString(R.string.server_start)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getDeviceIp(context: Context): String? {
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val ipInt = wifi?.connectionInfo?.ipAddress ?: 0
    if (ipInt == 0) return null
    return Formatter.formatIpAddress(ipInt)
}

private class FolderHttpServer(
    private val context: Context,
    private val root: DocumentFile,
    port: Int,
    private val authEnabled: Boolean,
    private val password: String
) : NanoHTTPD(port) {

    fun stopServer() {
        stop()
    }

    override fun serve(session: IHTTPSession): Response {
        if (authEnabled && !isAuthorized(session)) {
            val res = newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "text/plain",
                "Unauthorized"
            )
            res.addHeader("WWW-Authenticate", "Basic realm=\"Folder Share\"")
            return res
        }

        return when {
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            session.uri == "/file" -> handleFile(session)
            else -> handleIndex(session)
        }
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: return false
        if (!header.startsWith("Basic ")) return false
        val encoded = header.removePrefix("Basic ").trim()
        val decoded = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) return false
        return parts[1] == password
    }

    private fun handleIndex(session: IHTTPSession): Response {
        val relPath = session.parameters["path"]?.firstOrNull() ?: ""
        val current = resolvePath(root, relPath) ?: root
        val listing = current.listFiles()
            .sortedWith(compareBy({ !it.isDirectory }, { it.name ?: "" }))

        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"/>")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>")
        sb.append("<title>Folder Share</title>")
        sb.append(
            "<style>" +
                ":root{color-scheme:light dark;}" +
                "body{margin:0;font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;" +
                "background:linear-gradient(160deg,#0f172a,#111827 45%,#020617);color:#e2e8f0;min-height:100vh;}" +
                ".wrap{max-width:900px;margin:0 auto;padding:32px 20px 48px;}" +
                ".card{background:rgba(15,23,42,0.9);border:1px solid rgba(148,163,184,0.25);" +
                "border-radius:16px;padding:20px 20px;margin-bottom:16px;box-shadow:0 10px 30px rgba(0,0,0,0.25);}" +
                "h1{margin:0 0 6px;font-size:28px;letter-spacing:0.3px;}" +
                ".muted{color:#94a3b8;font-size:14px;}" +
                ".path{font-weight:600;color:#7dd3fc;}" +
                ".toolbar{display:flex;gap:12px;flex-wrap:wrap;align-items:center;}" +
                "input[type=file]{color:#e2e8f0;}" +
                "button{background:#38bdf8;border:none;color:#0b1020;padding:10px 16px;border-radius:10px;" +
                "font-weight:600;cursor:pointer;}" +
                "button:hover{filter:brightness(1.05);}" +
                ".list{list-style:none;padding:0;margin:0;}" +
                ".item{display:flex;justify-content:space-between;align-items:center;padding:10px 12px;" +
                "border-radius:10px;margin-bottom:8px;background:rgba(2,6,23,0.6);}" +
                ".item a{text-decoration:none;color:#e2e8f0;}" +
                ".item a:hover{color:#38bdf8;}" +
                ".badge{font-size:12px;color:#0b1020;background:#a7f3d0;padding:3px 8px;border-radius:999px;}" +
                ".dir{background:#fcd34d;color:#3f2d00;}" +
                ".footer{margin-top:14px;color:#94a3b8;font-size:12px;}" +
            "</style>"
        )
        sb.append("</head><body><div class=\"wrap\">")
        sb.append("<div class=\"card\">")
        sb.append("<h1>Folder Share</h1>")
        sb.append("<div class=\"muted\">Path: <span class=\"path\">/")
            .append(escapeHtml(relPath)).append("</span></div>")
        sb.append("</div>")
        sb.append("<div class=\"card\">")
        sb.append("<form method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">")
        sb.append("<input type=\"hidden\" name=\"path\" value=\"")
            .append(escapeHtml(relPath)).append("\"/>")
        sb.append("<div class=\"toolbar\">")
        sb.append("<input type=\"file\" name=\"file\"/>")
        sb.append("<button type=\"submit\">Upload</button>")
        sb.append("</div>")
        sb.append("</form></div>")
        sb.append("<div class=\"card\">")
        sb.append("<ul class=\"list\">")
        if (relPath.isNotEmpty()) {
            val parentPath = relPath.substringBeforeLast("/", "")
            sb.append("<li class=\"item\"><a href=\"/?path=").append(urlEncode(parentPath))
                .append("\">..</a><span class=\"badge dir\">UP</span></li>")
        }
        for (file in listing) {
            val name = file.name ?: continue
            val childPath = if (relPath.isEmpty()) name else "$relPath/$name"
            if (file.isDirectory) {
                sb.append("<li class=\"item\"><a href=\"/?path=").append(urlEncode(childPath))
                    .append("\">").append(escapeHtml(name)).append("</a><span class=\"badge dir\">DIR</span></li>")
            } else {
                sb.append("<li class=\"item\"><a href=\"/file?path=").append(urlEncode(childPath))
                    .append("\">").append(escapeHtml(name)).append("</a><span class=\"badge\">FILE</span></li>")
            }
        }
        sb.append("</ul>")
        sb.append("<div class=\"footer\">Shared over local Wi‑Fi. Uploads are allowed, deletions are disabled. ")
        sb.append("Made with love by <a href=\"https://github.com/CRYPTD777\" target=\"_blank\" rel=\"noopener noreferrer\">CRYPTD777</a>.</div>")
        sb.append("</div></div></body></html>")
        return newFixedLengthResponse(Response.Status.OK, "text/html", sb.toString())
    }

    private fun handleFile(session: IHTTPSession): Response {
        val relPath = session.parameters["path"]?.firstOrNull() ?: return notFound()
        val target = resolvePath(root, relPath) ?: return notFound()
        if (target.isDirectory) return notFound()
        val input = context.contentResolver.openInputStream(target.uri) ?: return notFound()
        val mime = target.type ?: "application/octet-stream"
        return newChunkedResponse(Response.Status.OK, mime, input)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        val params = session.parameters
        val relPath = params["path"]?.firstOrNull() ?: ""
        val current = resolvePath(root, relPath) ?: root
        try {
            session.parseBody(files)
        } catch (_: Exception) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Upload failed")
        }
        val tempPath = files["file"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "text/plain",
            "No file"
        )
        val filename = params["file"]?.firstOrNull() ?: "upload.bin"
        val target = current.createFile("application/octet-stream", filename)
            ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Create failed")
        val input = FileInputStream(File(tempPath))
        val output = context.contentResolver.openOutputStream(target.uri, "w")
            ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Write failed")
        input.use { inp ->
            output.use { out ->
                inp.copyTo(out)
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Uploaded")
    }

    private fun resolvePath(root: DocumentFile, relPath: String): DocumentFile? {
        var current: DocumentFile? = root
        val normalized = relPath.trim('/').trim()
        if (normalized.isEmpty()) return current
        val parts = normalized.split("/")
        for (part in parts) {
            val decoded = URLDecoder.decode(part, "UTF-8")
            current = current?.listFiles()?.firstOrNull { it.name == decoded }
            if (current == null) return null
        }
        return current
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun escapeHtml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun notFound(): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }
}
