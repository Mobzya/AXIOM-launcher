package dev.axiom.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UniverseControlPanel(
    grainCount: Int, maxGrains: Int,
    onSpawn: (MatterMaterial, Float) -> Boolean,
    onPerturb: (Float) -> Unit,
    onEvent: (CosmicEventIntensity) -> Unit,
    onTime: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var material by remember { mutableStateOf(MatterMaterial.ROCK) }
    var mass by remember { mutableFloatStateOf(1.5f) }
    var impulse by remember { mutableFloatStateOf(.10f) }
    var message by remember { mutableStateOf("") }
    val options = listOf(
        MatterMaterial.ROCK to "Планета",
        MatterMaterial.IRON to "Железо",
        MatterMaterial.ICE to "Ледяная",
        MatterMaterial.METHANE to "Газовый мир",
        MatterMaterial.HYDROGEN to "Звезда",
        MatterMaterial.PLASMA to "Квазар",
        MatterMaterial.MAGMA to "Магма",
        MatterMaterial.ORGANICS to "Органика"
    )
    Column(
        Modifier.fillMaxWidth().heightIn(max = 600.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Brush.verticalGradient(listOf(
                AX_PANEL.copy(alpha=.99f), AX_SURFACE.copy(alpha=.99f))))
            .border(1.dp,AX_BORDER,RoundedCornerShape(25.dp))
            .verticalScroll(rememberScrollState())
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            Column {
                Text("AXIOM / ОБСЕРВАТОРИЯ",color=AX_WHITE,
                    fontFamily=FontFamily.Monospace,fontSize=13.sp,
                    fontWeight=FontWeight.Medium)
                Text(grainCount.toString()+" / "+maxGrains+" песчинок",
                    color=AX_GRAY,fontSize=10.sp)
            }
            Text("×",color=AX_WHITE,fontSize=28.sp,
                modifier=Modifier.clickable(onClick=onDismiss)
                    .padding(horizontal=12.dp,vertical=4.dp))
        }
        Text("СОЗДАТЬ ФИЗИЧЕСКИЙ ОБЪЕКТ",
            color=AX_GRAY,fontFamily=FontFamily.Monospace,fontSize=10.sp)
        options.chunked(4).forEach { group ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                group.forEach { (type,label) ->
                    Box(Modifier.weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if(type==material) AX_TILE else AX_GLASS)
                        .border(1.dp,if(type==material) Color(0xff75c8e8)
                            else AX_BORDER,RoundedCornerShape(10.dp))
                        .clickable { material=type }
                        .padding(vertical=13.dp,horizontal=2.dp),
                        contentAlignment=Alignment.Center) {
                        Text(label,color=AX_WHITE,fontSize=10.sp,
                            textAlign=TextAlign.Center,maxLines=2)
                    }
                }
            }
        }
        Text("Масса · "+String.format(java.util.Locale.US,"%.2f",mass),
            color=AX_WHITE,fontSize=12.sp)
        Slider(value=mass,onValueChange={ mass=it },valueRange=.06f..18f)
        ObservatoryAction("＋  Создать объект") {
            message=if(onSpawn(material,mass))
                "Новый объект создан из реальных частиц"
            else "Не хватает места для новых песчинок"
        }
        Text("ВОЗДЕЙСТВИЕ НА МАТЕРИЮ",
            color=AX_GRAY,fontFamily=FontFamily.Monospace,fontSize=10.sp)
        Text("Сила импульса · "+String.format(java.util.Locale.US,"%.2f",impulse),
            color=AX_WHITE,fontSize=12.sp)
        Slider(value=impulse,onValueChange={ impulse=it },
            valueRange=.025f.. .28f)
        ObservatoryAction("↗  Приложить импульс в центре") {
            onPerturb(impulse)
            message="Импульс передан настоящим песчинкам"
        }
        Text("МОЩНОСТЬ КОСМИЧЕСКОГО СОБЫТИЯ",
            color=AX_GRAY,fontFamily=FontFamily.Monospace,fontSize=10.sp)
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            CosmicEventIntensity.entries.forEach { power ->
                Box(Modifier.weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AX_TILE)
                    .clickable {
                        onEvent(power)
                        message="Запущено: "+power.label
                    }
                    .padding(vertical=12.dp,horizontal=1.dp),
                    contentAlignment=Alignment.Center) {
                    Text(power.label,color=AX_WHITE,fontSize=10.sp,
                        textAlign=TextAlign.Center)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ObservatoryAction("Ⅱ  Пауза"){onTime(0f)}
            }
            Box(Modifier.weight(1f)) {
                ObservatoryAction("▶  Играть"){onTime(.5f)}
            }
        }
        if(message.isNotEmpty())Text(message,color=AX_GRAY,fontSize=11.sp)
    }
}

@Composable
private fun ObservatoryAction(title:String,onClick:()->Unit){
    Box(Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Brush.horizontalGradient(listOf(
            Color(0xff477b93).copy(alpha=.42f),
            AX_TILE,
            Color(0xff70538d).copy(alpha=.34f))))
        .border(1.dp,AX_BORDER,RoundedCornerShape(12.dp))
        .clickable(onClick=onClick).padding(vertical=12.dp),
        contentAlignment=Alignment.Center) {
        Text(title,color=AX_WHITE,fontSize=12.sp,fontWeight=FontWeight.Medium)
    }
}
