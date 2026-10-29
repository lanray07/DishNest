package com.dishnest.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dishnest.app.platform.AppLanguages
import com.dishnest.app.ui.DishNestTheme
import com.dishnest.app.ui.LanguageDialog
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-port-mdpi", application = DishNestApp::class)
class LanguagePickerUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun open() {
        rule.runOnUiThread { rule.activity.setContent { DishNestTheme { LanguageDialog {} } } }
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(R.string.language_title)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "fr-rFR-w800dp-h1280dp-port-mdpi")
    fun frenchPickerUsesRealRadioControlsAndUpdatesTheSystemPreference() {
        open()
        rule.onNodeWithText("Deutsch").assertHasClickAction()
        rule.onNode(isDialog()).captureRoboImage("../artifacts/screens/host-language-picker-fr.png")
        rule.onNodeWithText("Deutsch").performClick()
        rule.runOnIdle { assertEquals("de", AppLanguages.selected(rule.activity)) }
    }

    @Test @Config(qualifiers = "ar-rEG-ldrtl-w800dp-h1280dp-port-mdpi")
    fun arabicPickerRendersLocalizedCopyAndCancellationDoesNotChangePreference() {
        open()
        val before = AppLanguages.selected(rule.activity)
        rule.onNodeWithText(rule.activity.getString(R.string.language_system)).assertIsDisplayed()
        rule.onNode(isDialog()).captureRoboImage("../artifacts/screens/host-language-picker-ar.png")
        rule.onNodeWithText(rule.activity.getString(R.string.cancel)).performClick()
        rule.runOnIdle { assertEquals(before, AppLanguages.selected(rule.activity)) }
    }
    @Test fun kitchenDeletionDisablesUsageCountsAndUpdatesItsToggle() {
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[KitchenViewModel::class.java]
        rule.runOnUiThread {
            vm.metrics.enabled = true
            vm.metrics.record("voice_used")
            rule.activity.setContent { DishNestTheme { com.dishnest.app.ui.SettingsScreen(KitchenState(), vm) {} } }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(rule.activity.getString(R.string.ui_optional_usage_counts_ea8511)))
        rule.onNode(isToggleable()).assertIsOn()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(rule.activity.getString(R.string.ui_delete_all_kitchen_data_013193)))
        rule.onNodeWithText(rule.activity.getString(R.string.ui_delete_all_kitchen_data_013193)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.ui_permanently_delete_9003e1)).performClick()
        rule.waitUntil(20000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            vm.message.value == rule.activity.getString(R.string.kitchen_deleted)
        }
        rule.runOnIdle { assertFalse(vm.metrics.enabled) }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(rule.activity.getString(R.string.ui_optional_usage_counts_ea8511)))
        rule.onNode(isToggleable()).assertIsOff()
    }

}
