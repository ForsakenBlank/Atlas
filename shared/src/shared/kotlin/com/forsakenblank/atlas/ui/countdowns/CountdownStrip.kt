package com.forsakenblank.atlas.ui.countdowns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.util.milestone
import com.forsakenblank.atlas.util.upcoming
import java.time.LocalDate

// pinned ones first, then the next few coming up
internal fun stripCountdowns(countdowns: List<Countdown>, today: LocalDate, extra: Int = 4): List<Countdown> {
    val pinned = countdowns.filter { it.pinned }.sortedWith(countdownOrder(today))
    return pinned + upcoming(countdowns, today).filter { !it.pinned }.take(extra)
}

@Composable
fun CountdownStrip(
    countdowns: List<Countdown>,
    onOpen: (Countdown) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val shown = remember(countdowns, today) { stripCountdowns(countdowns, today) }
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Countdowns", Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("See all") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { countdown ->
                StripCard(countdown, today) { onOpen(countdown) }
            }
        }
    }
}

// every line is always there, even when blank, so the cards in a row line up
@Composable
private fun StripCard(countdown: Countdown, today: LocalDate, onClick: () -> Unit) {
    val (number, caption) = bigCount(countdown, today)
    AtlasCard(modifier = Modifier.width(150.dp), color = cardTint(countdown), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountdownBadge(countdown, size = 36.dp)
                Spacer(Modifier.weight(1f))
                if (countdown.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                number,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (caption == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                caption.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                countdown.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                countdown.milestone(today) ?: dateLine(countdown, today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
