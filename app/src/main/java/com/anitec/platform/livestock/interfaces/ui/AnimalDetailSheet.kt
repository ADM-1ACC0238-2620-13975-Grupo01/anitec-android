package com.anitec.platform.livestock.interfaces.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.designsystem.component.StatusTag
import com.anitec.platform.core.network.resolveMediaUrl
import com.anitec.platform.livestock.domain.AnimalView

/** The animal's technical record: photo and every field, with edit/delete for ranchers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalDetailSheet(
    view: AnimalView,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val animal = view.animal
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.animals_detail_title), style = MaterialTheme.typography.titleLarge)

            val url = resolveMediaUrl(animal.imageUrl)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                val noPhoto: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Pets, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text(stringResource(R.string.animals_no_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (url != null) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = animal.name,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Crop,
                        loading = { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) { CircularProgressIndicator() } },
                        error = { Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) { noPhoto() } },
                    )
                } else {
                    noPhoto()
                }
            }

            DetailRow(stringResource(R.string.animal_tag)) { Text(animal.tag) }
            DetailRow(stringResource(R.string.animal_name)) { Text(animal.name) }
            DetailRow(stringResource(R.string.animal_species)) { Text(LivestockOptions.species.labelFor(animal.species)) }
            DetailRow(stringResource(R.string.animal_breed)) { Text(animal.breed) }
            DetailRow(stringResource(R.string.animal_gender)) { Text(LivestockOptions.gender.labelFor(animal.gender)) }
            DetailRow(stringResource(R.string.animal_birth_date)) { Text(animal.birthDate ?: stringResource(R.string.animal_no_birth_date)) }
            DetailRow(stringResource(R.string.animal_weight)) { Text("${animal.weight} kg") }
            DetailRow(stringResource(R.string.animal_status)) { StatusTag(statusLabel(animal.status), animal.healthStatus.severity()) }
            DetailRow(stringResource(R.string.animal_herd)) { Text(view.herdName) }
            DetailRow(stringResource(R.string.animal_corral)) { Text(view.corralName ?: stringResource(R.string.animal_no_corral)) }
            DetailRow(stringResource(R.string.animal_source)) { Text(animal.source?.let { LivestockOptions.source.labelFor(it) } ?: "-") }
            DetailRow(stringResource(R.string.animal_age_range)) { Text(animal.ageRange?.let { LivestockOptions.ageRange.labelFor(it) } ?: "-") }

            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton(
                        text = stringResource(R.string.animals_edit),
                        onClick = onEdit,
                        icon = Icons.Filled.Edit,
                        modifier = Modifier.weight(1f),
                    )
                    DangerTextButton(
                        text = stringResource(R.string.common_delete),
                        onClick = onDelete,
                        icon = Icons.Filled.Delete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.weight(0.6f)) { value() }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
