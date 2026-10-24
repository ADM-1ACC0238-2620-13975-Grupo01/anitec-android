package com.anitec.platform.livestock

import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.BatchNaming
import com.anitec.platform.livestock.infrastructure.toDto
import com.anitec.platform.livestock.interfaces.viewmodel.AnimalFormUiState
import com.anitec.platform.livestock.interfaces.viewmodel.RegistrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchNamingTest {

    @Test
    fun `a name is the base, a dash and a three digit number`() {
        assertEquals("polloComercio-001", BatchNaming.nameFor("polloComercio", 1))
        assertEquals("polloComercio-010", BatchNaming.nameFor("polloComercio", 10))
        assertEquals("polloComercio-500", BatchNaming.nameFor("polloComercio", 500))
        assertEquals("pato-007", BatchNaming.nameFor("  pato ", 7))
    }

    @Test
    fun `the batch draft sends its base name and leaves it out when there is none`() {
        fun draft(name: String?) = AnimalBatchDraft(
            species = "Pollo", breed = "Cobb 500", gender = "Mixto", birthDate = null, weight = 3.0, status = "Saludable",
            herdId = 1, corralId = 2, quantity = 10, source = null, ageRange = null, imageUrl = null, name = name,
        )

        assertEquals("polloComercio", draft("polloComercio").toDto().name)
        assertNull(draft(null).toDto().name)
    }

    @Test
    fun `the form previews the first and last name of the batch`() {
        val form = AnimalFormUiState(mode = RegistrationMode.Bulk, name = " polloComercio ", quantity = "10")

        assertEquals("polloComercio-001" to "polloComercio-010", form.batchNamePreview)
    }

    @Test
    fun `there is no preview without a name, with an invalid quantity or for a single animal`() {
        assertNull(AnimalFormUiState(mode = RegistrationMode.Bulk, name = "", quantity = "10").batchNamePreview)
        assertNull(AnimalFormUiState(mode = RegistrationMode.Bulk, name = "pollo", quantity = "0").batchNamePreview)
        assertNull(AnimalFormUiState(mode = RegistrationMode.Bulk, name = "pollo", quantity = "501").batchNamePreview)
        assertNull(AnimalFormUiState(mode = RegistrationMode.Individual, name = "pollo", quantity = "10").batchNamePreview)
    }

    @Test
    fun `a batch of one previews a single name`() {
        val form = AnimalFormUiState(mode = RegistrationMode.Bulk, name = "pato", quantity = "1")

        assertEquals("pato-001" to "pato-001", form.batchNamePreview)
    }
}
