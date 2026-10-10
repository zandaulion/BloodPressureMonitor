// SPDX-License-Identifier: AGPL-3.0-only
package com.zandaulion.bpdigitized.data.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.min

internal data class HearthConfig(
    val size: Int = 512,
    val minScore: Double = .2,
    val acceptScore: Double = .25,
    val digitMinScore: Double = .5,
    val portraitFallback: Boolean = true,
    val adaptiveFallback: Boolean = true,
    val rectificationFallback: Boolean = true,
) {
    init {
        require(size == 512)
        require(listOf(minScore, acceptScore, digitMinScore).all { it.isFinite() && it in 0.0..1.0 })
    }
}

/** CPU only; sessions and tensors never leave the reader's single executor thread. */
internal class HearthEngine(loadModel: (String) -> ByteArray, private val config: HearthConfig) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
    private val detector: OrtSession
    private val digits: OrtSession

    init {
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            options.setInterOpNumThreads(1)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            detector = environment.createSession(loadModel("bp-detector.onnx"), options)
            try {
                digits = environment.createSession(loadModel("bp-digits.onnx"), options)
            } catch (error: Throwable) {
                detector.close()
                throw error
            }
        }
        try {
            require((detector.inputInfo.values.single().info as TensorInfo).shape.contentEquals(longArrayOf(1, 3, 512, 512)))
            val digitShape = (digits.inputInfo.values.single().info as TensorInfo).shape
            require(digitShape.size == 4 && digitShape.drop(1) == listOf(1L, 48L, 32L))
        } catch (error: Throwable) { close(); throw error }
    }

    fun infer(image: HearthImage, check: () -> Unit): HearthResult {
        check()
        val input = image.letterbox(config.size, check)
        val detections = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.tensor), longArrayOf(1, 3, 512, 512)).use { tensor ->
            detector.run(mapOf(detector.inputNames.single() to tensor)).use { output ->
                check()
                val values = output[0] as OnnxTensor
                val buffer = values.floatBuffer
                val data = FloatArray(buffer.remaining()).also { buffer.get(it) }
                HearthReading.decode(data, values.info.shape, input.scale, input.left, input.top,
                    image.width, image.height, config.minScore)
            }
        }
        val refined = detections.filter { it.label == "row" }.toMutableList()
        // Bound native allocations even on noisy images with many digit proposals.
        for (batch in detections.filter { it.label != "row" }.chunked(32)) {
            check()
            val pixels = FloatArray(batch.size * 32 * 48)
            batch.forEachIndexed { i, d -> image.digitTensor(d.box, check).copyInto(pixels, i * 32 * 48) }
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixels), longArrayOf(batch.size.toLong(), 1, 48, 32)).use { tensor ->
                digits.run(mapOf(digits.inputNames.single() to tensor)).use { output ->
                    check()
                    val values = output[0] as OnnxTensor
                    require(values.info.shape.contentEquals(longArrayOf(batch.size.toLong(), 11)))
                    val buffer = values.floatBuffer
                    val logits = FloatArray(buffer.remaining()).also { buffer.get(it) }
                    batch.forEachIndexed { i, d ->
                        val offset = i * 11
                        var best = 0
                        for (c in 1..10) if (logits[offset + c] > logits[offset + best]) best = c
                        val peak = logits[offset + best].toDouble()
                        val sum = (0..10).sumOf { exp(logits[offset + it] - peak) }
                        val confidence = 1.0 / sum
                        if (best != 10 && confidence.isFinite() && confidence >= config.digitMinScore) {
                            refined.add(d.copy(label = best.toString(), score = min(d.score, confidence)))
                        }
                    }
                }
            }
        }
        return HearthReading.assemble(refined, config.minScore, config.acceptScore)
    }

    override fun close() {
        try { digits.close() } finally { detector.close() }
    }
}

internal object HearthPipeline {
    fun read(image: HearthImage, config: HearthConfig, check: () -> Unit,
             infer: (HearthImage) -> HearthResult): HearthResult {
        check()
        val full = infer(image)
        if (full.plausible) return full
        if (config.portraitFallback) {
            val views = HearthReading.portraitCrops(image.width, image.height).map { check(); infer(image.crop(it)) }
            val result = HearthReading.consensus(full, views, "portrait")
            if (result.plausible) return result
        }
        if (config.adaptiveFallback) {
            val region = HearthReading.rowRegion(full.detections, image.width, image.height)
            if (region != null) {
                val crop = image.crop(region)
                val views = listOf(.04, .05).map { check(); infer(crop.normalized(it, check)) }
                val result = HearthReading.consensus(full, views, "adaptive")
                if (result.plausible) return result
            }
        }
        if (config.rectificationFallback) {
            val views = mutableListOf<HearthResult>()
            for (quad in HearthRectification.quads(image, full.detections, check)) {
                check()
                val warped = HearthRectification.warp(image, quad, check) ?: continue
                views.add(infer(warped))
                views.add(infer(warped.normalized(.04, check)))
            }
            return HearthReading.consensus(full, views, "rectified")
        }
        return full
    }
}
