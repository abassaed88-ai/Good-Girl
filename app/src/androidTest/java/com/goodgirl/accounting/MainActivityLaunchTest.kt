package com.goodgirl.accounting

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that a clean installation can create the first screen and keep
 * the launcher activity in the foreground without crashing during startup.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun mainActivityLaunchesAndRemainsResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
