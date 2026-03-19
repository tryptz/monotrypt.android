package tf.monochrome.android.ui.eq

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tf.monochrome.android.domain.model.Headphone

/**
 * Full-screen headphone browser with A-Z alphabetical index sidebar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeadphoneSelectScreen(
    viewModel: EqViewModel,
    onHeadphoneSelected: (Headphone) -> Unit,
    onDismiss: () -> Unit
) {
    val availableHeadphones by viewModel.availableHeadphones.collectAsState()
    val headphonesLoading by viewModel.headphonesLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val headphoneTypeFilter by viewModel.headphoneTypeFilter.collectAsState()

    var localSearchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadAvailableHeadphones()
    }

    // Filter headphones
    val filteredHeadphones = remember(availableHeadphones, localSearchQuery, headphoneTypeFilter) {
        availableHeadphones.filter { hp ->
            val matchesSearch = localSearchQuery.isEmpty() ||
                    hp.name.contains(localSearchQuery, ignoreCase = true)
            val matchesType = headphoneTypeFilter == null || hp.type == headphoneTypeFilter
            matchesSearch && matchesType
        }
    }

    // Group by first letter
    val groupedHeadphones = remember(filteredHeadphones) {
        filteredHeadphones
            .groupBy {
                val first = it.name.firstOrNull()?.uppercaseChar() ?: '#'
                if (first.isLetter()) first else '#'
            }
            .toSortedMap()
    }

    val availableLetters = remember(groupedHeadphones) { groupedHeadphones.keys }

    // Build flat list for LazyColumn indexing (letter headers + items)
    data class ListEntry(
        val isHeader: Boolean,
        val letter: Char? = null,
        val headphone: Headphone? = null
    )

    val flatList = remember(groupedHeadphones) {
        buildList {
            groupedHeadphones.forEach { (letter, headphones) ->
                add(ListEntry(isHeader = true, letter = letter))
                headphones.forEach { hp ->
                    add(ListEntry(isHeader = false, headphone = hp))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // ─── Header ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Database",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "AutoEq Repo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${filteredHeadphones.size} models",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = { viewModel.refreshHeadphones() }) {
                Icon(Icons.Default.Refresh, "Refresh", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", modifier = Modifier.size(20.dp))
            }
        }

        // ─── Search Bar ───
        OutlinedTextField(
            value = localSearchQuery,
            onValueChange = { localSearchQuery = it },
            placeholder = { Text("Search model (e.g. HD 600)...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (localSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { localSearchQuery = "" }) {
                        Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Error ───
        if (!error.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp
                )
            }
        }

        // ─── Content: List + A-Z Sidebar ───
        if (headphonesLoading && availableHeadphones.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Loading headphones from GitHub...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredHeadphones.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No headphones found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Headphone list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    flatList.forEach { entry ->
                        if (entry.isHeader && entry.letter != null) {
                            stickyHeader(key = "header_${entry.letter}") {
                                Text(
                                    entry.letter.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        } else if (entry.headphone != null) {
                            item(key = "hp_${entry.headphone.id}") {
                                HeadphoneListItem(
                                    headphone = entry.headphone,
                                    onClick = {
                                        viewModel.selectHeadphone(entry.headphone)
                                        onHeadphoneSelected(entry.headphone)
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }

                // A-Z Index Sidebar
                AlphabeticalIndexSidebar(
                    availableLetters = availableLetters,
                    onLetterClicked = { letter ->
                        scope.launch {
                            val index = flatList.indexOfFirst {
                                it.isHeader && it.letter == letter
                            }
                            if (index >= 0) {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HeadphoneListItem(
    headphone: Headphone,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Headphones,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                headphone.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${headphone.measurements.size} profiles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            ">",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
