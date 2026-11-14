package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches WAFA AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WAFA AI", appName)
    }

    @Test
    fun `home screen renders and navigates to API Manager`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_screen_list").assertExists()
        composeRule.onNodeWithTag("nav_api_manager").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("api_providers_list").assertExists()
    }

    @Test
    fun `pdf and ooxml generation produces valid non-empty files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.core.files.OoxmlDocumentEngine(context)

        val pdfInfo = engine.createPdf("WAFA Report", "Line 1\nLine 2\nTesting Android PdfDocument", "test_report")
        assertTrue(pdfInfo.file.exists())
        assertTrue(pdfInfo.byteSize > 100L)
        val header = pdfInfo.file.readBytes().take(5).toByteArray().decodeToString()
        assertEquals("%PDF-", header)

        val docxInfo = engine.createDocx("Test Doc", "# Intro\nHello WAFA AI", "test_doc")
        assertTrue(docxInfo.file.exists())
        assertTrue(docxInfo.byteSize > 200L)
        val readBack = engine.readDocxText(docxInfo.file)
        assertTrue(readBack.contains("Hello WAFA AI"))
        File(docxInfo.file.absolutePath).delete()
        File(pdfInfo.file.absolutePath).delete()

        val qrBmp = com.example.core.tools.UtilityToolsEngine.generateQrBitmap("https://docs.xpiki.com/", 128)
        org.junit.Assert.assertNotNull(qrBmp)
        assertEquals(128, qrBmp!!.width)
    }
}
