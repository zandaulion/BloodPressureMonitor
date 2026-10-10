package com.zandaulion.bpdigitized.data.ocr

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.*

class HearthParityTest {
    @Test fun nativeAlgorithmsMatchSyntheticWebReferenceCorpus() {
        val stream = javaClass.getResourceAsStream("/hearth-parity.tsv") ?: error("Missing parity corpus")
        var count = 0
        stream.bufferedReader().useLines { lines -> lines.filter { !it.startsWith('#') && it.isNotBlank() }.forEach { line ->
            val p = line.split('\t'); count++
            when (p[0]) {
                "assemble" -> assertResult(line, result(p[3]), HearthReading.assemble(detections(p[2])))
                "region" -> {
                    val region = HearthReading.rowRegion(detections(p[2]), 320, 480)
                    assertEquals(line, p[3], region?.let { "${it.x},${it.y},${it.width},${it.height}" } ?: "_")
                }
                "portrait" -> {
                    val (w, h) = p[1].split(',').map { it.toInt() }
                    val actual = HearthReading.portraitCrops(w, h).joinToString(";") { "${it.x},${it.y},${it.width},${it.height}" }.ifEmpty { "_" }
                    assertEquals(line, p[2], actual)
                }
                "normalize" -> {
                    val dims = p[1].split(',')
                    val image = pattern(dims[0].toInt(), dims[1].toInt(), dims[2].toInt())
                    assertEquals(line, p[2], hash(image.normalized(dims[3].toDouble())))
                }
                "warp" -> {
                    val quad = p[1].split(',').map { it.toDouble() }.chunked(2).map { HearthPoint(it[0], it[1]) }
                    val image = HearthRectification.warp(pattern(160, 140, 17), quad)!!
                    assertEquals(line, p[2], "${image.width},${image.height}")
                    assertEquals(line, p[3], hash(image))
                }
                "detect" -> {
                    val actual = HearthRectification.detect(display()).joinToString(";") { quad -> quad.flatMap { listOf(it.x.toInt(), it.y.toInt()) }.joinToString(",") }.ifEmpty { "_" }
                    assertEquals(line, p[2], actual)
                }
                "consensus" -> assertResult(line, result(p[5]), HearthReading.consensus(result(p[2]), listOf(result(p[3]), result(p[4])), p[1]))
                else -> error("Unknown fixture ${p[0]}")
            }
        } }
        assertTrue("Reference corpus unexpectedly empty", count >= 900)
    }

    @Test fun letterboxPreservesRgbChannelsAndPadding() {
        val image = HearthImage(2, 1, intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt()))
        val box = image.letterbox(2)
        assertEquals(1.0, box.scale, 0.0)
        assertArrayEquals(floatArrayOf(1f, 0f, 114f/255, 114f/255, 0f, 1f, 114f/255, 114f/255, 0f, 0f, 114f/255, 114f/255), box.tensor, 0f)
        val white = HearthImage(32,48,IntArray(32*48) { -1 }).digitTensor(HearthBox(0.0,0.0,32.0,48.0))
        assertTrue(white.all { it == 0f })
    }

    @Test fun pipelinePreservesPlausibleFullFrameAndRejectsDisagreeingCrops() {
        val image = HearthImage(100,160,IntArray(16000) { -1 })
        val full = result("review,123,78,64,0.21")
        var calls = 0
        assertSame(full, HearthPipeline.read(image, HearthConfig(), {}) { calls++; full })
        assertEquals(1, calls)
        val outputs = ArrayDeque(listOf(HearthResult(), result("candidate,123,78,64,0.8"), result("candidate,124,78,64,0.8")))
        val rejected = HearthPipeline.read(image, HearthConfig(rectificationFallback = false), {}) { outputs.removeFirst() }
        assertEquals(HearthStatus.RETAKE, rejected.status)
        assertTrue(outputs.isEmpty())
    }

    @Test fun detectorClassOrderAndLetterboxCoordinateRecovery() {
        val data = FloatArray(30)
        data[0]=20f; data[1]=60f; data[2]=30f; data[3]=30f
        data[4]=10f; data[5]=10f; data[6]=20f; data[7]=20f
        data[12]=.8f; data[15]=.9f
        val decoded = HearthReading.decode(data,longArrayOf(1,15,2),1.0,0,0,100,100,.25)
        assertEquals(listOf("2","row"),decoded.map { it.label })
        assertEquals(HearthBox(55.0,20.0,65.0,40.0),decoded.first().box)
    }

    private fun result(text: String): HearthResult {
        val p = text.split(',')
        return HearthResult(HearthStatus.valueOf(p[0].uppercase()), if (p[1]=="_") null else HearthTriplet(p[1].toInt(),p[2].toInt(),p[3].toInt()), p[4].takeIf { it!="_" }?.toDouble())
    }
    private fun assertResult(message: String, expected: HearthResult, actual: HearthResult) {
        assertEquals(message, expected.status, actual.status)
        assertEquals(message, expected.reading, actual.reading)
        if (expected.score == null) assertNull(actual.score) else assertEquals(message, expected.score, actual.score!!, 1e-12)
    }
    private fun detections(text: String): List<HearthDetection> = if (text=="_") emptyList() else text.split(';').map {
        val p=it.split(','); HearthDetection(p[0],p[1].toDouble(),HearthBox(p[2].toDouble(),p[3].toDouble(),p[4].toDouble(),p[5].toDouble()))
    }
    private fun pattern(w: Int,h: Int,seed: Int) = HearthImage(w,h,IntArray(w*h) {
        val x=it%w; val y=it/w
        -0x1000000 or (((x*13+y*7+seed)%256) shl 16) or (((x*3+y*11+seed*2)%256) shl 8) or ((x*5+y*17+seed*3)%256)
    })
    private fun hash(image: HearthImage): String {
        val rgba=ByteArray(image.pixels.size*4)
        image.pixels.forEachIndexed { i,p -> rgba[4*i]=(p ushr 16).toByte(); rgba[4*i+1]=(p ushr 8).toByte(); rgba[4*i+2]=p.toByte(); rgba[4*i+3]=255.toByte() }
        return MessageDigest.getInstance("SHA-256").digest(rgba).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun display(): HearthImage {
        val w=240; val h=180; val pixels=IntArray(w*h) { 0xff191919.toInt() }
        val points=listOf(HearthPoint(48.0,32.0),HearthPoint(200.0,44.0),HearthPoint(186.0,146.0),HearthPoint(36.0,134.0))
        for(i in 0..3) {
            val a=points[i];val b=points[(i+1)%4];val steps=ceil(hypot(b.x-a.x,b.y-a.y)).toInt()
            for(k in 0..steps) {
                val x=(a.x+(b.x-a.x)*k/steps).roundToInt();val y=(a.y+(b.y-a.y)*k/steps).roundToInt()
                for(dy in -4..4)for(dx in -4..4) if(x+dx in 0 until w && y+dy in 0 until h) pixels[(y+dy)*w+x+dx]=0xffdcdcdc.toInt()
            }
        }
        return HearthImage(w,h,pixels)
    }
}
