package app.familycalendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodeTest {
    @Test
    fun codesAreEightUnambiguousCharacters() {
        repeat(100) {
            val code = SyncManager.newInviteCode()
            assertEquals(8, code.length)
            assertTrue(code.none { it in "01IO" })
            assertTrue(code.all { it.isUpperCase() || it.isDigit() })
        }
    }
}
