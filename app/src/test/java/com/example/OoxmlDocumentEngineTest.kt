package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.files.OoxmlDocumentEngine
import com.example.core.files.PresentationSlide
import com.example.core.files.SpreadsheetSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OoxmlDocumentEngineTest {

    @Test
    fun `xlsx multi-sheet creation evaluates formulas and produces valid archive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OoxmlDocumentEngine(context)

        val sheet1 = SpreadsheetSheet(
            "Expenses",
            listOf(
                listOf("Item", "Amount"),
                listOf("Server", "40"),
                listOf("Domain", "15"),
                listOf("Total", "=SUM(B2:B3)")
            )
        )
        val sheet2 = SpreadsheetSheet("Meta", listOf(listOf("Owner", "WAFA AI")))

        val info = engine.createXlsx(listOf(sheet1, sheet2), "budget_test")
        assertTrue(info.file.exists())

        val entries = mutableSetOf<String>()
        ZipInputStream(FileInputStream(info.file)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries += e.name
                e = zip.nextEntry
            }
        }
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet2.xml"))

        val summary = engine.readXlsxSummary(info.file)
        assertTrue(summary.contains("=SUM(B2:B3)"))
        assertEquals(55.0, engine.evaluateFormulaOrNull("=SUM(B2:B3)", sheet1.rows)!!, 0.001)
        info.file.delete()
    }

    @Test
    fun `pptx creation produces valid slide deck zip entries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OoxmlDocumentEngine(context)

        val slides = listOf(
            PresentationSlide("Slide One", listOf("Alpha", "Beta")),
            PresentationSlide("Slide Two", listOf("Gamma"))
        )
        val info = engine.createPptx(slides, "Deck", "deck_test")
        assertTrue(info.file.exists())

        val entries = mutableSetOf<String>()
        ZipInputStream(FileInputStream(info.file)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries += e.name
                e = zip.nextEntry
            }
        }
        assertTrue(entries.contains("ppt/presentation.xml"))
        assertTrue(entries.contains("ppt/slides/slide1.xml"))
        assertTrue(entries.contains("ppt/slides/slide2.xml"))
        info.file.delete()
    }

    @Test
    fun `project zip packages multiple source files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OoxmlDocumentEngine(context)

        val zipInfo = engine.createProjectZip(
            mapOf("src/App.kt" to "fun main() {}", "README.md" to "# Hello"),
            "code_bundle"
        )
        assertTrue(zipInfo.file.exists())
        val entries = mutableSetOf<String>()
        ZipInputStream(FileInputStream(zipInfo.file)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                entries += e.name
                e = zip.nextEntry
            }
        }
        assertEquals(setOf("src/App.kt", "README.md"), entries)
        zipInfo.file.delete()
    }
}
