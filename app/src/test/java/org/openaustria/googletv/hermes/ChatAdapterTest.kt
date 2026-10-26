package org.openaustria.googletv.hermes

import org.junit.Assert.assertEquals
import org.junit.Test

/** Seitenweises Scrollen in Zeilen, die höher als der sichtbare Verlauf (0 bis 400, Seite 300) sind. */
class ChatAdapterTest {

    @Test
    fun `row that is fully visible needs no scrolling`() {
        assertEquals(0, rowScrollAmount(100, 300, 0, 400, down = true))
        assertEquals(0, rowScrollAmount(100, 300, 0, 400, down = false))
    }

    @Test
    fun `down pages through a tall row until its end is visible`() {
        assertEquals(300, rowScrollAmount(0, 1000, 0, 400, down = true))
        assertEquals(100, rowScrollAmount(-500, 500, 0, 400, down = true))
        assertEquals(0, rowScrollAmount(-600, 400, 0, 400, down = true))
    }

    @Test
    fun `up pages back until the start of the row is visible`() {
        assertEquals(-300, rowScrollAmount(-1000, 200, 0, 400, down = false))
        assertEquals(-50, rowScrollAmount(-50, 900, 0, 400, down = false))
        assertEquals(0, rowScrollAmount(0, 900, 0, 400, down = false))
    }
}
