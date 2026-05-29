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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zandaulion.bpdigitized.domain.model.BPCategory
import com.zandaulion.bpdigitized.presentation.extensions.badgeLabelRes
import com.zandaulion.bpdigitized.presentation.theme.CategoryCrisis
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage1
import com.zandaulion.bpdigitized.presentation.theme.CategoryStage2

@Composable
fun CategoryBadge(category: BPCategory, modifier: Modifier = Modifier) {
    val color = when (category) {
        BPCategory.NORMAL              -> CategoryNormal
        BPCategory.ELEVATED            -> CategoryElevated
        BPCategory.STAGE_1             -> CategoryStage1
        BPCategory.STAGE_2             -> CategoryStage2
        BPCategory.HYPERTENSIVE_CRISIS -> CategoryCrisis
    }
    Text(
        text = stringResource(category.badgeLabelRes()),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
