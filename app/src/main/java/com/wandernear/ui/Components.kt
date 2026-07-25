package com.wandernear.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Synagogue
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.material.icons.filled.TempleHindu
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wandernear.ui.theme.categoryTint

/**
 * Shared, on-brand UI building blocks. Keeping the card, the section header, the
 * category badge and the action buttons in ONE place means every screen looks the
 * same — the fix for a UI that used to be a wall of identical grey boxes.
 */

/** The standard content card: a clean white (or dark) surface, soft rounded corners,
 *  a gentle lift in light mode and a hairline in dark mode (where shadows don't show).
 *  Pass [onClick] to make the whole card tappable (with a proper ripple). */
@Composable
fun WnCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surface
    val shadow = if (dark) 0.dp else 2.dp
    val border = if (dark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, shadowElevation = shadow, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, shadowElevation = shadow, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

/**
 * A rounded, tinted badge holding a category's icon — the splash of colour that makes
 * each place instantly recognisable (orange fork for food, green tree for a park…).
 * Worship uses the faith-specific icon when we know the religion.
 */
@Composable
fun CategoryBadge(category: String, religion: String? = null, size: Dp = 42.dp) {
    val tint = categoryTint(category, isSystemInDarkTheme())
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            categoryIcon(category, religion),
            contentDescription = null,
            tint = tint.icon,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** The icon for a place category (faith-aware for worship). */
fun categoryIcon(category: String, religion: String? = null): ImageVector = when (category) {
    "food" -> Icons.Filled.Restaurant
    "worship" -> when (religion) {
        "muslim" -> Icons.Filled.Mosque
        "christian" -> Icons.Filled.Church
        "jewish" -> Icons.Filled.Synagogue
        "hindu" -> Icons.Filled.TempleHindu
        "buddhist" -> Icons.Filled.TempleBuddhist
        else -> Icons.Filled.AccountBalance
    }
    "outdoor" -> Icons.Filled.Park
    "shopping" -> Icons.Filled.ShoppingBag
    "culture" -> Icons.Filled.TheaterComedy
    "attraction" -> Icons.Filled.Attractions
    "safety" -> Icons.Filled.LocalPolice
    "health" -> Icons.Filled.LocalHospital
    "fuel" -> Icons.Filled.LocalGasStation
    "parking" -> Icons.Filled.LocalParking
    else -> Icons.Filled.Place
}

/** A section heading inside a card: a small tinted icon + a title, optional trailing. */
@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * A compact, icon-led action: "Directions", "Save", "Call", "Website". A tappable pill
 * with a comfortable touch target, coloured by role (primary by default). Replaces the
 * old plain text buttons so actions read at a glance.
 */
@Composable
fun WnActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Common action buttons, so every card spells them the same way. */
@Composable fun DirectionsButton(onClick: () -> Unit) =
    WnActionButton(Icons.Filled.Navigation, "Directions", onClick)

@Composable fun SaveButton(onClick: () -> Unit) =
    WnActionButton(Icons.Filled.BookmarkBorder, "Save", onClick, MaterialTheme.colorScheme.tertiary)

@Composable fun CallButton(onClick: () -> Unit) =
    WnActionButton(Icons.Filled.Call, "Call", onClick, MaterialTheme.colorScheme.secondary)

@Composable fun WebsiteButton(onClick: () -> Unit) =
    WnActionButton(Icons.Filled.Language, "Website", onClick, MaterialTheme.colorScheme.secondary)

/**
 * A tappable suggestion pill for the "what are you in the mood for?" row — a friendly,
 * slightly-raised chip that clearly invites a tap.
 */
@Composable
fun MoodChip(text: String, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (dark) 0.dp else 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Centred in a comfortable (>=44dp) target, so the label stays centred even if it
        // wraps at large system font sizes.
        Box(
            modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A row of action buttons that wraps to a new line when they don't fit (e.g. a place
 *  with Directions + Call + Website on a narrow card), so a button never clips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
