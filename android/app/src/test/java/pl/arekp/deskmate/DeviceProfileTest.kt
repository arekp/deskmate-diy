package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileTest {

    @Test
    fun detectsGalaxyS23() {
        assertEquals(DeviceProfile.GALAXY_S23, DeviceProfile.detect("SM-S911B"))
        assertEquals(DeviceProfile.GALAXY_S23, DeviceProfile.detect("sm-s911b"))
    }

    @Test
    fun detectsGalaxyZFlip6() {
        assertEquals(DeviceProfile.GALAXY_Z_FLIP6, DeviceProfile.detect("SM-F741B"))
    }

    @Test
    fun detectsGalaxyZFold8Ultra() {
        assertEquals(DeviceProfile.GALAXY_Z_FOLD8_ULTRA, DeviceProfile.detect("SM-F976B"))
    }

    @Test
    fun unknownPhone_fallsBackToGeneric() {
        assertEquals(DeviceProfile.GENERIC, DeviceProfile.detect("Pixel 8"))
        assertEquals(DeviceProfile.GENERIC, DeviceProfile.detect(""))
    }

    @Test
    fun profilesHaveSaneCameraValues() {
        DeviceProfile.entries.forEach {
            assertTrue(it.name, it.cameraHfovDeg in 40f..120f)
            assertTrue(it.name, it.cameraVfovDeg in 30f..100f)
            assertTrue(it.name, it.eyeScale in 0.5f..1.5f)
        }
    }
}
