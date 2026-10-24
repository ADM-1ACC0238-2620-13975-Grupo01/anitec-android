package com.anitec.platform.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The English and es-419 resources must stay in step: every key exists in both, with the same placeholders. */
class StringResourcesTest {

    private val resDir = File("src/main/res")

    private fun strings(dir: String): Map<String, String> {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return File(resDir, dir).listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }.orEmpty()
            .flatMap { file ->
                val nodes = builder.parse(file).getElementsByTagName("string")
                (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
            }
            .toMap()
    }

    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    @Test
    fun `both languages define the same keys`() {
        val english = strings("values")
        val spanish = strings("values-b+es+419")
        assertTrue("English strings were not found", english.isNotEmpty())
        assertEquals("Missing in es-419", emptySet<String>(), english.keys - spanish.keys)
        assertEquals("Missing in English", emptySet<String>(), spanish.keys - english.keys)
    }

    @Test
    fun `translations keep the placeholders of the original`() {
        val english = strings("values")
        val spanish = strings("values-b+es+419")
        val mismatched = english.filter { (key, text) ->
            spanish[key]?.let { placeholder.findAll(it).map { m -> m.value }.sorted().toList() != placeholder.findAll(text).map { m -> m.value }.sorted().toList() } == true
        }.keys
        assertEquals(emptySet<String>(), mismatched)
    }
}
