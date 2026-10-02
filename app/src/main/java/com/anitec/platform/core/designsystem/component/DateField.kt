package com.anitec.platform.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.anitec.platform.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Read-only date field with a calendar picker; ISO value (yyyy-MM-dd). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    AniTecTextField(
        value = value,
        onValueChange = {},
        label = label,
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        error = error,
        placeholder = "yyyy-mm-dd",
        trailingIcon = {
            if (clearable && value.isNotBlank()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_clear_date))
                }
            } else {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.common_choose_date))
                }
            }
        },
    )
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}
