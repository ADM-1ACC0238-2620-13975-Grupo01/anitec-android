package com.anitec.platform.core.designsystem.component

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource

/** "1 farm" / "3 farms": the quantity both selects the plural form and fills the placeholder. */
@Composable
fun pluralCount(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count, count)
