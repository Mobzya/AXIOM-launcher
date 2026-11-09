package dev.axiom.launcher

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
        android.os.SystemClock.sleep(700)
        val directory="/sdcard/Download/axiom-ui"
        device.executeShellCommand("mkdir -p $directory")
        device.executeShellCommand("screencap -p $directory/$name.png")
        assertEquals("saved",device.executeShellCommand("test -s $directory/$name.png && echo saved").trim())
        val hierarchy=File(instrumentation.targetContext.cacheDir,"$name.xml")
        device.dumpWindowHierarchy(hierarchy)
        device.executeShellCommand("cp '${hierarchy.absolutePath}' '$directory/$name.xml'")
    }
    @Test fun homeLifeScienceCreationAndBlackHoleSceneAreAccessible() {
        prepare();device.setOrientationNatural()
        ActivityScenario.launch(MainActivity::class.java).use {
          try {
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
            assertTrue(visible("Начальные условия"))
            for(i in 0 until 14) {
                val target=device.findObject(By.text("У края бездны"))
                if(target!=null && !target.visibleBounds.isEmpty)break
                // Use the same upward finger gesture as a person. Modal sheet
                // accessibility nodes need not expose an Android ScrollView.
                device.swipe(device.displayWidth/2,(device.displayHeight*.86).toInt(),
                    device.displayWidth/2,(device.displayHeight*.48).toInt(),35)
                android.os.SystemClock.sleep(250)
            }
            click("У края бездны")
            assertTrue(visible("Космос"))
            screenshot("05-black-hole")
          } catch(failure:Throwable) {
            runCatching {screenshot("99-navigation-failure")}
            throw failure
          }
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
