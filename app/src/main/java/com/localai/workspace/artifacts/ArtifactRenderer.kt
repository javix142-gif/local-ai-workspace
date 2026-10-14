package com.localai.workspace.artifacts

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal standards-based packages generated from data only. No imported package is rewritten.
 * XLSX caches are supplied values, never evaluated. PDF is controlled regeneration, not PDF editing.
 */
class ArtifactRenderer(private val context:Context) {
    fun render(spec:ArtifactSpec,target:File) {
        check(!target.exists()){"TARGET_ALREADY_EXISTS"}
        when(spec.format) {
            ArtifactFormat.CODE->target.writeText(spec.json().getString("body"),Charsets.UTF_8)
            ArtifactFormat.PDF->pdf(spec,target)
            else->ZipOutputStream(target.outputStream()).use { zip->
                val parts=when(spec.format){ArtifactFormat.XLSX->xlsx(spec);ArtifactFormat.DOCX->docx(spec);else->pptx(spec)}
                parts.forEach{(name,body)->checkInterrupted();zip.putNextEntry(ZipEntry(name).apply{time=0});zip.write(body.toByteArray(Charsets.UTF_8));zip.closeEntry()}
            }
        }
        require(target.length() in (if(spec.format==ArtifactFormat.CODE)0L else 1L)..20L*1024*1024){"ARTIFACT_BYTES_LIMIT"}
    }
    private fun checkInterrupted(){if(Thread.currentThread().isInterrupted)throw InterruptedException()}
    private fun xml(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    private fun declaration(body:String)="<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"+body
    private val relNS="http://schemas.openxmlformats.org/package/2006/relationships"
    private val officeRel="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private fun rels(vararg values:Triple<String,String,String>)=declaration("<Relationships xmlns=\"$relNS\">"+values.joinToString(""){(id,type,path)->"<Relationship Id=\"$id\" Type=\"$officeRel/$type\" Target=\"${xml(path)}\"/>"}+"</Relationships>")
    private fun types(vararg parts:Pair<String,String>)=declaration("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"+parts.joinToString(""){(name,type)->"<Override PartName=\"/$name\" ContentType=\"$type\"/>"}+"</Types>")

