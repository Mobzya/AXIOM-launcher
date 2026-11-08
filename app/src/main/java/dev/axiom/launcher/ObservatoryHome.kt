package dev.axiom.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ObservatoryClock(modifier:Modifier=Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val context=LocalContext.current
    LaunchedEffect(Unit) { while(true) { now=LocalDateTime.now(); delay(15_000) } }
    val format=if(DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern(format)),color=AX_WHITE,
            fontSize=54.sp,fontWeight=FontWeight.ExtraLight,letterSpacing=(-2).sp)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.getDefault()))
            .replaceFirstChar {it.titlecase(Locale.getDefault())},
            color=AX_GRAY,fontSize=13.sp,letterSpacing=.4.sp)
    }
}

@Composable
internal fun HomeNavigation(modifier:Modifier=Modifier,onApps:()->Unit,
    onCosmos:()->Unit,onTerminal:()->Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
        .background(AX_GLASS).border(1.dp,AX_BORDER,RoundedCornerShape(23.dp))
        .padding(5.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf(Triple("⌘","Приложения",onApps),Triple("✧","Космос",onCosmos),
            Triple(">_","Консоль",onTerminal)).forEach { (symbol,label,action) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                .clickable(role=Role.Button,onClick=action).padding(vertical=10.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(symbol,color=AX_ACCENT,fontSize=19.sp,fontFamily=FontFamily.Monospace)
                Text(label,color=AX_GRAY,fontSize=10.sp)
            }
        }
    }
}
