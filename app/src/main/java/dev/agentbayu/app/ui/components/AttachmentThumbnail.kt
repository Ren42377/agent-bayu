package dev.agentbayu.app.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.ui.theme.GlassBadgeShape
import dev.agentbayu.app.ui.theme.glassSurface

fun interface AttachmentLoader {
    suspend fun thumbnail(id: String, edge: Int): ImageBitmap?
}

val LocalAttachmentLoader = staticCompositionLocalOf<AttachmentLoader?> { null }

class AttachmentThumbnails(private val loader: suspend (String, Int) -> Bitmap?) :
    AttachmentLoader {

    private val cache = LruCache<String, ImageBitmap>(CACHE_ENTRIES)

    override suspend fun thumbnail(id: String, edge: Int): ImageBitmap? {
        val key = id + "@" + edge
        cache.get(key)?.let { return it }
        val decoded = loader(id, edge)?.asImageBitmap() ?: return null
        cache.put(key, decoded)
        return decoded
    }

    private companion object {
        const val CACHE_ENTRIES = 24
    }
}

@Composable
fun AttachmentThumbnail(
    attachment: MessageAttachment,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_THUMBNAIL_SIZE,
    shape: Shape = GlassBadgeShape
) {
    val loader = LocalAttachmentLoader.current
    var bitmap by remember(attachment.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(attachment.id, loader) {
        bitmap = loader?.thumbnail(attachment.id, THUMBNAIL_EDGE)
    }
    Box(
        modifier = modifier
            .size(size)
            .glassSurface(shape = shape)
            .clip(shape)
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.chat_attachment),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}

private val DEFAULT_THUMBNAIL_SIZE = 64.dp
private const val THUMBNAIL_EDGE = 256
