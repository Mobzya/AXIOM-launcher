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

@RunWith(AndroidJUnit4::class)
class ObservatoryUiTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun prepare(preset:CosmicPreset=CosmicPreset.LIVING_SYSTEM) {
        instrumentation.targetContext.getSharedPreferences("axiom_prefs",0).edit().clear()
            .putString("cosmic_preset",preset.name)
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
        // UiAutomation uses Runtime.exec: commands have no shell expansion.
        val bytes=device.executeShellCommand("stat -c%s $directory/$name.png").trim().toLongOrNull() ?: 0L
        assertTrue("Screenshot was not saved: $name",bytes>0)
        val hierarchy=java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(hierarchy)
        hierarchy.toString("UTF-8").chunked(2000).forEachIndexed { index,chunk ->
            android.util.Log.i("AXIOM_UI","$name[$index] $chunk")
        }
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
            val search=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10_000)
            assertNotNull("Scenario search is missing",search)
            search.click()
            search.text="бездны"
            android.os.SystemClock.sleep(700)
            val automation=instrumentation.uiAutomation
            // The sheet owns a Dialog window. Activity insets cannot hide its
            // keyboard; BACK is consumed by the visible IME before the sheet.
            if(automation.windows.any {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}) {
                device.pressBack()
                android.os.SystemClock.sleep(700)
            }
            automation.serviceInfo=automation.serviceInfo
            val scene=device.wait(Until.findObject(By.descStartsWith("Сцена: У края бездны.")),10_000)
            assertNotNull("Filtered black-hole scene is missing",scene)
            scene.click()
            android.os.SystemClock.sleep(700)
            automation.serviceInfo=automation.serviceInfo
            assertTrue(visible("Космос"))
            assertTrue(visible("У края бездны"))
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
    @Test fun supermassiveSceneRendersAtLauncherScale() {
        prepare(CosmicPreset.GALACTIC_NUCLEUS);device.setOrientationNatural()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(visible("Космос"))
            assertTrue(visible("Сверхмассивное ядро"))
            screenshot("07-supermassive")
        }
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
