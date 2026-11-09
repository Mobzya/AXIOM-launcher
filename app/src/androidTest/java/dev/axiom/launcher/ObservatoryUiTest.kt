package dev.axiom.launcher

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ObservatoryUiTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun prepare() {
        instrumentation.targetContext.getSharedPreferences("axiom_prefs",0).edit().clear()
            .putString("cosmic_preset",CosmicPreset.LIVING_SYSTEM.name)
            .putString("life_mode",LifeMode.SEEDED.name)
            .putBoolean("auto_events",false).putBoolean("rare_events",false)
            .putBoolean("wall_mode",true).putBoolean("clock_wall",true).commit()
    }
    private fun visible(text:String) = device.wait(Until.hasObject(By.text(text)),20_000)
    private fun click(text:String) {
        assertTrue("Missing UI: $text",visible(text))
        device.findObject(By.text(text)).click()
    }
    private fun screenshot(name:String) {
        val folder=instrumentation.targetContext.getExternalFilesDir("screenshots")!!
        folder.mkdirs()
        assertTrue(device.takeScreenshot(File(folder,"$name.png")))
    }
    @Test fun homeLifeScienceCreationAndBlackHoleSceneAreAccessible() {
        prepare();device.setOrientationNatural()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(visible("Космос"))
            assertTrue(device.wait(Until.hasObject(By.descStartsWith("Время ")),10_000))
            screenshot("01-home-living")
            click("Космос")
            assertTrue(visible("Жизнь"))
            device.findObject(By.desc("Пауза"))?.click()
            click("Жизнь")
            assertTrue(visible("Жизнь среди звёзд"))
            assertTrue(visible("КОЛОНИИ"))
            screenshot("02-civilizations")
            click("Наука")
            assertTrue(visible("От движения к пониманию"))
            screenshot("03-science")
            click("Объекты")
            assertTrue(visible("Добавь новый мир"))
            screenshot("04-objects")
            click("Сцены")
            for(i in 0 until 12) {
                if(device.hasObject(By.text("У края бездны")))break
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.75f)
            }
            click("У края бездны")
            assertTrue(visible("Космос"))
            screenshot("05-black-hole")
        }
    }
    @Test fun compactLandscapeKeepsLauncherNavigationUsable() {
        prepare();device.setOrientationLeft()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                assertTrue(visible("Приложения"));assertTrue(visible("Космос"))
                screenshot("06-landscape")
            }
        } finally {device.setOrientationNatural()}
    }
    @Test fun collisionFieldUsesTheActualRenderedClockInk() {
        val raster=rasterClock("08:08",108f,-4f)
        val w=raster.bitmap.width;val h=raster.bitmap.height
        val field=GlyphDistanceField.fromAlpha(w,h,raster.alpha)
        var solid=0;var empty=0
        for(y in 0 until h step 2)for(x in 0 until w step 2) {
            val alpha=raster.alpha[y*w+x].toInt() and 255
            if(alpha>=128){assertTrue(field.distance(x.toFloat(),y.toFloat())<0);solid++}
            if(alpha==0){assertTrue(field.distance(x.toFloat(),y.toFloat())>0);empty++}
        }
        assertTrue(solid>50);assertTrue(empty>solid)
    }
}
