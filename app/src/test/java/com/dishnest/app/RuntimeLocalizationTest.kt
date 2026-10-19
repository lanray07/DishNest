package com.dishnest.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DishNestApp::class)
class RuntimeLocalizationTest {
    @Test fun allRuntimeMessagesExportsAndLabelsUseResources() =
        RuntimeLocalizationChecks.allLocales(ApplicationProvider.getApplicationContext())

    @Test fun quantitiesUseTheActiveResourceLocale() =
        RuntimeLocalizationChecks.resourceLocaleWinsOverProcessLocale(ApplicationProvider.getApplicationContext())
}
