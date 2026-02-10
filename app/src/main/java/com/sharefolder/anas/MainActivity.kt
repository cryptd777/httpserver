package com.sharefolder.anas

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    val canOpen = effectiveUri != null && activity != null

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
                        Button(
                            onClick = {
                                if (!canOpen) return@Button
                                effectiveUri?.let { uriString ->
                                    val result = openFolder(activity, uriString)
                                    if (result == OpenResult.PERMISSION_EXPIRED) {
                                        transientUri = null
                                        scope.launch {
                                            context.dataStore.edit { prefs ->
                                                prefs.remove(SelectedUriKey)
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = canOpen,
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
                                text = context.getString(R.string.open_folder),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class OpenResult {
    OPENED,
    NO_HANDLER,
    PERMISSION_EXPIRED
}

private fun openFolder(activity: Activity?, uriString: String): OpenResult {
    if (activity == null) return OpenResult.NO_HANDLER
    val treeUri = Uri.parse(uriString)
    val docUri = try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    } catch (_: IllegalArgumentException) {
        Toast.makeText(
            activity,
            activity.getString(R.string.invalid_folder),
            Toast.LENGTH_LONG
        ).show()
        return OpenResult.PERMISSION_EXPIRED
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }
    try {
        activity.startActivity(intent)
        return OpenResult.OPENED
    } catch (ex: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(fallback)
            return OpenResult.NO_HANDLER
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                activity.getString(R.string.no_file_manager),
                Toast.LENGTH_LONG
            ).show()
            return OpenResult.NO_HANDLER
        }
    } catch (_: SecurityException) {
        Toast.makeText(
            activity,
            activity.getString(R.string.permission_expired),
            Toast.LENGTH_LONG
        ).show()
        return OpenResult.PERMISSION_EXPIRED
    }
}
