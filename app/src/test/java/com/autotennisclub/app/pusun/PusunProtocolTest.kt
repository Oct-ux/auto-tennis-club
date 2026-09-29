package com.autotennisclub.app.pusun

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PusunProtocolTest {
    @Test fun startProgramFrame() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x6A, 0x05, 0xA5.toByte()),
            PusunFrameBuilder.build(PusunCommand.Start(StartMode.PROGRAM)).single()
        )
    }

    @Test fun stopFrame() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x6B, 0x00, 0xA5.toByte()),
            PusunFrameBuilder.build(PusunCommand.Stop).single()
        )
    }

    @Test fun batteryParser() {
        assertEquals(
            PusunNotification.Battery(87),
            PusunNotificationParser.parse(byteArrayOf(0xBB.toByte(), 0x03, 87, 0xB5.toByte()))
        )
    }

    @Test fun noBallsFaultParser() {
        assertEquals(
            PusunNotification.Fault(3, FaultType.NO_BALLS),
            PusunNotificationParser.parse(byteArrayOf(0xBB.toByte(), 0x5E, 3, 0xB5.toByte()))
        )
    }
}