    private fun xlsx(spec:ArtifactSpec):Map<String,String> {
        val sheets=spec.json().getJSONArray("sheets").objects();val ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val out=linkedMapOf(
            "[Content_Types].xml" to types("xl/workbook.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml","xl/styles.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",*sheets.indices.map{"xl/worksheets/sheet${it+1}.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"}.toTypedArray()),
            "_rels/.rels" to rels(Triple("rId1","officeDocument","xl/workbook.xml")),
            "xl/workbook.xml" to declaration("<workbook xmlns=\"$ns\" xmlns:r=\"$officeRel\"><sheets>"+sheets.mapIndexed{i,s->"<sheet name=\"${xml(s.getString("name"))}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")+"</sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>"),
            "xl/_rels/workbook.xml.rels" to rels(*(sheets.indices.map{Triple("rId${it+1}","worksheet","worksheets/sheet${it+1}.xml")}+Triple("styles","styles","styles.xml")).toTypedArray()),
            "xl/styles.xml" to declaration("<styleSheet xmlns=\"$ns\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs><cellXfs count=\"2\"><xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        )
        sheets.forEachIndexed{i,sheet->
            val rows=sheet.getJSONArray("cells").objects().sortedWith(compareBy<JSONObject>{ArtifactSpec.address(it.getString("address")).first}.thenBy{ArtifactSpec.address(it.getString("address")).second}).groupBy{ArtifactSpec.address(it.getString("address")).first}
            out["xl/worksheets/sheet${i+1}.xml"]=declaration("<worksheet xmlns=\"$ns\"><sheetData>"+rows.entries.joinToString(""){(row,cells)->"<row r=\"$row\">"+cells.joinToString(""){c->
                val type=c.optString("type","string");val formula=c.optString("formula","");val value=c.optString("value","");val style=if(c.optString("style")=="header")1 else 0
                val body=if(formula.isNotBlank())"<f>${xml(formula)}</f>"+(if(c.has("value"))"<v>${xml(value)}</v>"else "")else if(type=="string")"<is><t xml:space=\"preserve\">${xml(value)}</t></is>"else "<v>${xml(value)}</v>"
                val t=if(type=="boolean")"b"else if(type=="number")"n"else if(formula.isNotBlank())"str"else "inlineStr"
                "<c r=\"${c.getString("address")}\" s=\"$style\" t=\"$t\">$body</c>"
            }+"</row>"}+"</sheetData></worksheet>")
        };return out
    }
    private fun docx(spec:ArtifactSpec):Map<String,String> {
        val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        fun runs(s:String)=s.split('\n').mapIndexed{i,line->(if(i>0)"<w:r><w:br/></w:r>"else "")+"<w:r><w:t xml:space=\"preserve\">${xml(line)}</w:t></w:r>"}.joinToString("")
        val paragraphs=spec.json().getJSONArray("paragraphs").objects().joinToString(""){p->
            val style=p.optString("style","body");val props=when(style){"title"->"<w:pStyle w:val=\"Title\"/>";"heading"->"<w:pStyle w:val=\"Heading1\"/>";"bullet"->"<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>";"pageBreak"->"<w:pageBreakBefore/>";else->""}
            "<w:p><w:pPr>$props</w:pPr>${runs(p.getString("text"))}</w:p>"
        }
        val tables=spec.json().optJSONArray("tables")?.objects().orEmpty().joinToString(""){t->"<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>"+listOf("top","left","bottom","right","insideH","insideV").joinToString(""){"<w:$it w:val=\"single\" w:sz=\"4\"/>"}+"</w:tblBorders></w:tblPr><w:tblGrid>"+(0 until t.getJSONArray("rows").getJSONArray(0).length()).joinToString(""){"<w:gridCol w:w=\"${9026/t.getJSONArray("rows").getJSONArray(0).length()}\"/>"}+"</w:tblGrid>"+t.getJSONArray("rows").arrays().joinToString(""){row->"<w:tr>"+(0 until row.length()).joinToString(""){"<w:tc><w:p>${runs(row.getString(it))}</w:p></w:tc>"}+"</w:tr>"}+"</w:tbl>"}
        return linkedMapOf(
            "[Content_Types].xml" to types("word/document.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml","word/styles.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml","word/numbering.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"),
            "_rels/.rels" to rels(Triple("rId1","officeDocument","word/document.xml")),
            "word/document.xml" to declaration("<w:document xmlns:w=\"$ns\"><w:body>$paragraphs$tables<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"),
            "word/_rels/document.xml.rels" to rels(Triple("styles","styles","styles.xml"),Triple("numbering","numbering","numbering.xml")),
            "word/styles.xml" to declaration("<w:styles xmlns:w=\"$ns\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"24\"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style><w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:rPr><w:b/><w:sz w:val=\"40\"/></w:rPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style></w:styles>"),
            "word/numbering.xml" to declaration("<w:numbering xmlns:w=\"$ns\"><w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>")
        )
    }
    private fun pptx(spec:ArtifactSpec):Map<String,String> {
        val slides=spec.json().getJSONArray("slides").objects();val p="http://schemas.openxmlformats.org/presentationml/2006/main";val a="http://schemas.openxmlformats.org/drawingml/2006/main"
        val group="<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        fun box(id:Int,name:String,y:Long,height:Long,text:String,size:Int)="<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"548640\" y=\"$y\"/><a:ext cx=\"11094720\" cy=\"$height\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>"+text.split('\n').joinToString(""){"<a:p><a:r><a:rPr lang=\"es-ES\" sz=\"$size\"><a:solidFill><a:srgbClr val=\"1D2522\"/></a:solidFill><a:latin typeface=\"Arial\"/></a:rPr><a:t>${xml(it)}</a:t></a:r><a:endParaRPr lang=\"es-ES\"/></a:p>"}+"</p:txBody></p:sp>"
        val out=linkedMapOf(
            "[Content_Types].xml" to types("ppt/presentation.xml" to "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",*slides.indices.map{"ppt/slides/slide${it+1}.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"}.toTypedArray()),
            "_rels/.rels" to rels(Triple("rId1","officeDocument","ppt/presentation.xml")),
            "ppt/presentation.xml" to declaration("<p:presentation xmlns:p=\"$p\" xmlns:r=\"$officeRel\"><p:sldIdLst>"+slides.indices.joinToString(""){"<p:sldId id=\"${256+it}\" r:id=\"rId${it+1}\"/>"}+"</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>"),
            "ppt/_rels/presentation.xml.rels" to rels(*slides.indices.map{Triple("rId${it+1}","slide","slides/slide${it+1}.xml")}.toTypedArray())
        )
        slides.forEachIndexed{i,s->out["ppt/slides/slide${i+1}.xml"]=declaration("<p:sld xmlns:p=\"$p\" xmlns:a=\"$a\"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"F6F7F4\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>$group${box(2,"Title",365760,1005840,s.getString("title"),3200)}${box(3,"Body",1554480,4754880,s.getString("body"),2200)}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>")};return out
    }
    private fun pdf(spec:ArtifactSpec,target:File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { doc->
            var stream:PDPageContentStream?=null;var y=0f
            fun page(){stream?.close();require(doc.numberOfPages<100){"PDF_PAGE_LIMIT"};val p=PDPage();doc.addPage(p);stream=PDPageContentStream(doc,p);y=p.mediaBox.height-54}
            fun line(value:String,font:PDType1Font,size:Float){checkInterrupted();if(stream==null||y<54)page();stream!!.beginText();stream!!.setFont(font,size);stream!!.newLineAtOffset(54f,y);stream!!.showText(value);stream!!.endText();y-=size*1.4f}
            fun paragraph(text:String,bold:Boolean=false,size:Float=12f) {
                val font=if(bold)PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                for(raw in text.split('\n')) {
                    var part=""
                    for(cp in raw.codePoints().toArray()) {val s=String(Character.toChars(cp));require(cp!=9){"PDF_TAB_UNSUPPORTED_USE_SPACES"};val next=part+s
                        if(font.getStringWidth(next)/1000f*size>504f && part.isNotEmpty()){line(part,font,size);part=s}else part=next
                    }
                    line(part,font,size)
                };y-=6
            }
            try {
                spec.json().getJSONArray("paragraphs").objects().forEach{p->val style=p.optString("style","body");if(style=="pageBreak" && stream!=null)page();paragraph(p.getString("text"),style in setOf("title","heading"),if(style=="title")20f else if(style=="heading")16f else 12f)}
                spec.json().optJSONArray("tables")?.objects()?.forEach{t->t.getJSONArray("rows").arrays().forEach{row->paragraph((0 until row.length()).joinToString(" | "){row.getString(it)})}}
                stream?.close();stream=null;doc.save(target)
            }finally{stream?.close()}
        }
    }
}
