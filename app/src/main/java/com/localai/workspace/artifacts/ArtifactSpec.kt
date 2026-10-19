package com.localai.workspace.artifacts

import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

enum class ArtifactFormat(val extension:String,val mime:String) {
    XLSX("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    PDF("pdf","application/pdf"),
    PPTX("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    CODE("txt","text/plain")
}

/** Versioned, bounded data contract; no paths, raw XML, macros, scripts or executable hooks. */
class ArtifactSpec private constructor(val format:ArtifactFormat,val name:String,private val payload:String) {
    fun json()=JSONObject(payload)
    fun preview():String = when(format) {
        ArtifactFormat.CODE->json().getString("body")
        ArtifactFormat.XLSX->json().getJSONArray("sheets").objects().joinToString("\n\n") { s->
            s.getString("name")+"\n"+s.getJSONArray("cells").objects().joinToString("\n") { c->
                val formula=c.optString("formula","")
                val displayedValue=if(formula.isNotBlank()&&!c.has("value"))"[cached result unavailable]"else c.optString("value","")
                c.getString("address")+" = "+displayedValue+if(formula.isBlank())"" else " [formula: $formula; not recalculated]"
            }
        }
        ArtifactFormat.PPTX->json().getJSONArray("slides").objects().mapIndexed { index, slide ->
            buildString {
                append("Slide ").append(index + 1).append(" · ").append(slide.getString("title")).append('\n')
                append(slide.getString("body"))
                if (slide.has("imageAltText")) {
                    val image = PptxImagePayloadValidator.decodeAndValidate(slide.getString("imagePngBase64"))
                    append("\n[PNG image: ").append(slide.getString("imageAltText")).append(']')
                    append("\n[PNG SHA-256: ").append(ArtifactStore.hash(image.bytes)).append(']')
                }
            }
        }.joinToString("\n\n")
        ArtifactFormat.DOCX->{
            val descriptor=json()
            if(descriptor.has("blocks")) descriptor.getJSONArray("blocks").objects().joinToString("\n\n") { block ->
                when(block.getString("kind")) {
                    "paragraph"->block.getString("text")
                    "table"->block.getJSONArray("rows").arrays().joinToString("\n") { row->(0 until row.length()).joinToString(" | "){row.getString(it)} }
                    else->error("DOCX_BLOCK_KIND_UNSUPPORTED")
                }
            } else descriptor.getJSONArray("paragraphs").objects().joinToString("\n\n"){it.getString("text")}+
                descriptor.optJSONArray("tables")?.objects().orEmpty().joinToString("\n\n",prefix="\n") { t->
                    t.getJSONArray("rows").arrays().joinToString("\n"){row->(0 until row.length()).joinToString(" | "){row.getString(it)}}
                }
        }
        else->json().getJSONArray("paragraphs").objects().joinToString("\n\n"){it.getString("text")}+
            json().optJSONArray("tables")?.objects().orEmpty().joinToString("\n\n",prefix="\n") { t->
                t.getJSONArray("rows").arrays().joinToString("\n"){row->(0 until row.length()).joinToString(" | "){row.getString(it)}}
            }
    }
    companion object {
        const val MAX_SPEC_BYTES=256*1024
        fun parse(input:JSONObject):ArtifactSpec {
            require(input.toString().toByteArray(Charsets.UTF_8).size<=MAX_SPEC_BYTES){"ARTIFACT_SPEC_TOO_LARGE"}
            val format=ArtifactFormat.valueOf(input.getString("format"))
            val name=input.getString("name")
            require(name.length in 1..100 && Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}._ -]*").matches(name) && !name.contains("..")){"ARTIFACT_NAME_INVALID"}
            val extension=name.substringAfterLast('.',"").lowercase(Locale.ROOT)
            require(if(format==ArtifactFormat.CODE)extension in setOf("txt","md","kt","java","py","js","ts","json","xml","html","css","c","cpp","h","rs","go","sh","sql")else extension==format.extension){"ARTIFACT_EXTENSION_MISMATCH"}
            val keys=setOf("format","name")+when(format){ArtifactFormat.CODE->setOf("body");ArtifactFormat.XLSX->setOf("sheets");ArtifactFormat.PPTX->setOf("slides");ArtifactFormat.DOCX->setOf("paragraphs","tables","blocks");ArtifactFormat.PDF->setOf("paragraphs","tables")}
            fields(input,keys)
            when(format) {
                ArtifactFormat.CODE->text(input.getString("body"),128*1024)
                ArtifactFormat.XLSX->{
                    val sheets=input.getJSONArray("sheets");require(sheets.length() in 1..16){"SHEET_LIMIT"}
                    val names=mutableSetOf<String>();var count=0
                    for(sheet in sheets.objects()) {
                        fields(sheet,setOf("name","cells"));val title=sheet.getString("name");text(title,31)
                        require(title.isNotBlank() && title.none{it in "[]:*?/\\"} && !title.startsWith("'") && !title.endsWith("'") && names.add(title.lowercase(Locale.ROOT))){"SHEET_NAME_INVALID_OR_DUPLICATE"}
                        val addresses=mutableSetOf<String>()
                        for(cell in sheet.getJSONArray("cells").objects()) {
                            require(++count<=10000){"CELL_LIMIT"};fields(cell,setOf("address","value","type","formula","style"))
                            val address=cell.getString("address");address(address);require(addresses.add(address)){"CELL_DUPLICATE"}
                            val type=cell.optString("type","string");require(type in setOf("string","number","boolean")){"CELL_TYPE_UNSUPPORTED"}
                            require(cell.has("value") || cell.has("formula")){"CELL_VALUE_MISSING"}
                            if(cell.has("value")){val v=cell.getString("value");text(v,32768);if(type=="number")number(v);if(type=="boolean")require(v in setOf("0","1")){"BOOLEAN_MUST_BE_0_OR_1"}}
                            if(cell.has("formula")) {
                                val f=cell.getString("formula");text(f,1024);require(f.isNotBlank() && !f.startsWith("=")){"FORMULA_EXCLUDES_LEADING_EQUALS"}
                                require(f.none{it in "[]|"} && !Regex("(?i)(https?:|file:|WEBSERVICE|HYPERLINK|RTD|DDE|EXEC|REGISTER)").containsMatchIn(f)){"EXTERNAL_OR_ACTIVE_FORMULA_UNSUPPORTED"}
                                val functions=Regex("([A-Za-z_][A-Za-z_0-9.]*)\\s*\\(").findAll(f).map{it.groupValues[1].uppercase(Locale.ROOT)}
                                require(functions.all{it in setOf("SUM","AVERAGE","MIN","MAX","COUNT","COUNTA","IF","AND","OR","ROUND","ABS")}){"FORMULA_FUNCTION_UNSUPPORTED"}
                            }
                            require(cell.optString("style","plain") in setOf("plain","header")){"CELL_STYLE_UNSUPPORTED"}
                        }
                    }
                }
                ArtifactFormat.PPTX->{
                    val slides=input.getJSONArray("slides");require(slides.length() in 1..30){"SLIDE_LIMIT"}
                    var totalImageBytes=0L
                    slides.objects().forEach{slide->
                        fields(slide,setOf("title","body","imagePngBase64","imageAltText"))
                        text(slide.getString("title"),120);text(slide.getString("body"),1000)
                        val hasImage=slide.has("imagePngBase64");val hasAlt=slide.has("imageAltText")
                        require(hasImage==hasAlt){"PPTX_IMAGE_FIELDS_REQUIRED_TOGETHER"}
                        if(hasImage) {
                            val alt=slide.getString("imageAltText");require(alt.length in 1..120){"PPTX_IMAGE_ALT_TEXT_LIMIT"};text(alt,120);require(alt.isNotBlank()){"PPTX_IMAGE_ALT_REQUIRED"}
                            totalImageBytes+=PptxImagePayloadValidator.decodeAndValidate(slide.getString("imagePngBase64")).bytes.size
                            require(totalImageBytes<=PptxImagePayloadValidator.MAX_TOTAL_IMAGE_BYTES){"PPTX_TOTAL_IMAGE_BYTES_LIMIT"}
                        }
                    }
                }
                ArtifactFormat.DOCX->{
                    if(input.has("blocks")) {
                        require(!input.has("paragraphs")&&!input.has("tables")){"DOCX_BLOCKS_CANNOT_MIX_LEGACY_CONTENT"}
                        val blocks=input.getJSONArray("blocks");require(blocks.length() in 1..516){"DOCX_BLOCK_LIMIT"}
                        var paragraphs=0;var tables=0
                        for(block in blocks.objects()) when(block.getString("kind")) {
                            "paragraph"->{
                                require(++paragraphs<=500){"PARAGRAPH_LIMIT"}
                                fields(block,setOf("kind","text","style"));text(block.getString("text"),10000)
                                require(block.optString("style","body") in setOf("title","heading","body","bullet","pageBreak")){"PARAGRAPH_STYLE_UNSUPPORTED"}
                            }
                            "table"->{require(++tables<=16){"TABLE_LIMIT"};fields(block,setOf("kind","rows"));validateTable(block.getJSONArray("rows"))}
                            else->error("DOCX_BLOCK_KIND_UNSUPPORTED")
                        }
                    } else validateLegacyParagraphsAndTables(input)
                }
                ArtifactFormat.PDF->validateLegacyParagraphsAndTables(input)
            }
            return ArtifactSpec(format,name,input.toString())
        }
        internal fun address(value:String):Pair<Int,Int> {
            val match=Regex("([A-Z]{1,3})([1-9][0-9]{0,6})").matchEntire(value) ?: error("A1_ADDRESS_REQUIRED")
            val col=match.groupValues[1].fold(0){n,c->n*26+c.code-'A'.code+1};val row=match.groupValues[2].toInt()
            require(col<=16384 && row<=1048576){"ADDRESS_OUT_OF_EXCEL_BOUNDS"};return row to col
        }
        private fun number(v:String) {val n=v.toBigDecimalOrNull() ?: error("NUMBER_INVALID");require(n.precision()<=128 && kotlin.math.abs(n.scale().toLong())<=128){"NUMBER_PRECISION_LIMIT"}}
        private fun validateLegacyParagraphsAndTables(input:JSONObject) {
            val paragraphs=input.getJSONArray("paragraphs");require(paragraphs.length() in 1..500){"PARAGRAPH_LIMIT"}
            paragraphs.objects().forEach{fields(it,setOf("text","style"));text(it.getString("text"),10000);require(it.optString("style","body") in setOf("title","heading","body","bullet","pageBreak")){"PARAGRAPH_STYLE_UNSUPPORTED"}}
            val tables=input.optJSONArray("tables") ?: JSONArray();require(tables.length()<=16){"TABLE_LIMIT"}
            for(t in tables.objects()){fields(t,setOf("rows"));validateTable(t.getJSONArray("rows"))}
        }
        private fun validateTable(rows:JSONArray) {
            require(rows.length() in 1..200){"ROW_LIMIT"};var width:Int?=null
            for(row in rows.arrays()) {
                require(row.length() in 1..8 && (width==null||width==row.length())){"TABLE_RECTANGLE_REQUIRED"}
                width=row.length();for(i in 0 until row.length())text(row.getString(i),2000)
            }
        }
        private fun fields(j:JSONObject,allowed:Set<String>) {require(j.keys().asSequence().all{it in allowed}){"ARTIFACT_FIELD_UNSUPPORTED"}}
        private fun text(s:String,max:Int){require(s.length<=max){"ARTIFACT_TEXT_LIMIT"};var i=0;while(i<s.length){val c=s.codePointAt(i);require(c==9||c==10||c==13||c in 32..0xD7FF||c in 0xE000..0xFFFD||c in 0x10000..0x10FFFF){"ARTIFACT_INVALID_UNICODE"};i+=Character.charCount(c)}}
    }
}
internal fun JSONArray.objects()=(0 until length()).map{getJSONObject(it)}
internal fun JSONArray.arrays()=(0 until length()).map{getJSONArray(it)}
