package eu.kigenc.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kigenc.app.app

/** Kleine Leiste „Nr. 1 der Charts“ mit Stopp und „Ganzen Song öffnen“, solange die Hörprobe läuft. */
@Composable
fun NowPlayingBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val music = context.app.music
    val track by music.track.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = track != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val t = track ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nr. 1 · ${t.genre} · Hörprobe via Deezer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "${t.title} – ${t.artist}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    music.stop()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.link)))
                    } catch (_: ActivityNotFoundException) {
                    }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Ganzen Song öffnen", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                IconButton(onClick = { music.stop() }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Musik stoppen", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}
