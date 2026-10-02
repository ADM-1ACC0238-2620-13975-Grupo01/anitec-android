package com.anitec.platform.livestock.interfaces.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.DateField
import com.anitec.platform.core.designsystem.component.DropdownField
import com.anitec.platform.core.designsystem.component.FormHero
import com.anitec.platform.core.designsystem.component.FormScreenScaffold
import com.anitec.platform.core.designsystem.component.MessageEffect
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import com.anitec.platform.core.network.resolveMediaUrl
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.interfaces.viewmodel.AnimalFormUiState
import com.anitec.platform.livestock.interfaces.viewmodel.AnimalFormViewModel
import com.anitec.platform.livestock.interfaces.viewmodel.RegistrationMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimalFormScreen(
    onBack: () -> Unit,
    onCreateCorral: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimalFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    MessageEffect(state.messageRes, snackbarHostState, viewModel::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onBack() }

    val required = stringResource(R.string.error_field_required)

    FormScreenScaffold(
        title = stringResource(if (state.isEdit) R.string.animal_form_edit else R.string.animal_form_new),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) {
        FormHero(
            icon = Icons.Filled.Pets,
            title = stringResource(if (state.isEdit) R.string.animal_form_edit else R.string.animal_form_new),
            chip = stringResource(R.string.nav_animals),
            subtitle = stringResource(R.string.animal_form_subtitle),
        )

        if (!state.isEdit) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = RegistrationMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(if (mode == RegistrationMode.Individual) R.string.animal_mode_individual else R.string.animal_mode_bulk))
                    }
                }
            }
        }

        if (!state.bulk) {
            AniTecTextField(
                value = state.tag, onValueChange = viewModel::onTagChange, label = stringResource(R.string.animal_tag),
                modifier = Modifier.fillMaxWidth(), error = if (state.tagMissing) required else null,
            )
            AniTecTextField(
                value = state.name, onValueChange = viewModel::onNameChange, label = stringResource(R.string.animal_name),
                modifier = Modifier.fillMaxWidth(), error = if (state.nameMissing) required else null,
            )
        }

        DropdownField(
            label = stringResource(R.string.animal_herd),
            options = state.herds.map { it.id },
            selected = state.herdId,
            onSelected = viewModel::onHerdChange,
            optionLabel = { id -> state.herds.firstOrNull { it.id == id }?.name.orEmpty() },
            error = if (state.herdMissing) required else null,
        )
        DropdownField(
            label = stringResource(R.string.animal_corral),
            options = state.corralsOfHerd.map { it.id },
            selected = state.corralId,
            onSelected = viewModel::onCorralChange,
            optionLabel = { id -> state.allCorrals.firstOrNull { it.id == id }?.name.orEmpty() },
            error = if (state.corralMissing) required else null,
            placeholder = stringResource(R.string.animal_select),
        )
        if (state.corralsOfHerd.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.animal_no_corrals_for_herd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCreateCorral) {
                    Text(stringResource(R.string.animal_create_corral), color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        OptionDropdown(R.string.animal_species, LivestockOptions.species, state.species, viewModel::onSpeciesChange)
        AniTecTextField(
            value = state.breed, onValueChange = viewModel::onBreedChange, label = stringResource(R.string.animal_breed),
            modifier = Modifier.fillMaxWidth(), error = if (state.breedMissing) required else null,
        )
        OptionDropdown(R.string.animal_gender, LivestockOptions.gender, state.gender, viewModel::onGenderChange)
        StatusDropdown(state.status, viewModel::onStatusChange)
        DateField(
            value = state.birthDate,
            onChange = viewModel::onBirthDateChange,
            label = stringResource(R.string.animal_birth_date),
            clearable = true,
        )
        AniTecTextField(
            value = state.weight, onValueChange = viewModel::onWeightChange, label = stringResource(R.string.animal_weight),
            modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal,
            error = if (state.weightInvalid) stringResource(R.string.animal_invalid_weight) else null,
        )
        if (state.bulk) {
            AniTecTextField(
                value = state.quantity, onValueChange = viewModel::onQuantityChange, label = stringResource(R.string.animal_quantity),
                modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number,
                error = if (state.quantityInvalid) stringResource(R.string.animal_error_batch_size) else null,
            )
            Text(stringResource(R.string.animal_bulk_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NullableOptionDropdown(R.string.animal_source, LivestockOptions.source, state.source, viewModel::onSourceChange)
        NullableOptionDropdown(R.string.animal_age_range, LivestockOptions.ageRange, state.ageRange, viewModel::onAgeRangeChange)

        PhotoSection(
            state = state,
            onPicked = viewModel::onPhotoPicked,
            onRemoved = viewModel::onPhotoRemoved,
            onCameraDenied = viewModel::onCameraDenied,
            onCameraUnavailable = viewModel::onCameraUnavailable,
        )
        if (state.bulk) {
            Text(stringResource(R.string.animal_bulk_photo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.serverMessages.isNotEmpty()) {
            Column {
                state.serverMessages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }

        PrimaryButton(
            text = stringResource(R.string.common_save),
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            loading = state.saving,
            enabled = !state.uploading,
        )
        SecondaryButton(text = stringResource(R.string.common_cancel), onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

/** Dropdown over a fixed list of API values; a stored value the app does not know is kept as an extra choice. */
@Composable
private fun OptionDropdown(label: Int, options: List<ApiOption>, selected: String, onSelected: (String) -> Unit) {
    val values = options.map { it.value }.let { if (selected.isNotBlank() && it.none { v -> v.equals(selected, true) }) it + selected else it }
    DropdownField(
        label = stringResource(label),
        options = values,
        selected = selected.takeIf { it.isNotBlank() },
        onSelected = onSelected,
        optionLabel = { options.labelFor(it) },
    )
}

@Composable
private fun NullableOptionDropdown(label: Int, options: List<ApiOption>, selected: String?, onSelected: (String?) -> Unit) {
    val values: List<String?> = listOf<String?>(null) + options.map { it.value }
        .let { if (selected != null && it.none { v -> v.equals(selected, true) }) it + selected else it }
    DropdownField(
        label = stringResource(label),
        options = values,
        selected = selected,
        onSelected = onSelected,
        optionLabel = { if (it == null) stringResource(R.string.animal_select) else options.labelFor(it) },
        placeholder = stringResource(R.string.animal_select),
    )
}

@Composable
private fun StatusDropdown(selected: String, onSelected: (String) -> Unit) {
    val values = AnimalStatus.selectable.map { it.apiValue }
        .let { if (selected.isNotBlank() && it.none { v -> v.equals(selected, true) }) it + selected else it }
    DropdownField(
        label = stringResource(R.string.animal_status),
        options = values,
        selected = selected.takeIf { it.isNotBlank() },
        onSelected = onSelected,
        optionLabel = { statusLabel(it) },
    )
}

/**
 * Photo of the animal. The camera is a device resource that needs the CAMERA permission: it is requested
 * only when the user taps "Take photo", a denial is reported without blocking the form, and the gallery
 * (which needs no permission) always remains available.
 */
@Composable
private fun PhotoSection(
    state: AnimalFormUiState,
    onPicked: (String) -> Unit,
    onRemoved: () -> Unit,
    onCameraDenied: () -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) captureUri?.let { onPicked(it.toString()) }
    }
    val launchCamera = {
        val directory = File(context.cacheDir, "images").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(directory, "capture_${System.currentTimeMillis()}.jpg"))
        captureUri = uri
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onCameraDenied()
    }
    val pickFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.animal_photo), style = MaterialTheme.typography.labelLarge)

        val preview = state.localPhotoUri ?: resolveMediaUrl(state.imageUrl)
        if (preview != null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = preview, contentDescription = stringResource(R.string.animal_photo), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
                if (state.uploading) {
                    Row(
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.animal_photo_uploading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                text = stringResource(R.string.animal_photo_take),
                icon = Icons.Filled.AddAPhoto,
                modifier = Modifier.weight(1f),
                onClick = {
                    when {
                        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> onCameraUnavailable()
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> launchCamera()
                        else -> requestCamera.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            SecondaryButton(
                text = stringResource(R.string.animal_photo_choose),
                icon = Icons.Filled.PhotoLibrary,
                modifier = Modifier.weight(1f),
                onClick = { pickFromGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
        if (preview != null && !state.uploading) {
            TextButton(onClick = onRemoved) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Text(stringResource(R.string.animal_photo_remove), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
