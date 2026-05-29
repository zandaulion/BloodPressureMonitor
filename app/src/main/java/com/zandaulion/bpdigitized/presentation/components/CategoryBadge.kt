package com.zandaulion.bpdigitized.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.presentation.theme.CategoryCrisis
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage1
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2

@Composable
fun CategoryBadge(category: BPCategory, modifier: Modifier = Modifier) {
    val (color, label) = when (category) {
        BPCategory.NORMAL -> CategoryNormal to "Normal"
        BPCategory.ELEVATED -> CategoryElevated to "Elevated"
        BPCategory.STAGE_1 -> CategoryStage1 to "Stage 1"
        BPCategory.STAGE_2 -> CategoryStage2 to "Stage 2"
        BPCategory.HYPERTENSIVE_CRISIS -> CategoryCrisis to "Crisis"
    }
    Text(
        text = label,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
