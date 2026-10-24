package com.anitec.platform.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Marks a title as a heading, so screen-reader users can jump from section to section. */
fun Modifier.sectionHeading(): Modifier = semantics { heading() }

/**
 * Makes a screen reader announce this node's text when it appears or changes, without moving focus.
 * Use [assertive] only for errors; it interrupts whatever is being read.
 */
fun Modifier.liveStatus(assertive: Boolean = false): Modifier = semantics(mergeDescendants = true) {
    liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
}

/** Reads the children of a row or card as one item, e.g. a label and its value. */
fun Modifier.mergedSemantics(): Modifier = semantics(mergeDescendants = true) {}
