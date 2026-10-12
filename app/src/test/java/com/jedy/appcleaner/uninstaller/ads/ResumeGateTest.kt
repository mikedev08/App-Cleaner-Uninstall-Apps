package com.jedy.appcleaner.uninstaller.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeGateTest {

    private val gate = ResumeGate()

    private fun ResumeGate.backgroundAndReturn(): Boolean {
        onStop(changingConfigurations = false)
        onStart()
        return onResume()
    }

    @Test
    fun `cold start is not a resume`() {
        gate.onStart()
        assertFalse(gate.onResume())
    }

    @Test
    fun `returning from the home screen is a resume, every time`() {
        gate.onStart()
        gate.onResume()
        assertTrue(gate.backgroundAndReturn())
        assertTrue(gate.backgroundAndReturn())
    }

    @Test
    fun `a new activity after the old one went to the background is a launch, not a resume`() {
        gate.onStart()
        gate.onResume()
        gate.onStop(changingConfigurations = false) // Back from Home: the activity finishes
        gate.onCreate() // relaunched in the same process
        gate.onStart()
        assertFalse(gate.onResume())
    }

    @Test
    fun `coming back from Settings, the Play Store or an ad is not a resume`() {
        gate.onStart()
        gate.onResume()
        gate.onExternalLaunch()
        assertFalse(gate.backgroundAndReturn())
    }

    @Test
    fun `the suppression lasts for one return only`() {
        gate.onStart()
        gate.onResume()
        gate.onExternalLaunch()
        gate.backgroundAndReturn()
        assertTrue(gate.backgroundAndReturn())
    }

    @Test
    fun `a translucent activity that never stops the app clears the suppression`() {
        gate.onStart()
        gate.onResume()
        gate.onExternalLaunch() // a permission dialog: paused, never stopped
        assertFalse(gate.onResume())
        assertTrue(gate.backgroundAndReturn())
    }

    @Test
    fun `a configuration change is not a resume`() {
        gate.onStart()
        gate.onResume()
        gate.onStop(changingConfigurations = true)
        gate.onStart()
        assertFalse(gate.onResume())
    }

    @Test
    fun `a resume is reported once`() {
        gate.onStart()
        gate.onResume()
        gate.onStop(changingConfigurations = false)
        gate.onStart()
        assertTrue(gate.onResume())
        assertFalse(gate.onResume())
    }
}
