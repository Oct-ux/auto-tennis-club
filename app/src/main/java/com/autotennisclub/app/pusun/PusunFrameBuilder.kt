package com.autotennisclub.app.pusun

object PusunFrameBuilder {
    private const val HEAD = 0xAA
    private const val END = 0xA5

    fun build(command: PusunCommand): List<ByteArray> = when (command) {
        is PusunCommand.SetFrequencyGrade -> listOf(frame(0x61, byte(command.grade)))
        is PusunCommand.SetSpin -> listOf(frame(0x62, byte(command.type.protocolValue), byte(command.value)))
        is PusunCommand.SetVelocity -> listOf(frame(0x63, byte(command.value)))
        is PusunCommand.SetDirection -> listOf(frame(0x6C, u16(command.lr), u16(command.ud)))
        is PusunCommand.SetPoint -> listOf(frame(command.point, u16(command.lr), u16(command.ud)))
        is PusunCommand.ProgramPoints -> buildProgram(command.points)
        is PusunCommand.Start -> listOf(frame(0x6A, byte(command.mode.protocolValue)))
        PusunCommand.Stop -> listOf(frame(0x6B, 0x00))
    }

    private fun buildProgram(points: List<Int>): List<ByteArray> {
        require(points.size <= 28) { "PUSUN supports up to 28 program points" }
        return points.chunked(5).mapIndexed { index, chunk ->
            frameRaw(
                0x6D,
                byteArrayOf(byte(index + 1), *chunk.map { byte(it) }.toByteArray(), *ByteArray(5 - chunk.size))
            )
        }
    }

    private fun frame(command: Int, vararg payload: Byte): ByteArray =
        frameRaw(command, payload)

    private fun frame(command: Int, vararg payload: ByteArray): ByteArray =
        frameRaw(command, payload.flatMap { it.asList() }.toByteArray())

    private fun frameRaw(command: Int, payload: ByteArray): ByteArray =
        byteArrayOf(HEAD.toByte(), command.toByte(), *payload, END.toByte())

    private fun u16(value: Int): ByteArray =
        byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun byte(value: Int): Byte = (value and 0xFF).toByte()
}