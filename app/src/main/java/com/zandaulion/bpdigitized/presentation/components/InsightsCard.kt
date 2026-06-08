package com.zandaulion.bpdigitized.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zandaulion.bpdigitized.R
import com.zandaulion.bpdigitized.domain.model.BpTag
import com.zandaulion.bpdigitized.domain.model.Insight
import com.zandaulion.bpdigitized.domain.model.InsightKind
import com.zandaulion.bpdigitized.domain.model.InsightTone
import com.zandaulion.bpdigitized.presentation.extensions.labelRes
import com.zandaulion.bpdigitized.presentation.extensions.templateRes
import com.zandaulion.bpdigitized.presentation.theme.CategoryElevated
import com.zandaulion.bpdigitized.presentation.theme.CategoryNormal

/** Number of insights shown before the "N more" button appears. */
private const val COLLAPSED = 3

@Composable
fun InsightsCard(
    insights: List<Insight>,
    modifier: Modifier = Modifier,
) {
    if (insights.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) insights else insights.take(COLLAPSED)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            Text(
                text = stringResource(R.string.insights_card_title),
                style = MaterialTheme.typography.titleSmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { insight ->
                    InsightRow(insight)
                }
            }

            if (insights.size > COLLAPSED) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.insights_show_less)
                        } else {
                            stringResource(
                                R.string.insights_show_more,
                                insights.size - COLLAPSED,
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: Insight) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Tone dot — offset slightly to align with the text cap-height
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(insight.tone.dotColor()),
        )

        // TAG_HIGHER / TAG_LOWER carry the BpTag enum name in args[0]; resolve
        // it to a localized label before formatting the template string.
        val resolvedArgs: Array<Any> = when (insight.kind) {
            InsightKind.TAG_HIGHER, InsightKind.TAG_LOWER -> {
                val tag = BpTag.entries.firstOrNull { it.name == insight.args[0] }
                val tagLabel = if (tag != null) stringResource(tag.labelRes()) else insight.args[0]
                (listOf(tagLabel) + insight.args.drop(1)).toTypedArray()
            }
            else -> insight.args.toTypedArray()
        }

        Text(
            text = stringResource(insight.kind.templateRes(), *resolvedArgs),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun InsightTone.dotColor(): Color = when (this) {
    InsightTone.POSITIVE -> CategoryNormal    // green
    InsightTone.WATCH    -> CategoryElevated  // amber
    InsightTone.NEUTRAL  -> MaterialTheme.colorScheme.onSurfaceVariant
}
