package com.qingluan.schedule

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.qingluan.schedule.data.AppSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*

import com.qingluan.schedule.ui.QingluanTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderingThemeTest {
    @get:Rule val ui = createComposeRule()

    @Test
    fun backdropLensAndTransparentSplashRender() {
        ui.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(240.dp, 100.dp).testTag("optics-test")) {
                Row(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    repeat(12) { i ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (i % 2 == 0) Color(0xFF1B9B85) else Color(0xFFB5C8FF)))
                    }
                }
                Box(Modifier.padding(12.dp).fillMaxSize().drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(27.dp) },
                    effects = { vibrancy(); blur(8.dp.toPx()); lens(22.dp.toPx(), 24.dp.toPx(), chromaticAberration = true) },
                ))
            }
        }
        val image = ui.onNodeWithTag("optics-test").captureToImage().asAndroidBitmap()
        java.io.File(System.getProperty("qingluan.screenshots"), "liquid-lens-native.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = context.getDrawable(com.qingluan.schedule.R.drawable.splash_mark)!!
        val pixels = android.graphics.Bitmap.createBitmap(108, 108, android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 108, 108)
        icon.draw(android.graphics.Canvas(pixels))
        org.junit.Assert.assertEquals(0, android.graphics.Color.alpha(pixels.getPixel(0, 0)))
        org.junit.Assert.assertTrue(android.graphics.Color.alpha(pixels.getPixel(30, 70)) > 0)
    }

    @Test
    fun transparentDarkScreensInheritReadableForeground() {
        var foreground = Color.Unspecified
        var background = Color.Unspecified
        ui.setContent {
            QingluanTheme(AppSettings(theme = "dark")) {
                Surface(color = Color.Transparent) {
                    foreground = LocalContentColor.current
                    background = MaterialTheme.colorScheme.surface
                    Text("课程名称")
                }
            }
        }
        ui.runOnIdle {
            val ratio = (foreground.luminance() + .05f) / (background.luminance() + .05f)
            assertTrue("深色透明页面对比度不足：$ratio", ratio >= 7f)
        }
    }

    @Test
    fun palettesAndCourseColorsHaveReadableContrastAndOldSettingsStillDecode() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance()
            val y = b.luminance()
            return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
        }
        for (palette in com.qingluan.schedule.ui.AppPalette.entries) {
            for (dark in listOf(false, true)) {
                val scheme = com.qingluan.schedule.ui.appColorScheme(palette.key, dark)
                assertTrue(
                    "${palette.key} / $dark foreground",
                    contrast(scheme.onSurface, scheme.surface) >= 7f,
                )
                assertTrue(
                    "${palette.key} primary",
                    contrast(scheme.primary, scheme.surfaceContainerLow) >= 4.5f,
                )
                assertTrue(
                    "${palette.key} container",
                    contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 7f,
                )
                for (base in
                    com.qingluan.schedule.model.courseColors + listOf(0xFF000000, 0xFFFFFFFF)) {
                    for (inactive in listOf(false, true)) {
                        val ink = com.qingluan.schedule.ui.courseInk(Color(base), dark, inactive)
                        val backgrounds = listOf(scheme.surface, if(dark) Color.Black else scheme.surface)
                        if (inactive) assertTrue(ink.fill.alpha in .39f.. .41f) else assertTrue(ink.fill.alpha == 1f)
                        for (background in backgrounds) {
                            val actual = ink.fill.compositeOver(background)
                            assertTrue("course title $base $dark $inactive",contrast(ink.text,actual) >= if(inactive || base in com.qingluan.schedule.model.courseColors) 4.5f else 7f)
                            assertTrue("course detail $base $dark $inactive",contrast(ink.detail,actual) >= 4.5f)
                            if(inactive) {
                                val active = com.qingluan.schedule.ui.courseInk(Color(base),dark)
                                if(dark) assertTrue(actual.luminance() < active.fill.luminance())
                                assertTrue("noncurrent contrast quieter",contrast(ink.text,actual) < contrast(active.text,active.fill))
                            }
                        }

                    }
                }
            }
            val settings = AppSettings(palette = palette.key, theme = "dark")
            val json =
                com.qingluan.schedule.data.BackupCodec.encode(
                    com.qingluan.schedule.data.Backup(settings = settings)
                )
            org.junit.Assert.assertEquals(
                settings,
                com.qingluan.schedule.data.BackupCodec.decode(json).settings,
            )
        }
        val old =
            com.qingluan.schedule.data.BackupCodec.json.decodeFromString<AppSettings>(
                """{"theme":"dark","dynamicColor":true}"""
            )
        org.junit.Assert.assertEquals("jade", old.palette)
        assertTrue(old.dynamicColor)
    }
    @Test
    fun selectionCardsHaveUniformMaterialWithoutGlassBevel() {
        ui.setContent {
            QingluanTheme(AppSettings(theme="dark")) {
                Surface { com.qingluan.schedule.ui.ValueRow("当前课表","测试课表",{},Modifier.testTag("restored-card")) }
            }
        }
        val image=ui.onNodeWithTag("restored-card").captureToImage().asAndroidBitmap()
        org.junit.Assert.assertEquals(image.getPixel(image.width/2,image.height/7),image.getPixel(image.width/2,image.height*6/7))
    }

    @Test
    fun oledIsBlackAndSettingsGroupHasOnlyOutsideCornersRounded() {
        val scheme=com.qingluan.schedule.ui.dynamicBackdrop(com.qingluan.schedule.ui.appColorScheme("jade",true),true)
        org.junit.Assert.assertEquals(Color.Black,scheme.background)
        org.junit.Assert.assertEquals(Color.Black,scheme.surface)
        ui.setContent {
            QingluanTheme(AppSettings(theme="dark",dynamicColor=true)) {
                Surface { com.qingluan.schedule.ui.SettingsGroup(Modifier.testTag("group-shape")) {
                    com.qingluan.schedule.ui.ValueRow("甲","1",{})
                    com.qingluan.schedule.ui.ValueRow("乙","2",{})
                    com.qingluan.schedule.ui.ValueRow("丙","3",{})
                } }
            }
        }
        val image=ui.onNodeWithTag("group-shape").captureToImage().asAndroidBitmap()
        val center=image.width/2
        org.junit.Assert.assertNotEquals(image.getPixel(1,1),image.getPixel(center,8))
        org.junit.Assert.assertEquals(image.getPixel(1,image.height/2),image.getPixel(center,image.height/2-12))
        org.junit.Assert.assertNotEquals(image.getPixel(1,image.height-2),image.getPixel(center,image.height-8))
    }

}
