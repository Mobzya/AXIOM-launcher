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

@Composable
internal fun LivingHomeBadge(world:CosmicWorld,modifier:Modifier=Modifier) {
    val counts by remember(world) {derivedStateOf {
        Triple(world.civilization.colonies.size,world.civilization.ships.size,world.civilizationConfig.mode)
    }}
    if(counts.third!=LifeMode.OFF && (counts.first>0 || counts.second>0))
        Text("Колонии ${counts.first}  ·  корабли ${counts.second}",
            modifier=modifier,color=AX_ACCENT.copy(alpha=.8f),fontSize=10.sp,letterSpacing=.4.sp)
}
