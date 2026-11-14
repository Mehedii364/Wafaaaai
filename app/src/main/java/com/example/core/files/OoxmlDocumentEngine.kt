package com.example.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class SpreadsheetSheet(
    val name: String,
    val rows: List<List<String>>
)

data class PresentationSlide(
    val title: String,
    val bullets: List<String>
)

data class GeneratedFileInfo(
    val file: File,
    val byteSize: Long,
    val previewSummary: String
)

/**
 * Genuine on-device document, spreadsheet, presentation, PDF, and project ZIP engine.
 * Generates valid ECMA-376 OOXML archives (.docx, .xlsx, .pptx) and native Android PdfDocument files
 * without external AWT dependencies that fail on Android minSdk 24.
 */
class OoxmlDocumentEngine(private val context: Context) {

    val artifactsDir: File
        get() = File(context.filesDir, "artifacts").apply { mkdirs() }

    val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    val exportsDir: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    fun sanitizeFileName(raw: String, ext: String): String {
        val base = raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "wafa_output_${System.currentTimeMillis()}" }
        return if (base.endsWith(".$ext", ignoreCase = true)) base else "$base.$ext"
    }

    // ==================== PDF ====================

    fun createPdf(title: String, bodyText: String, fileName: String): GeneratedFileInfo {
        val outFile = File(artifactsDir, sanitizeFileName(fileName, "pdf"))
        try {
            val pdf = PdfDocument()
            val pageWidth = 595 // A4 ~72dpi
            val pageHeight = 842
            val margin = 44f
            val maxTextWidth = pageWidth - margin * 2

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(12, 74, 110)
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 9f
            }

            val wrappedLines = wrapLines(bodyText, bodyPaint, maxTextWidth)
            var lineIndex = 0
            var pageNumber = 1

            while (lineIndex < wrappedLines.size || pageNumber == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdf.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                var y = margin + 12f

                if (pageNumber == 1) {
                    canvas.drawText(title.take(64), margin, y, titlePaint)
                    y += 14f
                    val divider = Paint().apply {
                        color = Color.rgb(0, 180, 216)
                        strokeWidth = 2f
                    }
                    canvas.drawLine(margin, y, pageWidth - margin, y, divider)
                    y += 22f
                }

                while (lineIndex < wrappedLines.size && y < pageHeight - margin - 20f) {
                    canvas.drawText(wrappedLines[lineIndex], margin, y, bodyPaint)
                    y += 16f
                    lineIndex++
                }

                canvas.drawText("WAFA AI Document Studio • Page $pageNumber", margin, pageHeight - 22f, footerPaint)
                pdf.finishPage(page)
                pageNumber++
            }

            FileOutputStream(outFile).use { fos ->
                pdf.writeTo(fos)
            }
            pdf.close()

            return GeneratedFileInfo(
                file = outFile,
                byteSize = outFile.length(),
                previewSummary = "PDF (${pageNumber - 1} page(s), ${outFile.length()} bytes)"
            )
        } catch (_: Throwable) {
            writeFallbackPdf14(outFile, title, bodyText)
            return GeneratedFileInfo(
                file = outFile,
                byteSize = outFile.length(),
                previewSummary = "PDF (1 page(s), ${outFile.length()} bytes)"
            )
        }
    }

    private fun writeFallbackPdf14(outFile: File, title: String, bodyText: String) {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        val lines = (listOf(title, "---") + bodyText.lines()).take(42)
        val contentStream = buildString {
            append("BT\n/F1 12 Tf\n50 780 Td\n15 TL\n")
            lines.forEachIndexed { idx, line ->
                if (idx == 0) append("(${esc(line)}) Tj\n")
                else append("T* (${esc(line)}) Tj\n")
            }
            append("ET\n")
        }
        val objs = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
            "4 0 obj\n<< /Length ${contentStream.toByteArray(StandardCharsets.US_ASCII).size} >>\nstream\n${contentStream}endstream\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        )
        val sb = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        for (o in objs) {
            offsets += sb.length
            sb.append(o)
        }
        val xrefStart = sb.length
        sb.append("xref\n0 ${objs.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { off -> sb.append(String.format("%010d 00000 n \n", off)) }
        sb.append("trailer\n<< /Size ${objs.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        outFile.writeText(sb.toString(), StandardCharsets.US_ASCII)
    }

    fun renderPdfFirstPagePreview(file: File): Bitmap? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // ==================== DOCX ====================

    fun createDocx(title: String, bodyText: String, fileName: String): GeneratedFileInfo {
        val outFile = File(artifactsDir, sanitizeFileName(fileName, "docx"))
        val paragraphs = bodyText.split("\n")

        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("<w:body>")
            // Title paragraph
            append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")
            append("<w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t>${escapeXml(title)}</w:t></w:r></w:p>")
            for (para in paragraphs) {
                val trimmed = para.trimEnd()
                if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
                    val heading = trimmed.trimStart('#', ' ')
                    append("<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>")
                    append("<w:r><w:rPr><w:b/><w:sz w:val=\"26\"/></w:rPr><w:t>${escapeXml(heading)}</w:t></w:r></w:p>")
                } else {
                    append("<w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(trimmed)}</w:t></w:r></w:p>")
                }
            }
            append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>")
            append("</w:body></w:document>")
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val wordRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
</w:styles>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypes)
            writeZipEntry(zip, "_rels/.rels", rootRels)
            writeZipEntry(zip, "word/_rels/document.xml.rels", wordRels)
            writeZipEntry(zip, "word/styles.xml", stylesXml)
            writeZipEntry(zip, "word/document.xml", docXml)
        }

        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "DOCX (${paragraphs.size} paragraphs, ${outFile.length()} bytes)"
        )
    }

    fun readDocxText(file: File): String {
        val sb = StringBuilder()
        ZipInputStream(FileInputStream(file)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(StandardCharsets.UTF_8)
                    val parser = Xml.newPullParser()
                    parser.setInput(xml.reader())
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.TEXT -> sb.append(parser.text)
                            XmlPullParser.END_TAG -> if (parser.name == "p") sb.append("\n")
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }
        return sb.toString().trim()
    }

    // ==================== XLSX ====================

    fun createXlsx(sheets: List<SpreadsheetSheet>, fileName: String): GeneratedFileInfo {
        val safeSheets = sheets.ifEmpty {
            listOf(SpreadsheetSheet("Sheet1", listOf(listOf("Column1", "Value"), listOf("Sample", "0"))))
        }
        val outFile = File(artifactsDir, sanitizeFileName(fileName, "xlsx"))

        val sharedStrings = mutableListOf<String>()
        val sharedIndex = mutableMapOf<String, Int>()
        fun internString(s: String): Int = sharedIndex.getOrPut(s) {
            sharedStrings.add(s)
            sharedStrings.lastIndex
        }

        val sheetXmlList = safeSheets.map { sheet ->
            buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                append("<sheetData>")
                sheet.rows.forEachIndexed { rIdx, row ->
                    val rowNum = rIdx + 1
                    append("<row r=\"$rowNum\">")
                    row.forEachIndexed { cIdx, rawVal ->
                        val ref = "${columnLetters(cIdx)}$rowNum"
                        val trimmed = rawVal.trim()
                        when {
                            trimmed.startsWith("=") && trimmed.length > 1 -> {
                                val formula = trimmed.removePrefix("=")
                                val evalVal = evaluateFormulaOrNull(formula, sheet.rows)
                                append("<c r=\"$ref\"><f>${escapeXml(formula)}</f>")
                                if (evalVal != null) append("<v>$evalVal</v>")
                                append("</c>")
                            }
                            trimmed.toDoubleOrNull() != null -> {
                                append("<c r=\"$ref\"><v>${trimmed.toDouble()}</v></c>")
                            }
                            else -> {
                                val idx = internString(rawVal)
                                append("<c r=\"$ref\" t=\"s\"><v>$idx</v></c>")
                            }
                        }
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
        }

        val sharedStringsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
            for (s in sharedStrings) {
                append("<si><t xml:space=\"preserve\">${escapeXml(s)}</t></si>")
            }
            append("</sst>")
        }

        val workbookXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            safeSheets.forEachIndexed { index, sheet ->
                val cleanName = sheet.name.replace(Regex("[\\[\\]:*?/\\\\]"), "").take(30).ifBlank { "Sheet${index + 1}" }
                append("""<sheet name="${escapeXml(cleanName)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
            }
            append("</sheets></workbook>")
        }

        val workbookRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            safeSheets.forEachIndexed { index, _ ->
                append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""")
            }
            append("""<Relationship Id="rId${safeSheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
            append("""<Relationship Id="rId${safeSheets.size + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
            append("</Relationships>")
        }

        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            safeSheets.forEachIndexed { index, _ ->
                append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
            append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            append("</Types>")
        }

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypes)
            writeZipEntry(zip, "_rels/.rels", rootRels)
            writeZipEntry(zip, "xl/workbook.xml", workbookXml)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
            writeZipEntry(zip, "xl/sharedStrings.xml", sharedStringsXml)
            writeZipEntry(zip, "xl/styles.xml", stylesXml)
            sheetXmlList.forEachIndexed { index, xml ->
                writeZipEntry(zip, "xl/worksheets/sheet${index + 1}.xml", xml)
            }
        }

        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "XLSX (${safeSheets.size} sheet(s), ${outFile.length()} bytes)"
        )
    }

    fun readXlsxSummary(file: File): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(file)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(bytes.toString(StandardCharsets.UTF_8).reader())
            var event = parser.eventType
            var inT = false
            val cur = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") { inT = true; cur.clear() }
                    XmlPullParser.TEXT -> if (inT) cur.append(parser.text)
                    XmlPullParser.END_TAG -> if (parser.name == "t") { inT = false; shared += cur.toString() }
                }
                event = parser.next()
            }
        }
        val out = StringBuilder()
        entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted().forEach { name ->
            out.append("--- $name ---\n")
            val xml = entries[name]!!.toString(StandardCharsets.UTF_8)
            val parser = Xml.newPullParser()
            parser.setInput(xml.reader())
            var event = parser.eventType
            var cellType = ""
            var inV = false
            var inF = false
            val vText = StringBuilder()
            val fText = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "c" -> cellType = parser.getAttributeValue(null, "t").orEmpty()
                        "v" -> { inV = true; vText.clear() }
                        "f" -> { inF = true; fText.clear() }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) vText.append(parser.text)
                        if (inF) fText.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v" -> inV = false
                        "f" -> inF = false
                        "c" -> {
                            val display = when {
                                fText.isNotEmpty() -> "=${fText}"
                                cellType == "s" -> shared.getOrElse(vText.toString().toIntOrNull() ?: -1) { "" }
                                else -> vText.toString()
                            }
                            out.append(display).append(" | ")
                            vText.clear()
                            fText.clear()
                        }
                        "row" -> out.append("\n")
                    }
                }
                event = parser.next()
            }
        }
        return out.toString().trim()
    }

    // ==================== PPTX ====================

    fun createPptx(slides: List<PresentationSlide>, deckTitle: String, fileName: String): GeneratedFileInfo {
        val safeSlides = slides.ifEmpty {
            listOf(PresentationSlide(deckTitle, listOf("Created with WAFA AI Presentation Studio")))
        }
        val outFile = File(artifactsDir, sanitizeFileName(fileName, "pptx"))

        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
            append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
            append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
            safeSlides.forEachIndexed { idx, _ ->
                append("""<Override PartName="/ppt/slides/slide${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
            }
            append("</Types>")
        }

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

        val presXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
            append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rIdMaster1"/></p:sldMasterIdLst>""")
            append("<p:sldIdLst>")
            safeSlides.forEachIndexed { idx, _ ->
                append("""<p:sldId id="${256 + idx}" r:id="rId${idx + 1}"/>""")
            }
            append("</p:sldIdLst>")
            append("""<p:sldSz cx="9144000" cy="6858000" type="screen4x3"/><p:notesSz cx="6858000" cy="9144000"/></p:presentation>""")
        }

        val presRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            append("""<Relationship Id="rIdMaster1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
            safeSlides.forEachIndexed { idx, _ ->
                append("""<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${idx + 1}.xml"/>""")
            }
            append("</Relationships>")
        }

        val slideMaster = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst>
