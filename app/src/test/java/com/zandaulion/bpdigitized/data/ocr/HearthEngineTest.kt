package com.zandaulion.bpdigitized.data.ocr

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException

/** Actual ONNX inference with generated pixels; no private photos or readings. */
class HearthEngineTest {
    private fun engine() = HearthEngine({ name ->
        File(System.getProperty("hearth.models", "src/main/assets/hearth/models"), name).readBytes()
    }, HearthConfig())

    @Test fun realModelsRefuseBlankAndNoiseAndReuseSessions() {
        engine().use { engine ->
            val blank = HearthImage(256, 384, IntArray(256 * 384) { -1 })
            repeat(2) {
                val result = HearthPipeline.read(blank, HearthConfig(), {}) { engine.infer(it) {} }
                assertNull(result.reading)
            }
            val noise = HearthImage(160, 240, IntArray(160 * 240) { i ->
                val value = ((i.toLong() * 1103515245 + 12345) ushr 16).toInt() and 255
                -0x1000000 or (value shl 16) or (value shl 8) or value
            })
            assertFalse(engine.infer(noise) {}.plausible)
        }
    }

    @Test fun canceledProcessingCanRetryWithTheSameSessions() {
        engine().use { engine ->
            val blank = HearthImage(160, 240, IntArray(160 * 240) { -1 })
            var checks = 0
            try {
                engine.infer(blank) { if (++checks == 10) throw CancellationException() }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertNull(engine.infer(blank) {}.reading)
        }
    }

    @Test fun incompleteSyntheticDetectionIsRefusedInsteadOfInventingAReading() {
        engine().use { engine ->
            val image = syntheticMonitor()
            val result = HearthPipeline.read(image, HearthConfig(), {}) { engine.infer(it) {} }
            // Hearth's Python reference also detects only one digit on this deliberately
            // simplified drawing. Exercise DigitNet refinement without assuming that a
            // synthetic drawing is a supported real monitor photograph.
            assertEquals(HearthStatus.RETAKE, result.status)
            assertNull(result.reading)
            assertTrue(result.detections.any { it.label != "row" })
        }
    }

    private fun syntheticMonitor(): HearthImage {
        val w = 512; val h = 640; val pixels = IntArray(w * h) { 0xff444444.toInt() }
        fun rectangle(x: Int, y: Int, width: Int, height: Int, color: Int) {
            for (dy in y until y + height) for (dx in x until x + width) pixels[dy * w + dx] = color
        }
        rectangle(35, 40, 442, 530, 0xffb5c3ae.toInt())
        val patterns = mapOf('1' to "bc", '2' to "abdeg", '3' to "abcdg", '7' to "abc", '8' to "abcdefg", '6' to "acdefg", '4' to "bcfg")
        for ((row, text) in listOf("123", "78", "64").withIndex()) for ((column, digit) in text.withIndex()) {
            val x = 130 + (3 - text.length + column) * 90; val y = 90 + row * 155
            for (segment in patterns.getValue(digit)) {
                val r = when (segment) {
                    'a' -> intArrayOf(9, 0, 46, 10); 'g' -> intArrayOf(9, 55, 46, 10); 'd' -> intArrayOf(9, 110, 46, 10)
                    'f' -> intArrayOf(0, 10, 10, 45); 'b' -> intArrayOf(55, 10, 10, 45)
                    'e' -> intArrayOf(0, 65, 10, 45); else -> intArrayOf(55, 65, 10, 45)
                }
                rectangle(x + r[0], y + r[1], r[2], r[3], 0xff202820.toInt())
            }
        }
        return HearthImage(w, h, pixels)
    }
}
