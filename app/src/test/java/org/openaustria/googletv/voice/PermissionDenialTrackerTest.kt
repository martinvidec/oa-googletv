package org.openaustria.googletv.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDenialTrackerTest {

    private val tracker = PermissionDenialTracker()

    @Test
    fun `dialog closed with back on first request is not permanent`() {
        tracker.onRequest(rationale = false)
        assertFalse(tracker.onDenied(rationaleAfter = false))
    }

    @Test
    fun `regular denial is not permanent`() {
        tracker.onRequest(rationale = false)
        assertFalse(tracker.onDenied(rationaleAfter = true))
    }

    @Test
    fun `denial after rationale without further rationale is permanent`() {
        tracker.onRequest(rationale = false)
        tracker.onDenied(rationaleAfter = true)
        tracker.onRequest(rationale = true)
        assertTrue(tracker.onDenied(rationaleAfter = false))
    }

    @Test
    fun `second denial without rationale is permanent`() {
        tracker.onRequest(rationale = false)
        assertFalse(tracker.onDenied(rationaleAfter = false))
        tracker.onRequest(rationale = false)
        assertTrue(tracker.onDenied(rationaleAfter = false))
    }

    @Test
    fun `grant resets previous denial`() {
        tracker.onRequest(rationale = false)
        tracker.onDenied(rationaleAfter = false)
        tracker.onGranted()
        tracker.onRequest(rationale = false)
        assertFalse(tracker.onDenied(rationaleAfter = false))
    }
}
