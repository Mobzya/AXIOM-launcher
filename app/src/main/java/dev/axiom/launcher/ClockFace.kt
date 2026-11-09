package dev.axiom.launcher

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

internal data class ClockRaster(val bitmap:Bitmap,val alpha:ByteArray)
internal fun rasterClock(text:String,size:Float,spacing:Float):ClockRaster {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color=android.graphics.Color.WHITE;textSize=size
        typeface=Typeface.create("sans-serif-thin",Typeface.NORMAL)
    }
    val pad=4
    val widths=text.map {paint.measureText(it.toString())}
    val width=ceil(widths.sum()+spacing*(text.length-1)).toInt()+pad*2
    val fm=paint.fontMetrics
    val height=ceil(fm.descent-fm.ascent).toInt()+pad*2
    val bitmap=Bitmap.createBitmap(width.coerceAtLeast(1),height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
    val canvas=NativeCanvas(bitmap);var x=pad.toFloat()
    text.forEachIndexed {index,char ->
        canvas.drawText(char.toString(),x,pad-fm.ascent,paint);x+=widths[index]+spacing
    }
    val pixels=IntArray(width*height);bitmap.getPixels(pixels,0,width,0,0,width,height)
    return ClockRaster(bitmap,ByteArray(pixels.size) {(pixels[it] ushr 24).toByte()})
}

/** One raster is both the visible clock and the source of its physical contour. */
@Composable
internal fun ObservatoryClock(world:CosmicWorld,modifier:Modifier=Modifier) {
    var now by remember {mutableStateOf(LocalDateTime.now())}
    val context=LocalContext.current;val density=LocalDensity.current
    val format=if(DateFormat.is24HourFormat(context))"HH:mm" else "h:mm"
    val text=now.format(DateTimeFormatter.ofPattern(format))
    LaunchedEffect(Unit) {
        while(true) {
            now=LocalDateTime.now()
            val millis=System.currentTimeMillis()
            delay((60_000-millis%60_000).coerceIn(100,60_000))
        }
    }
    val textPixels=with(density){54.sp.toPx()};val spacing=with(density){(-2).sp.toPx()}
    val raster=remember(text,textPixels,spacing){rasterClock(text,textPixels,spacing)}
    val bitmap=remember(raster){raster.bitmap.asImageBitmap()}
    val field by produceState<GlyphDistanceField?>(null,raster) {
        value=null
        value=withContext(Dispatchers.Default){GlyphDistanceField.fromAlpha(raster.bitmap.width,raster.bitmap.height,raster.alpha)}
    }
    var left by remember {mutableFloatStateOf(0f)};var top by remember {mutableFloatStateOf(0f)}
    var positioned by remember {mutableStateOf(false)}
    LaunchedEffect(field,left,top,positioned) {
        if(positioned)world.updateClock(field,left,top)
    }
    DisposableEffect(world){onDispose {world.updateClock(null)}}
    val appearance=remember {Animatable(1f)}
    LaunchedEffect(text,world.reducedMotion) {
        if(!world.reducedMotion){appearance.snapTo(.72f);appearance.animateTo(1f,tween(320))}
        else appearance.snapTo(1f)
    }
    val ink=AX_WHITE
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.size(with(density){raster.bitmap.width.toDp()},with(density){raster.bitmap.height.toDp()})
            .semantics {contentDescription="Время $text"}
            .onGloballyPositioned {coordinates ->
                val p=coordinates.positionInRoot();left=p.x;top=p.y;positioned=true
            }) {
            drawImage(bitmap,alpha=appearance.value,colorFilter=ColorFilter.tint(ink))
        }
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.getDefault()))
            .replaceFirstChar {it.titlecase(Locale.getDefault())},
            color=AX_GRAY,fontSize=13.sp,letterSpacing=.4.sp)
    }
}
