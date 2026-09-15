package com.example.audiobook

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase R0 — Smoke Test.
 * First real instrumented test on device/emulator in project history.
 *
 * Launches MainActivity and verifies:
 * 1. The activity starts without crash.
 * 2. The app context is valid.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun appLaunchesWithoutCrash() {
        val scenario = activityRule.scenario
        scenario.recreate()
    }

    @Test
    fun appContextIsValid() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assert(appContext.packageName == "com.example.audiobook")
    }
}
