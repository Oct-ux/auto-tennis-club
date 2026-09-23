package com.autotennisclub.app.session

import com.autotennisclub.app.pusun.SpinType
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomConfigTest {
    private val rotate = CustomConfig(sequence = CustomConfig.ROTATE_POINTS, zones = setOf(LandingZone.NET_LEFT))
    private val fixed = CustomConfig(sequence = CustomConfig.FIXED_POINT, zones = setOf(LandingZone.NET_LEFT))

    @Test fun rotatePointsTogglesZonesInAndOut() {
        val added = rotate.toggle(LandingZone.BACK_RIGHT)
        assertEquals(setOf(LandingZone.NET_LEFT, LandingZone.BACK_RIGHT), added.zones)
        assertEquals(setOf(LandingZone.BACK_RIGHT), added.toggle(LandingZone.NET_LEFT).zones)
    }

    @Test fun fixedPointKeepsOneZone() {
        assertEquals(setOf(LandingZone.BACK_LEFT), fixed.toggle(LandingZone.BACK_LEFT).zones)
    }

    @Test fun switchingToFixedPointKeepsOnlyOneZone() {
        val many = CustomConfig(zones = LandingZone.entries.toSet())
        assertEquals(1, many.withSequence(CustomConfig.FIXED_POINT).zones.size)
    }

    @Test fun noSpinSendsZeroSpinValue() {
        assertEquals(0, CustomConfig(spin = "NONE", intensity = SpinIntensity.HEAVY).spinValue)
        assertEquals(SpinType.NONE, CustomConfig(spin = "NONE").spinType)
        assertEquals(SpinIntensity.HEAVY.spinValue, CustomConfig(spin = "BACKSPIN", intensity = SpinIntensity.HEAVY).spinValue)
    }
}
