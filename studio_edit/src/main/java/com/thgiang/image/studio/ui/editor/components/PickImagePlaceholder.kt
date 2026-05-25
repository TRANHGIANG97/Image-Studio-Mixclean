package com.thgiang.image.studio.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thgiang.image.studio.R
import com.thgiang.image.studio.ui.editor.theme.LocalEditorTokens

/**
 * Premium placeholder card — shown before the user picks a product image.
 * Style: Canva / Photoroom — white card on #EBEBEB workspace.
 */
@Composable
fun PickImagePlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalEditorTokens.current

    Box(
        modifier = modifier
            .widthIn(min = 200.dp, max = 300.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor   = Color.Black.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFE5E7EB),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload icon circle
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(tokens.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_replace_product),
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Title
            Text(
                text = stringResource(R.string.studio_pick_product_title),
                color = tokens.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            // Subtitle
            Text(
                text = stringResource(R.string.studio_pick_product_subtitle),
                color = tokens.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(4.dp))

            // CTA button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tokens.accent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.studio_tap_to_pick_product),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
