package com.localai.workspace.artifacts

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal standards-based packages generated from data only. No imported package is rewritten.
 * XLSX caches are supplied values, never evaluated. PDF is controlled regeneration, not PDF editing.
 */
class ArtifactRenderer(private val context:Context) {
    private data class DocxTemplateStyle(val font:String,val color:String,val titleSize:Int,val headingSize:Int)
    fun render(spec:ArtifactSpec,target:File) {
        check(!target.exists()){"TARGET_ALREADY_EXISTS"}
        when(spec.format) {
            ArtifactFormat.CODE->target.writeText(spec.json().getString("body"),Charsets.UTF_8)
            ArtifactFormat.PDF->pdf(spec,target)
            else->ZipOutputStream(target.outputStream()).use { zip->
                val parts:Map<String,ByteArray> = when(spec.format){
                    ArtifactFormat.XLSX->xlsx(spec).utf8Parts()
                    ArtifactFormat.DOCX->docx(spec).utf8Parts()
                    ArtifactFormat.PPTX->pptx(spec)
                    else->error("ZIP_RENDER_FORMAT_UNSUPPORTED")
                }
                parts.forEach{(name,body)->checkInterrupted();zip.putNextEntry(ZipEntry(name).apply{time=0});zip.write(body);zip.closeEntry()}
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
    private fun typesWithPng(vararg parts:Pair<String,String>)=declaration("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/>"+parts.joinToString(""){(name,type)->"<Override PartName=\"/$name\" ContentType=\"$type\"/>"}+"</Types>")
    private fun Map<String,String>.utf8Parts()=mapValues{it.value.toByteArray(Charsets.UTF_8)}

    private fun xlsx(spec:ArtifactSpec):Map<String,String> {
        val descriptor=spec.json()
        val sheets=descriptor.getJSONArray("sheets").objects();val ns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val tables=ArtifactSpec.tables(descriptor).mapIndexed{index,table->table.copy(id=index+1)}
        val tablesBySheet=tables.groupBy{it.sheetIndex}
        val contentTypes=(sheets.indices.map{"xl/worksheets/sheet${it+1}.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"}+
            listOf("xl/workbook.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                "xl/styles.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml")+
            tables.map{"xl/tables/table${it.id}.xml" to "application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml"})
        val out=linkedMapOf(
            "[Content_Types].xml" to types(*contentTypes.toTypedArray()),
            "_rels/.rels" to rels(Triple("rId1","officeDocument","xl/workbook.xml")),
            "xl/workbook.xml" to declaration("<workbook xmlns=\"$ns\" xmlns:r=\"$officeRel\"><sheets>"+sheets.mapIndexed{i,s->"<sheet name=\"${xml(s.getString("name"))}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")+"</sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>"),
            "xl/_rels/workbook.xml.rels" to rels(*(sheets.indices.map{Triple("rId${it+1}","worksheet","worksheets/sheet${it+1}.xml")}+Triple("styles","styles","styles.xml")).toTypedArray()),
            "xl/styles.xml" to declaration("<styleSheet xmlns=\"$ns\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf/></cellStyleXfs><cellXfs count=\"2\"><xf fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        )
        sheets.forEachIndexed{i,sheet->
            val rows=sheet.getJSONArray("cells").objects().sortedWith(compareBy<JSONObject>{ArtifactSpec.address(it.getString("address")).first}.thenBy{ArtifactSpec.address(it.getString("address")).second}).groupBy{ArtifactSpec.address(it.getString("address")).first}
            val sheetTables=tablesBySheet[i].orEmpty()
            val tableParts=if(sheetTables.isEmpty())"" else "<tableParts count=\"${sheetTables.size}\">"+sheetTables.joinToString(""){ "<tablePart r:id=\"rIdTable${it.id}\"/>" }+"</tableParts>"
            out["xl/worksheets/sheet${i+1}.xml"]=declaration("<worksheet xmlns=\"$ns\" xmlns:r=\"$officeRel\"><sheetData>"+rows.entries.joinToString(""){(row,cells)->"<row r=\"$row\">"+cells.joinToString(""){c->
                val type=c.optString("type","string");val formula=c.optString("formula","");val value=c.optString("value","");val style=if(c.optString("style")=="header")1 else 0
                val body=if(formula.isNotBlank())"<f>${xml(formula)}</f>"+(if(c.has("value"))"<v>${xml(value)}</v>"else "")else if(type=="string")"<is><t xml:space=\"preserve\">${xml(value)}</t></is>"else "<v>${xml(value)}</v>"
                val t=when {
                    formula.isNotBlank()&&!c.has("value")->"" // No type or stale cached value; let a spreadsheet reader calculate it.
                    type=="boolean"->"b"
                    type=="number"->"n"
                    formula.isNotBlank()->"str"
                    else->"inlineStr"
                }
                val typeAttribute=if(t.isBlank())""else " t=\"$t\""
                "<c r=\"${c.getString("address")}\" s=\"$style\"$typeAttribute>$body</c>"
            }+"</row>"}+"</sheetData>"+tableParts+"</worksheet>")
            if(sheetTables.isNotEmpty()) {
                out["xl/worksheets/_rels/sheet${i+1}.xml.rels"]=rels(*sheetTables.map{Triple("rIdTable${it.id}","table","../tables/table${it.id}.xml")}.toTypedArray())
            }
        }
        tables.forEach{table->
            val columns=table.headers.mapIndexed{index,header->"<tableColumn id=\"${index+1}\" name=\"${xml(header)}\"/>"}.joinToString("")
            out["xl/tables/table${table.id}.xml"]=declaration("<table xmlns=\"$ns\" id=\"${table.id}\" name=\"${xml(table.name)}\" displayName=\"${xml(table.name)}\" ref=\"${xml(table.range.ref)}\" totalsRowShown=\"0\"><autoFilter ref=\"${xml(table.range.ref)}\"/><tableColumns count=\"${table.headers.size}\">$columns</tableColumns><tableStyleInfo name=\"TableStyleMedium2\" showFirstColumn=\"0\" showLastColumn=\"0\" showRowStripes=\"1\" showColumnStripes=\"0\"/></table>")
        }
        return out
    }
    private fun docx(spec:ArtifactSpec):Map<String,String> {
        val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val orderedNumberingIds=linkedSetOf<Int>()
        var activeOrderedNumberingId:Int?=null
        var nextOrderedNumberingId=2
        fun runs(s:String)=s.split('\n').mapIndexed{i,line->(if(i>0)"<w:r><w:br/></w:r>"else "")+"<w:r><w:t xml:space=\"preserve\">${xml(line)}</w:t></w:r>"}.joinToString("")
        fun paragraph(p:JSONObject):String {
            val style=p.optString("style","body")
            val props=when(style){
                "title"->{activeOrderedNumberingId=null;"<w:pStyle w:val=\"Title\"/>"}
                "heading"->{activeOrderedNumberingId=null;"<w:pStyle w:val=\"Heading1\"/>"}
                "bullet"->{activeOrderedNumberingId=null;"<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr>"}
                "numbered"->{
                    val numId=activeOrderedNumberingId?:nextOrderedNumberingId++.also {
                        orderedNumberingIds+=it
                        activeOrderedNumberingId=it
                    }
                    "<w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"$numId\"/></w:numPr>"
                }
                "pageBreak"->{activeOrderedNumberingId=null;"<w:pageBreakBefore/>"}
                else->{activeOrderedNumberingId=null;""}
            }
            return "<w:p><w:pPr>$props</w:pPr>${runs(p.getString("text"))}</w:p>"
        }
        fun table(t:JSONObject):String {
            activeOrderedNumberingId=null
            return "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>"+listOf("top","left","bottom","right","insideH","insideV").joinToString(""){"<w:$it w:val=\"single\" w:sz=\"4\"/>"}+"</w:tblBorders></w:tblPr><w:tblGrid>"+(0 until t.getJSONArray("rows").getJSONArray(0).length()).joinToString(""){"<w:gridCol w:w=\"${9026/t.getJSONArray("rows").getJSONArray(0).length()}\"/>"}+"</w:tblGrid>"+t.getJSONArray("rows").arrays().joinToString(""){row->"<w:tr>"+(0 until row.length()).joinToString(""){"<w:tc><w:p>${runs(row.getString(it))}</w:p></w:tc>"}+"</w:tr>"}+"</w:tbl>"
        }
        val descriptor=spec.json()
        val content=if(descriptor.has("blocks")) descriptor.getJSONArray("blocks").objects().joinToString(""){block->
            when(block.getString("kind")){"paragraph"->paragraph(block);"table"->table(block);else->error("DOCX_BLOCK_KIND_UNSUPPORTED")}
        } else descriptor.getJSONArray("paragraphs").objects().joinToString(""){paragraph(it)}+
            descriptor.optJSONArray("tables")?.objects().orEmpty().joinToString(""){table(it)}
        val orderedInstances=orderedNumberingIds.joinToString("") { numId -> "<w:num w:numId=\"$numId\"><w:abstractNumId w:val=\"1\"/></w:num>" }
        val numbering=declaration("<w:numbering xmlns:w=\"$ns\"><w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"singleLevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"•\"/><w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"singleLevel\"/><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/><w:lvlText w:val=\"%1.\"/><w:pPr><w:tabs><w:tab w:val=\"num\" w:pos=\"720\"/></w:tabs><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr></w:lvl></w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>$orderedInstances</w:numbering>")
        return linkedMapOf(
            "[Content_Types].xml" to types("word/document.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml","word/styles.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml","word/numbering.xml" to "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"),
            "_rels/.rels" to rels(Triple("rId1","officeDocument","word/document.xml")),
            "word/document.xml" to declaration("<w:document xmlns:w=\"$ns\"><w:body>$content<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>"),
            "word/_rels/document.xml.rels" to rels(Triple("styles","styles","styles.xml"),Triple("numbering","numbering","numbering.xml")),
            "word/styles.xml" to docxStyles(descriptor.optString("template", "standard")),
            "word/numbering.xml" to numbering
        )
    }
    private fun docxStyles(template:String):String {
        val legacy="<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"24\"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style><w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:rPr><w:b/><w:sz w:val=\"40\"/></w:rPr></w:style><w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr></w:style></w:styles>"
        if(template=="standard")return declaration(legacy)
        val style=when(template) {
            "report"->DocxTemplateStyle("Calibri","3E746D",40,32)
            "meeting_minutes"->DocxTemplateStyle("Arial","1D2522",36,28)
            else->error("DOCX_TEMPLATE_UNSUPPORTED")
        }
        val family=style.font
        val color=style.color
        val titleHalfPoints=style.titleSize
        val headingHalfPoints=style.headingSize
        return declaration("""
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="$family" w:hAnsi="$family" w:eastAsia="$family" w:cs="$family"/><w:sz w:val="24"/><w:lang w:val="es-ES"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="160" w:line="276" w:lineRule="auto"/></w:pPr><w:rPr><w:rFonts w:ascii="$family" w:hAnsi="$family" w:eastAsia="$family" w:cs="$family"/><w:sz w:val="24"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="10"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="200"/></w:pPr><w:rPr><w:rFonts w:ascii="$family" w:hAnsi="$family" w:eastAsia="$family" w:cs="$family"/><w:b/><w:color w:val="$color"/><w:sz w:val="$titleHalfPoints"/><w:szCs w:val="$titleHalfPoints"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="9"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="0"/><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:rFonts w:ascii="$family" w:hAnsi="$family" w:eastAsia="$family" w:cs="$family"/><w:b/><w:color w:val="$color"/><w:sz w:val="$headingHalfPoints"/><w:szCs w:val="$headingHalfPoints"/></w:rPr></w:style>
            </w:styles>
        """.trimIndent())
    }
    private fun pptx(spec:ArtifactSpec):Map<String,ByteArray> {
        val slides=spec.json().getJSONArray("slides").objects();val p="http://schemas.openxmlformats.org/presentationml/2006/main";val a="http://schemas.openxmlformats.org/drawingml/2006/main"
        val group="<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>"
        fun box(id:Int,name:String,x:Long,y:Long,width:Long,height:Long,text:String,size:Int)="<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"$name\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$width\" cy=\"$height\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>"+text.split('\n').joinToString(""){"<a:p><a:r><a:rPr lang=\"es-ES\" sz=\"$size\"><a:solidFill><a:srgbClr val=\"1D2522\"/></a:solidFill><a:latin typeface=\"Arial\"/></a:rPr><a:t>${xml(it)}</a:t></a:r><a:endParaRPr lang=\"es-ES\"/></a:p>"}+"</p:txBody></p:sp>"
        val entries=linkedMapOf<String,ByteArray>()
        val overrides=slides.indices.map{"ppt/slides/slide${it+1}.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"}.toTypedArray()
        entries["[Content_Types].xml"] = (if(slides.any{it.has("imagePngBase64")}) typesWithPng(
            "ppt/presentation.xml" to "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "ppt/slideMasters/slideMaster1.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml",
            "ppt/slideLayouts/slideLayout1.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml",
            "ppt/theme/theme1.xml" to "application/vnd.openxmlformats-officedocument.theme+xml",*overrides
        ) else types(
            "ppt/presentation.xml" to "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "ppt/slideMasters/slideMaster1.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml",
            "ppt/slideLayouts/slideLayout1.xml" to "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml",
            "ppt/theme/theme1.xml" to "application/vnd.openxmlformats-officedocument.theme+xml",*overrides
        )).toByteArray(Charsets.UTF_8)
        entries["_rels/.rels"] = rels(Triple("rId1","officeDocument","ppt/presentation.xml")).toByteArray(Charsets.UTF_8)
        entries["ppt/presentation.xml"] = declaration("<p:presentation xmlns:p=\"$p\" xmlns:r=\"$officeRel\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"master\"/></p:sldMasterIdLst><p:sldIdLst>"+slides.indices.joinToString(""){"<p:sldId id=\"${256+it}\" r:id=\"rId${it+1}\"/>"}+"</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>").toByteArray(Charsets.UTF_8)
        entries["ppt/_rels/presentation.xml.rels"] = rels(*(slides.indices.map{Triple("rId${it+1}","slide","slides/slide${it+1}.xml")}+Triple("master","slideMaster","slideMasters/slideMaster1.xml")).toTypedArray()).toByteArray(Charsets.UTF_8)
        entries["ppt/slideMasters/slideMaster1.xml"] = declaration("<p:sldMaster xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$officeRel\"><p:cSld name=\"Local AI Workspace\"><p:spTree>$group</p:spTree></p:cSld><p:clrMap accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" bg1=\"lt1\" bg2=\"lt2\" folHlink=\"folHlink\" hlink=\"hlink\" tx1=\"dk1\" tx2=\"dk2\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"layout\"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>").toByteArray(Charsets.UTF_8)
        entries["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = rels(Triple("layout","slideLayout","../slideLayouts/slideLayout1.xml"),Triple("theme","theme","../theme/theme1.xml")).toByteArray(Charsets.UTF_8)
        entries["ppt/slideLayouts/slideLayout1.xml"] = declaration("<p:sldLayout xmlns:p=\"$p\" xmlns:a=\"$a\" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree>$group</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>").toByteArray(Charsets.UTF_8)
        entries["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = rels(Triple("master","slideMaster","../slideMasters/slideMaster1.xml")).toByteArray(Charsets.UTF_8)
        entries["ppt/theme/theme1.xml"] = theme().toByteArray(Charsets.UTF_8)
        slides.forEachIndexed{i,s->
            val hasImage=s.has("imagePngBase64")
            val bodyWidth=if(hasImage)5_200_000L else 11_094_720L
            val body=box(3,"Body",548640,1554480,bodyWidth,4754880,s.getString("body"),2200)
            val image=if(hasImage) {
                val payload=PptxImagePayloadValidator.decodeAndValidate(s.getString("imagePngBase64"))
                val boxX=6_048_640L;val boxY=1_554_480L;val boxW=5_500_000L;val boxH=4_754_880L
                val scale=minOf(boxW.toDouble()/payload.width,boxH.toDouble()/payload.height)
                val cx=(payload.width*scale).toLong().coerceAtLeast(1L);val cy=(payload.height*scale).toLong().coerceAtLeast(1L)
                val x=boxX+(boxW-cx)/2;val y=boxY+(boxH-cy)/2
                val picture="<p:pic><p:nvPicPr><p:cNvPr id=\"4\" name=\"Image ${i+1}\" descr=\"${xml(s.getString("imageAltText"))}\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed=\"rIdImage\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>"
                entries["ppt/media/image${i+1}.png"]=payload.bytes
                picture
            } else ""
            entries["ppt/slides/slide${i+1}.xml"] = declaration("<p:sld xmlns:p=\"$p\" xmlns:a=\"$a\" xmlns:r=\"$officeRel\"><p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"F6F7F4\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg><p:spTree>$group${box(2,"Title",548640,365760,11094720,1005840,s.getString("title"),3200)}$body$image</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>").toByteArray(Charsets.UTF_8)
            val relationships=mutableListOf(Triple("layout","slideLayout","../slideLayouts/slideLayout1.xml"))
            if(hasImage)relationships+=Triple("rIdImage","image","../media/image${i+1}.png")
            entries["ppt/slides/_rels/slide${i+1}.xml.rels"]=rels(*relationships.toTypedArray()).toByteArray(Charsets.UTF_8)
        }
        return entries
    }
    private fun theme():String {
        val colors=listOf("dk1" to "1D2522","lt1" to "FFFFFF","dk2" to "54605B","lt2" to "EFF1EE","accent1" to "3E746D","accent2" to "256D46","accent3" to "7A5D00","accent4" to "54605B","accent5" to "77817C","accent6" to "A5D8CE","hlink" to "3E746D","folHlink" to "54605B")
        val fill="<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
        return declaration("<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Local AI Workspace\"><a:themeElements><a:clrScheme name=\"Local\">"+colors.joinToString(""){(name,color)->"<a:$name><a:srgbClr val=\"$color\"/></a:$name>"}+"</a:clrScheme><a:fontScheme name=\"Local\"><a:majorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont><a:minorFont><a:latin typeface=\"Arial\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Local\"><a:fillStyleLst>${fill.repeat(3)}</a:fillStyleLst><a:lnStyleLst>"+(1..3).joinToString(""){"<a:ln w=\"9525\">$fill<a:prstDash val=\"solid\"/></a:ln>"}+"</a:lnStyleLst><a:effectStyleLst>"+"<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3)+"</a:effectStyleLst><a:bgFillStyleLst>${fill.repeat(3)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>")
    }
    private fun pdf(spec:ArtifactSpec,target:File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { doc->
            val bodyFont=context.assets.open("fonts/NotoSans-Regular.ttf").use { PDType0Font.load(doc,it,true) }
            val boldFont=context.assets.open("fonts/NotoSans-Bold.ttf").use { PDType0Font.load(doc,it,true) }
            var stream:PDPageContentStream?=null;var y=0f
            fun page(){stream?.close();require(doc.numberOfPages<100){"PDF_PAGE_LIMIT"};val p=PDPage();doc.addPage(p);stream=PDPageContentStream(doc,p);y=p.mediaBox.height-54}
            fun line(value:String,font:PDFont,size:Float){checkInterrupted();if(stream==null||y<54)page();stream!!.beginText();stream!!.setFont(font,size);stream!!.newLineAtOffset(54f,y);stream!!.showText(value);stream!!.endText();y-=size*1.4f}
            fun paragraph(text:String,bold:Boolean=false,size:Float=12f) {
                val font=if(bold)boldFont else bodyFont
                fun width(value:String)=font.getStringWidth(value)/1000f*size
                for(raw in text.split('\n')) {
                    var part=""
                    val tokens=mutableListOf<Pair<Boolean,String>>()
                    for(cp in raw.codePoints().toArray()) {
                        require(cp!=9){"PDF_TAB_UNSUPPORTED_USE_SPACES"}
                        val value=String(Character.toChars(cp));val whitespace=Character.isWhitespace(cp)
                        if(tokens.lastOrNull()?.first==whitespace)tokens[tokens.lastIndex]=whitespace to (tokens.last().second+value)
                        else tokens+=whitespace to value
                    }
                    fun splitOversizedToken(token:String) {
                        var chunk=""
                        for(cp in token.codePoints().toArray()) {
                            val value=String(Character.toChars(cp));val candidate=chunk+value
                            if(width(candidate)>504f && chunk.isNotEmpty()){line(chunk,font,size);chunk=value}else chunk=candidate
                        }
                        part=chunk
                    }
                    for((whitespace,token) in tokens) {
                        val combined=part+token
                        if(width(combined)<=504f)part=combined
                        else if(whitespace) {
                            if(part.isNotEmpty())line(part,font,size)
                            if(width(token)<=504f)part=token else splitOversizedToken(token)
                        } else {
                            if(part.isNotEmpty()){line(part,font,size);part=""}
                            if(width(token)<=504f)part=token else splitOversizedToken(token)
                        }
                    }
                    line(part,font,size)
                };y-=6
            }
            fun tableCellLines(text:String,font:PDFont,size:Float,maxWidth:Float):List<String> {
                fun width(value:String)=font.getStringWidth(value)/1000f*size
                val output=mutableListOf<String>()
                for(raw in text.split('\n')) {
                    var current=""
                    val segments=mutableListOf<Pair<Boolean,String>>()
                    for(cp in raw.codePoints().toArray()) {
                        require(cp!=9){"PDF_TAB_UNSUPPORTED_USE_SPACES"}
                        val value=String(Character.toChars(cp));val whitespace=Character.isWhitespace(cp)
                        if(segments.lastOrNull()?.first==whitespace)segments[segments.lastIndex]=whitespace to (segments.last().second+value)
                        else segments+=whitespace to value
                    }
                    fun wrapSegment(segment:String) {
                        var part=""
                        for(cp in segment.codePoints().toArray()) {
                            val value=String(Character.toChars(cp));val candidate=part+value
                            if(width(candidate)>maxWidth && part.isNotEmpty()){output+=part;part=value}else part=candidate
                        }
                        current=part
                    }
                    for((_,segment) in segments) {
                        val combined=current+segment
                        if(width(combined)<=maxWidth)current=combined
                        else {
                            if(current.isNotEmpty()){output+=current;current=""}
                            wrapSegment(segment)
                        }
                    }
                    output+=current
                }
                return output.ifEmpty{listOf("")}
            }
            fun table(value:JSONObject) {
                val rows=value.getJSONArray("rows").arrays()
                val columns=rows.first().length()
                val left=54f;val tableWidth=504f;val columnWidth=tableWidth/columns
                val fontSize=9f;val lineHeight=fontSize*1.25f;val padding=4f;val bottom=54f
                val headerFont=boldFont
                val headerCells=(0 until columns).map{tableCellLines(rows.first().getString(it),headerFont,fontSize,columnWidth-padding*2)}
                val headerLineCount=maxOf(1,headerCells.maxOf{it.size})
                val headerHeight=headerLineCount*lineHeight+padding*2
                val pageContentHeight=PDPage().mediaBox.height-bottom*2
                // A very tall first row cannot be repeated without consuming the full page.
                val repeatHeaderOnContinuation=headerHeight+lineHeight+padding*2+4f<=pageContentHeight
                fun drawChunk(cells:List<List<String>>,font:PDType1Font,offset:Int,chunkLines:Int,rowHeight:Float,headerBackground:Boolean) {
                    val rowTop=y;val rowBottom=rowTop-rowHeight
                    stream!!.saveGraphicsState()
                    if(headerBackground) {
                        stream!!.setNonStrokingColor(239,241,238)
                        for(column in 0 until columns) {
                            stream!!.addRect(left+column*columnWidth,rowBottom,columnWidth,rowHeight)
                            stream!!.fill()
                        }
                    }
                    stream!!.setStrokingColor(119,129,124)
                    stream!!.setLineWidth(0.5f)
                    for(column in 0 until columns) {
                        stream!!.addRect(left+column*columnWidth,rowBottom,columnWidth,rowHeight)
                        stream!!.stroke()
                    }
                    stream!!.restoreGraphicsState()
                    for(column in 0 until columns) {
                        val last=minOf(offset+chunkLines,cells[column].size)
                        for(lineIndex in offset until last) {
                            val text=cells[column][lineIndex]
                            if(text.isNotEmpty()) {
                                stream!!.beginText()
                                stream!!.setFont(font,fontSize)
                                stream!!.newLineAtOffset(left+column*columnWidth+padding,rowTop-padding-fontSize-(lineIndex-offset)*lineHeight)
                                stream!!.showText(text)
                                stream!!.endText()
                            }
                        }
                    }
                    y=rowBottom-4f
                }
                fun continuationPage(repeatHeader:Boolean) {
                    page()
                    if(repeatHeader && repeatHeaderOnContinuation) {
                        drawChunk(headerCells,headerFont,0,headerLineCount,headerHeight,headerBackground=true)
                    }
                }
                for((rowIndex,row) in rows.withIndex()) {
                    val font=if(rowIndex==0)boldFont else bodyFont
                    val cells=if(rowIndex==0)headerCells else (0 until columns).map{tableCellLines(row.getString(it),font,fontSize,columnWidth-padding*2)}
                    val rowLineCount=maxOf(1,cells.maxOf{it.size})
                    var offset=0
                    while(offset<rowLineCount) {
                        if(stream==null)y=0f
                        if(stream==null || y-bottom-2*padding<lineHeight)continuationPage(repeatHeader=rowIndex>0)
                        val linesAvailable=((y-bottom-2*padding)/lineHeight).toInt().coerceAtLeast(1)
                        val chunkLines=minOf(rowLineCount-offset,linesAvailable)
                        val rowHeight=chunkLines*lineHeight+padding*2
                        drawChunk(cells,font,offset,chunkLines,rowHeight,headerBackground=rowIndex==0&&offset==0)
                        offset+=chunkLines
                        if(offset<rowLineCount)continuationPage(repeatHeader=rowIndex>0)
                    }
                }
                y-=6f
            }
            try {
                spec.json().getJSONArray("paragraphs").objects().forEach{p->val style=p.optString("style","body");if(style=="pageBreak" && stream!=null)page();paragraph(p.getString("text"),style in setOf("title","heading"),if(style=="title")20f else if(style=="heading")16f else 12f)}
                spec.json().optJSONArray("tables")?.objects()?.forEach(::table)
                stream?.close();stream=null;doc.save(target)
            }finally{stream?.close()}
        }
    }
}
