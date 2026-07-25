package com.wandernear.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Rounded, friendly shapes. Cards use `large` (20dp) for a soft, modern feel; small
 * controls use 12dp. Material components pick these up automatically (Card = medium/
 * large, Chip = small, etc.), so the whole app rounds consistently.
 */
val WnShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
