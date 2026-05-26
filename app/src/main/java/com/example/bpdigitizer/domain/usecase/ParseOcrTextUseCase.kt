package com.example.bpdigitizer.domain.usecase

import com.google.mlkit.vision.text.Text
import javax.inject.Inject

data class ParsedReading(
    val systolic: Int?,
    val diastolic: Int?,
    val pulse: Int?,
)

class ParseOcrTextUseCase @Inject constructor() {
    operator fun invoke(text: Text): ParsedReading {
        // TODO: implement parsing per spec 2.3.1
        //  1. Filter numeric tokens.
        //  2. Group by Y-axis bounding box.
        //  3. Sort top-to-bottom; assign Sys/Dia/Pulse.
        //  4. Fallback to magnitude sort if Sys <= Dia.
        return ParsedReading(null, null, null)
    }
}
