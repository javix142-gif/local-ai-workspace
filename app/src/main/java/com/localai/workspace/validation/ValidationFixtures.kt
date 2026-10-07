package com.localai.workspace.validation

import android.content.Context
import android.graphics.*
import java.io.File
import java.nio.file.*
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class ValidationFixtures(val root: File) {
    fun file(name: String) = File(root,name).also { require(it.canonicalFile.parentFile==root.canonicalFile) }
    fun create(context: Context) {
        root.mkdirs()
        file("validation.txt").writeText("VALIDATION_MARKER: 12345\nSynthetic public self-test fixture.")
        file("validation.csv").writeText("name;value\n\"a;b\";12\nb;4\n")
        file("semantic_a.txt").writeText("Los flamencos adquieren su color rosado por pigmentos presentes en su dieta.")
        file("semantic_b.txt").writeText("La fotosíntesis permite que las plantas conviertan energía lumínica en energía química.")
        zip(file("validation.xlsx"),mapOf(
            "[Content_Types].xml" to "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'><Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/><Default Extension='xml' ContentType='application/xml'/><Override PartName='/xl/workbook.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'/><Override PartName='/xl/worksheets/sheet1.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml'/></Types>",
            "_rels/.rels" to "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='r1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='xl/workbook.xml'/></Relationships>",
            "xl/workbook.xml" to "<workbook xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet name='Sales' sheetId='1' r:id='r1'/></sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='r1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet' Target='worksheets/sheet1.xml'/></Relationships>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns='http://schemas.openxmlformats.org/spreadsheetml/2006/main'><sheetData><row r='1'><c r='A1' t='inlineStr'><is><t>Item</t></is></c><c r='B1' t='inlineStr'><is><t>Total</t></is></c></row><row r='2'><c r='A2' t='inlineStr'><is><t>Alpha</t></is></c><c r='B2'><v>12</v></c></row><row r='3'><c r='A3' t='inlineStr'><is><t>Beta</t></is></c><c r='B3'><v>4</v></c></row></sheetData></worksheet>"))
        zip(file("validation.zip"),mapOf("validation.txt" to file("validation.txt").readText(),"validation.csv" to file("validation.csv").readText()))
        ZipTraversalControl.create(file("traversal.zip"))
        file("invalid.png").writeText("Not an image. Synthetic invalid input.")
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG);paint.color=Color.rgb(0,60,120);canvas.drawRect(60f,50f,580f,430f,paint)
            paint.color=Color.WHITE;paint.textSize=230f;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)
            canvas.drawText("42",320f,320f,paint)
            file("vision_fixture.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        } finally { bitmap.recycle() }
        context.assets.open("device_validation/audio_fixture.wav").use { input -> file("audio_fixture.wav").outputStream().use { input.copyTo(it) } }
    }
    private fun zip(file: File, entries: Map<String,String>) = ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name,text) -> zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray());zip.closeEntry() } }
}
internal object ValidationResources {
    private val uuid=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    fun root(context: Context,id: String): File { require(uuid.matches(id));return File(context.filesDir,"device-validation/runs/"+id) }
    fun clean(context: Context,id: String) {
        require(uuid.matches(id))
        listOf(root(context,id),File(context.filesDir,"documents/self-test-"+id),File(context.filesDir,"attachments/self-test-"+id),File(context.filesDir,"audio-attachments/self-test-"+id)).forEach(::deleteTree)
    }
    fun recover(context: Context) {
        File(context.filesDir,"device-validation/runs").listFiles().orEmpty().filter { uuid.matches(it.name) }.forEach { clean(context,it.name) }
        // Also recover a preprocessing directory if death occurred before fixtures were created.
        listOf("documents","attachments","audio-attachments").forEach { name -> File(context.filesDir,name).listFiles().orEmpty().filter { it.name.startsWith("self-test-") && uuid.matches(it.name.removePrefix("self-test-")) }.forEach(::deleteTree) }
        deleteDatabase(context)
    }
    fun deleteDatabase(context:Context) {
        val name=com.localai.workspace.data.WorkspaceDatabase.VALIDATION_DATABASE
        val path=context.getDatabasePath(name)
        context.deleteDatabase(name)
        check(listOf(path,File(path.path+"-wal"),File(path.path+"-shm"),File(path.path+"-journal")).none { it.exists() }) { "Validation database cleanup failed" }
    }
    private fun deleteTree(file: File) {
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return
        Files.walkFileTree(file.toPath(),object:SimpleFileVisitor<Path>() {
            override fun visitFile(path:Path,attributes:BasicFileAttributes):FileVisitResult { Files.delete(path);return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(path:Path,error:java.io.IOException?):FileVisitResult { if(error!=null)throw error;Files.delete(path);return FileVisitResult.CONTINUE }
        }) // No FOLLOW_LINKS: a symlink is removed, never traversed.
    }
}
