import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun mapsTagsToCodes() {
        assertEquals(400, AppVersion.code("v0.4.0"))
        assertEquals(401, AppVersion.code("v0.4.1"))
        assertEquals(500, AppVersion.code("0.5.0"))
        assertEquals(1_000, AppVersion.code("v0.10.0"))
        assertEquals(10_000, AppVersion.code("v1.0.0"))
        assertEquals(10_203, AppVersion.code("v1.2.3"))
    }

    @Test fun namesDropThePrefix() {
        assertEquals("0.4.0", AppVersion.name("v0.4.0"))
        assertEquals("0.4.0", AppVersion.name(" 0.4.0 "))
    }

    @Test fun codesGrowWithVersions() {
        val ordered = listOf("v0.3.9", "v0.4.0", "v0.4.1", "v0.4.99", "v0.5.0", "v0.99.99", "v1.0.0", "v1.0.1", "v2.0.0")
        val codes = ordered.map(AppVersion::code)
        assertEquals(codes.sorted(), codes)
        assertTrue(codes.zipWithNext().all { (a, b) -> b > a })
        // Above every code used before v0.4 (v0.3.0 had code 3).
        assertTrue(AppVersion.code("v0.4.0") > 3)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsPreReleaseTags() {
        AppVersion.code("v0.4.0-rc1")
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMinorOver99() {
        AppVersion.code("v0.100.0")
    }
}
