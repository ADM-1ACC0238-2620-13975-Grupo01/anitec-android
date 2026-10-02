package com.anitec.platform.core.i18n

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.anitec.platform.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val languageManager: LanguageManager,
) : ViewModel() {
    fun current(): AppLanguage = languageManager.current()
    fun set(language: AppLanguage) = languageManager.set(language)
}

/**
 * EN / ES toggle. Changing the language recreates the activity, so the current value is read on each composition.
 * [onDark] switches to light chip colors for use over photos.
 */
@Composable
fun LanguageSwitcher(
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val current = viewModel.current()
    Row(modifier = modifier) {
        LanguageChip(stringResource(R.string.language_english), current == AppLanguage.English, onDark) {
            viewModel.set(AppLanguage.English)
        }
        Spacer(Modifier.width(8.dp))
        LanguageChip(stringResource(R.string.language_spanish), current == AppLanguage.Spanish, onDark) {
            viewModel.set(AppLanguage.Spanish)
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onDark: Boolean, onClick: () -> Unit) {
    val colors = if (onDark) {
        FilterChipDefaults.filterChipColors(
            containerColor = Color.Black.copy(alpha = 0.25f),
            labelColor = Color.White,
            selectedContainerColor = Color.White,
            selectedLabelColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = colors,
        border = if (onDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)) else FilterChipDefaults.filterChipBorder(true, selected),
    )
}
