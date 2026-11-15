package com.example.ui.formeit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.formeit.Remote
import java.io.File

/** Card shell used across the app (same shape/elevation as the original Assenze Collegio screens). */
@Composable
fun AcCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    elevation: Int = 2,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, icon: ImageVector? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (icon != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Renders loading / error / empty states around a remote value. */
@Composable
fun <T> RemoteContent(
    state: Remote<T>,
    onRetry: () -> Unit,
    isEmpty: (T) -> Boolean = { (it as? Collection<*>)?.isEmpty() == true || it == null },
    emptyText: String = "Nessun elemento.",
    content: @Composable (T) -> Unit
) {
    when (state) {
        Remote.Idle, Remote.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is Remote.Failed -> AcCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(state.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Riprova") }
        }
        is Remote.Ready -> if (isEmpty(state.value)) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(emptyText, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else content(state.value)
    }
}

val ListSpacing = Arrangement.spacedBy(12.dp)

/** Opens a downloaded file with an external viewer through the app FileProvider. */
fun openWithViewer(context: Context, file: File) {
    val ext = file.extension.lowercase()
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nessuna app disponibile per aprire ${file.name}", Toast.LENGTH_LONG).show()
    }
}

/** Strips HTML tags/entities that communications and notifications may contain. */
fun plainText(raw: String): String =
    androidx.core.text.HtmlCompat.fromHtml(raw, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
