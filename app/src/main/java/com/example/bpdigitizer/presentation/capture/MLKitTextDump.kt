package com.example.bpdigitizer.presentation.capture

import com.google.mlkit.vision.text.Text

/**
 * Serializes ML Kit's Text result (full text + per-block/line/element bounding boxes)
 * into a plain-text dump. Used by the "Share raw OCR" diagnostics flow so we can see
 * exactly what the recognizer returned for a given capture.
 */
internal fun dumpMlKitText(text: Text): String = buildString {
    appendLine("=== Full text ===")
    appendLine(text.text.ifBlank { "(empty)" })
    appendLine()
    appendLine("=== Blocks (${text.textBlocks.size}) ===")
    text.textBlocks.forEachIndexed { bi, block ->
        appendLine("Block $bi  box=${block.boundingBox}")
        block.lines.forEachIndexed { li, line ->
            appendLine("  Line $li  text=\"${line.text}\"  box=${line.boundingBox}")
            line.elements.forEachIndexed { ei, element ->
                appendLine(
                    "    Elem $ei  text=\"${element.text}\"  " +
                        "box=${element.boundingBox}  conf=${"%.2f".format(element.confidence ?: -1f)}",
                )
            }
        }
    }
}
