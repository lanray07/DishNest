package com.dishnest.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArabicFireUiTest {
    @get:Rule val rule = createAndroidComposeRule<QaConfigurationActivity>()

    @Test fun arabicPlannerAndNativeDialogRemainReachableAtLargeFontScale() {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        lateinit var reset: kotlinx.coroutines.Job
        rule.runOnIdle { reset = ViewModelProvider(rule.activity)[KitchenViewModel::class.java].deleteAll() }
        runBlocking { reset.join() }
        val context = rule.activity
        assertEquals("ar", context.resources.configuration.locales[0].language)
        assertEquals(1.5f, context.resources.configuration.fontScale, 0.001f)
        rule.onNodeWithText(context.getString(R.string.nav_plan), useUnmergedTree = true).performClick()
        rule.onNodeWithText(context.getString(R.string.manage_plans)).performClick()
        rule.onNodeWithText(context.getString(R.string.new_plan_name)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.create_plan)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("New plan name").assertDoesNotExist()
        val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir, "fire-qa-arabic-large-font.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
