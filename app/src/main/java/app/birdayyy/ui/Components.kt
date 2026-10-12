@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.birdayyy.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.birdayyy.data.Person
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/** The design's expressive shapes (scalloped cookie, flower, clover, sunny) drawn as polygons. */
class PolyShape(private val n: Int, private val amp: Float, private val flower: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val path = Path()
        val steps = 120
        for (i in 0 until steps) {
            val t = i.toDouble() / steps * 2 * Math.PI
            val r = if (flower) 0.5 * ((1 - amp) + amp * abs(Math.cos(n * t / 2)))
            else 0.5 * ((1 - amp) + amp * (1 + Math.cos(n * t)) / 2)
            val x = (size.width * (0.5 + r * Math.cos(t - Math.PI / 2))).toFloat()
            val y = (size.height * (0.5 + r * Math.sin(t - Math.PI / 2))).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

object DesignShapes {
    val Cookie: Shape = PolyShape(9, 0.12f, false)
    val Flower: Shape = PolyShape(6, 0.30f, true)
    val Clover: Shape = PolyShape(4, 0.34f, true)
    val Sunny: Shape = PolyShape(8, 0.18f, false)
    val Squircle: Shape = RoundedCornerShape(34)
    val Arch: Shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 18, bottomStartPercent = 18)
    val all = listOf(Cookie, Flower, Clover, Arch, Sunny)
}

fun shapeFor(key: String): Shape = DesignShapes.all[abs(key.hashCode()) % DesignShapes.all.size]
fun colorIndexFor(key: String): Int = abs((key + "x").hashCode()) % 4
fun tileIndexFor(key: String): Int = abs((key + "t").hashCode()) % 5

private val photoCache = LruCache<String, ImageBitmap>(60)

private fun loadPhoto(ctx: Context, photo: String): ImageBitmap? {
    photoCache.get(photo)?.let { return it }
    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = runCatching {
        if (photo.startsWith("content://")) ctx.contentResolver.openInputStream(Uri.parse(photo))?.use { BitmapFactory.decodeStream(it, null, opts) }
        else BitmapFactory.decodeFile(File(photo).absolutePath, opts)
    }.getOrNull()?.asImageBitmap()
    if (bmp != null) photoCache.put(photo, bmp)
    return bmp
}

@Composable
fun PersonAvatar(person: Person, size: Dp, modifier: Modifier = Modifier, shape: Shape? = null, gray: Boolean = false) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, person.photo) {
        value = person.photo?.let { withContext(Dispatchers.IO) { loadPhoto(ctx, it) } }
    }
    val s = shape ?: remember(person.id) { shapeFor(person.id) }
    Box(modifier.size(size).clip(s).background(if (gray) (if (isDark()) Color(0xFF8A8785) else Color(0xFFBDB9B6)) else pal.avatar(colorIndexFor(person.id))), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            colorFilter = if (gray) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null)
        else Text(person.initials, color = if (gray) Color(0xFF2A1A18) else pal.onAvatar, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.34f).sp)
    }
}

private class Piece(val kind: Int, val color: Int, val x: Float, val y: Float, val size: Float, val rot: Float)

/** Soft, blurred confetti used as the background of the whole app. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val pieces = remember {
        val r = Random(11)
        List(40) { Piece(r.nextInt(4), r.nextInt(4), r.nextFloat(), r.nextFloat(), 8f + r.nextFloat() * 14f, r.nextFloat() * 360f) }
    }
    Canvas(modifier.fillMaxSize().blur(4.dp)) {
        pieces.forEach { p ->
            val c = pal.confetti[p.color % pal.confetti.size].copy(alpha = .26f)
            val cx = p.x * size.width
            val cy = p.y * size.height
            val s = p.size.dp.toPx()
            rotate(p.rot, Offset(cx, cy)) {
                when (p.kind) {
                    0 -> drawCircle(c, s / 2, Offset(cx, cy))
                    1 -> drawRoundRect(c, Offset(cx - s, cy - s / 3), Size(s * 2, s * 0.66f), androidx.compose.ui.geometry.CornerRadius(s / 4))
                    2 -> drawPath(Path().apply {
                        moveTo(cx, cy - s * .7f); lineTo(cx + s * .7f, cy + s * .6f); lineTo(cx - s * .7f, cy + s * .6f); close()
                    }, c)
                    else -> drawCircle(c, s / 2, Offset(cx, cy), style = Stroke(width = s / 4))
                }
            }
        }
    }
}

@Composable
fun Modifier.glass(state: HazeState, shape: Shape = RoundedCornerShape(0.dp)): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return this.clip(shape).hazeEffect(state) {
        blurRadius = 24.dp
        backgroundColor = surface
        tints = listOf(HazeTint(surface.copy(alpha = 0.55f)))
        noiseFactor = 0f
    }
}
