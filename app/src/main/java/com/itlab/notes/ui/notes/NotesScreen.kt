package com.itlab.notes.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun notesListScreen(
    directoryName: String,
    notes: List<NoteItemUi>,
    onBack: () -> Unit,
    onAddNoteClick: () -> Unit,
    onNoteClick: (NoteItemUi) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            notesTopBar(
                directoryName = directoryName,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            notesFab(onAddNoteClick = onAddNoteClick)
        },
    ) { paddingValues ->
        notesListContent(
            notes = notes,
            paddingValues = paddingValues,
            onNoteClick = onNoteClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun notesTopBar(
    directoryName: String,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = { Text(directoryName, color = colors.onSurface) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.onSurface,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified,
            ),
    )
}

@Composable
private fun notesFab(onAddNoteClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onAddNoteClick,
        containerColor = colors.primary,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = colors.onPrimary,
        )
    }
}

@Composable
private fun notesListContent(
    notes: List<NoteItemUi>,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onNoteClick: (NoteItemUi) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
    ) {
        searchField()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(notes) { note ->
                noteCard(
                    note = note,
                    onClick = { onNoteClick(note) },
                )
            }
        }
    }
}

@Composable
private fun noteCard(
    note: NoteItemUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = note.title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = note.content,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun searchField() {
    val colors = MaterialTheme.colorScheme

    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(24.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            Text(
                text = "Hinted search text",
                color = colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
            )
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