</p:sldMaster>"""

        val masterRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""

        val slideLayout = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="titleAndContent">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
</p:sldLayout>"""

        val layoutRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""

        val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypes)
            writeZipEntry(zip, "_rels/.rels", rootRels)
            writeZipEntry(zip, "ppt/presentation.xml", presXml)
            writeZipEntry(zip, "ppt/_rels/presentation.xml.rels", presRels)
            writeZipEntry(zip, "ppt/slideMasters/slideMaster1.xml", slideMaster)
            writeZipEntry(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", masterRels)
            writeZipEntry(zip, "ppt/slideLayouts/slideLayout1.xml", slideLayout)
            writeZipEntry(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", layoutRels)

            safeSlides.forEachIndexed { idx, slide ->
                val num = idx + 1
                writeZipEntry(zip, "ppt/slides/_rels/slide$num.xml.rels", slideRels)
                writeZipEntry(zip, "ppt/slides/slide$num.xml", buildSlideXml(slide))
            }
        }

        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "PPTX (${safeSlides.size} slides, ${outFile.length()} bytes)"
        )
    }

    private fun buildSlideXml(slide: PresentationSlide): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
        append("<p:cSld><p:spTree>")
        append("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>")
        // Title Shape
        append("<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Title\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>")
        append("<p:spPr><a:xfrm><a:off x=\"457200\" y=\"365125\"/><a:ext cx=\"8229600\" cy=\"1143000\"/></a:xfrm><a:prstGeom prst=\"rect\"/></p:spPr>")
        append("<p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\" sz=\"3200\" b=\"1\"/><a:t>${escapeXml(slide.title)}</a:t></a:r></a:p></p:txBody></p:sp>")
        // Body Shape
        append("<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Content\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>")
        append("<p:spPr><a:xfrm><a:off x=\"457200\" y=\"1600200\"/><a:ext cx=\"8229600\" cy=\"4525962\"/></a:xfrm><a:prstGeom prst=\"rect\"/></p:spPr>")
        append("<p:txBody><a:bodyPr/><a:lstStyle/>")
        slide.bullets.forEach { bullet ->
            append("<a:p><a:r><a:rPr lang=\"en-US\" sz=\"2000\"/><a:t>• ${escapeXml(bullet)}</a:t></a:r></a:p>")
        }
        append("</p:txBody></p:sp>")
        append("</p:spTree></p:cSld></p:sld>")
    }

    // ==================== PROJECT ZIP & PLAIN FILES ====================

    fun createProjectZip(filesMap: Map<String, String>, zipFileName: String): GeneratedFileInfo {
        val outFile = File(exportsDir, sanitizeFileName(zipFileName, "zip"))
        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            filesMap.forEach { (relativePath, content) ->
                val safeEntry = relativePath.trimStart('/').replace("..", "")
                if (safeEntry.isNotBlank()) {
                    writeZipEntry(zip, safeEntry, content)
                }
            }
        }
        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "ZIP (${filesMap.size} files, ${outFile.length()} bytes)"
        )
    }

    fun writePlainFile(content: String, fileName: String, ext: String): GeneratedFileInfo {
        val outFile = File(artifactsDir, sanitizeFileName(fileName, ext))
        outFile.writeText(content, StandardCharsets.UTF_8)
        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "${ext.uppercase()} (${outFile.length()} bytes)"
        )
    }

    fun saveImageBytes(bytes: ByteArray, fileName: String): GeneratedFileInfo {
        val outFile = File(imagesDir, sanitizeFileName(fileName, "png"))
        outFile.writeBytes(bytes)
        return GeneratedFileInfo(
            file = outFile,
            byteSize = outFile.length(),
            previewSummary = "PNG (${outFile.length()} bytes)"
        )
    }

    // ==================== SAF & EVALUATION ====================

    fun readFileMetaAndPreview(uri: Uri, maxBytes: Long = 25L * 1024L * 1024L): Triple<String, Long, String> {
        var name = "selected_file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nIdx >= 0) name = cursor.getString(nIdx) ?: name
                if (sIdx >= 0) size = cursor.getLong(sIdx)
            }
        }
        if (size > maxBytes) {
            return Triple(name, size, "File exceeds 25MB safe memory limit.")
        }
        val mime = context.contentResolver.getType(uri).orEmpty()
        val preview = try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(64 * 1024) }
                ?: ByteArray(0)
            when {
                name.endsWith(".docx", true) -> {
                    val tmp = File(context.cacheDir, "tmp_read.docx").apply { writeBytes(bytes) }
                    readDocxText(tmp).take(4000)
                }
                name.endsWith(".xlsx", true) -> {
                    val tmp = File(context.cacheDir, "tmp_read.xlsx").apply { writeBytes(bytes) }
                    readXlsxSummary(tmp).take(4000)
                }
                mime.startsWith("text/") || name.endsWith(".md", true) || name.endsWith(".csv", true) ||
                    name.endsWith(".json", true) || name.endsWith(".xml", true) || name.endsWith(".kt", true) ||
                    name.endsWith(".py", true) || name.endsWith(".js", true) || name.endsWith(".html", true) -> {
                    bytes.toString(StandardCharsets.UTF_8).take(8000)
                }
                else -> "Binary attachment ($mime, ${bytes.size} bytes loaded for inspection)"
            }
        } catch (t: Throwable) {
            "Could not extract preview: ${t.message}"
        }
        return Triple(name, size, preview)
    }

    fun evaluateFormulaOrNull(formulaRaw: String, rows: List<List<String>>): Double? {
        val f = formulaRaw.trim().removePrefix("=").uppercase()
        val regex = Regex("^(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\\(([A-Z]+\\d+):([A-Z]+\\d+)\\)$")
        val match = regex.matchEntire(f) ?: return null
        val fn = match.groupValues[1]
        val start = parseCellRef(match.groupValues[2]) ?: return null
        val end = parseCellRef(match.groupValues[3]) ?: return null

        val vals = mutableListOf<Double>()
        for (r in minOf(start.first, end.first)..maxOf(start.first, end.first)) {
            for (c in minOf(start.second, end.second)..maxOf(start.second, end.second)) {
                val cell = rows.getOrNull(r)?.getOrNull(c)?.trim()
                cell?.toDoubleOrNull()?.let { vals += it }
            }
        }
        if (vals.isEmpty()) return 0.0
        return when (fn) {
            "SUM" -> vals.sum()
            "AVERAGE", "AVG" -> vals.average()
            "MIN" -> vals.min()
            "MAX" -> vals.max()
            "COUNT" -> vals.size.toDouble()
            else -> null
        }
    }

    private fun parseCellRef(ref: String): Pair<Int, Int>? {
        val m = Regex("^([A-Z]+)(\\d+)$").matchEntire(ref) ?: return null
        val colStr = m.groupValues[1]
        val rowNum = m.groupValues[2].toIntOrNull() ?: return null
        var col = 0
        for (ch in colStr) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return (rowNum - 1) to (col - 1)
    }

    private fun columnLetters(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = (i / 26) - 1
        }
        return sb.toString()
    }

    private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                out += ""
                continue
            }
            val words = paragraph.split(" ")
            val line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                } else {
                    if (line.isNotEmpty()) out += line.toString()
                    line.clear()
                    line.append(word)
                }
            }
            if (line.isNotEmpty()) out += line.toString()
        }
        return out
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
