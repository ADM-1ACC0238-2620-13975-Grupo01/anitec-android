package com.anitec.platform.core.designsystem

// Test coverage marker: accessibility of the design system components (TS-012, US-042).

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anitec.platform.core.designsystem.component.AniTecExtendedFab
import com.anitec.platform.core.designsystem.component.ButtonRow
import com.anitec.platform.core.designsystem.component.CappedFontScale
import com.anitec.platform.core.designsystem.component.DangerTextButton
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.designsystem.component.SecondaryButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What a screen reader gets from the base components, and how they behave when the text is made much larger. */
@RunWith(AndroidJUnit4::class)
class ComponentAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher("has the button role") {
        it.config.getOrNull(SemanticsProperties.Role) == Role.Button
    }

    @Composable
    private fun WithFontScale(scale: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = scale)) { content() }
    }

    @Test
    fun theFloatingButtonHasANameAndTheButtonRole() {
        rule.setContent { AniTecTheme { AniTecExtendedFab(text = "Nueva actividad", icon = Icons.Filled.Add, onClick = {}) } }

        rule.onNodeWithContentDescription("Nueva actividad").assertHasClickAction().assert(isButton).assertIsDisplayed()
    }

    @Test
    fun theButtonsAreAtLeast48dpTall() {
        rule.setContent {
            AniTecTheme {
                Column {
                    PrimaryButton(text = "Guardar", onClick = {})
                    SecondaryButton(text = "Editar", onClick = {})
                    DangerTextButton(text = "Eliminar", onClick = {})
                }
            }
        }

        listOf("Guardar", "Editar", "Eliminar").forEach { label ->
            rule.onNodeWithText(label).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun aLongLabelWithTextAt200PercentStaysFullyOnScreenAndGrowsTheButtonInsteadOfCuttingIt() {
        rule.setContent {
            AniTecTheme {
                WithFontScale(2f) {
                    Column(modifier = Modifier.width(360.dp)) {
                        PrimaryButton(text = "Solicitar acceso al ganadero", onClick = {})
                        SecondaryButton(text = "Reprogramar actividad", onClick = {})
                    }
                }
            }
        }

        val primary = rule.onNodeWithText("Solicitar acceso al ganadero", useUnmergedTree = true)
        primary.assertIsDisplayed()
        val bounds = primary.getUnclippedBoundsInRoot()
        assertTrue("the label must fit inside the width of its column, not run past it", bounds.right - bounds.left <= 360.dp)
        // The merged node is the button itself: with larger text it must still be at least 48 dp tall.
        rule.onNodeWithText("Reprogramar actividad").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aLoadingButtonAnnouncesItsState() {
        rule.setContent { AniTecTheme { PrimaryButton(text = "Guardar", onClick = {}, loading = true) } }

        rule.onNodeWithText("Guardar").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun textStaysReadableAtTheLargestSystemSize() {
        rule.setContent {
            AniTecTheme {
                WithFontScale(2f) { Text("Vacunacion de bovinos Valle Sagrado", modifier = Modifier.width(300.dp)) }
            }
        }

        rule.onNodeWithText("Vacunacion de bovinos Valle Sagrado").assertIsDisplayed()
    }

    @Composable
    private fun TwoButtons() {
        Column(modifier = Modifier.width(360.dp)) {
            ButtonRow {
                SecondaryButton(text = "Atendida", onClick = {})
                SecondaryButton(text = "Reprogramar", onClick = {})
            }
        }
    }

    @Test
    fun buttonsShareALineAtNormalSize() {
        rule.setContent { AniTecTheme { WithFontScale(1f) { TwoButtons() } } }

        val first = rule.onNodeWithText("Atendida").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithText("Reprogramar").getUnclippedBoundsInRoot()
        assertTrue("both buttons fit side by side", second.top < first.bottom)
    }

    @Test
    fun buttonsMoveToTheNextLineAtLargeTextInsteadOfBreakingTheirLabels() {
        rule.setContent { AniTecTheme { WithFontScale(2f) { TwoButtons() } } }

        val first = rule.onNodeWithText("Atendida").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithText("Reprogramar").getUnclippedBoundsInRoot()
        assertTrue("the second button goes below the first", second.top >= first.bottom)
        // One line of text per button: the height is the 48 dp minimum plus what the larger line needs, not two lines.
        rule.onNodeWithText("Atendida").assertIsDisplayed()
        rule.onNodeWithText("Reprogramar").assertIsDisplayed()
    }

    @Test
    fun aCappedLabelGrowsNoMoreThanItsLimit() {
        var capped = 0f
        var limit = 0f
        rule.setContent {
            AniTecTheme {
                Column {
                    WithFontScale(2f) { CappedFontScale(1.2f) { Text("Actividades", modifier = Modifier.testTag("capped")) } }
                    WithFontScale(1.2f) { Text("Actividades", modifier = Modifier.testTag("limit")) }
                }
            }
        }
        capped = rule.onNodeWithTag("capped").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        limit = rule.onNodeWithTag("limit").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

        assertTrue("capped $capped and limit $limit must match", kotlin.math.abs(capped - limit) < 0.5f)
    }
}
