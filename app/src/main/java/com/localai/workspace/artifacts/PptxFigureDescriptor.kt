package com.localai.workspace.artifacts

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Small, data-only shape vocabulary for generated PPTX slides. Coordinates are per-thousand
 * fractions of the 16:9 slide canvas; raw OOXML and external resources are never accepted. */
internal data class PptxFigure(
    val kind: String,
    val description: String,
    val label: String?,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val fillColor: String,
    val lineColor: String,
)

internal object PptxFigureDescriptor {
    const val MAX_PER_SLIDE = 12
    const val MAX_PER_PRESENTATION = 120
    private const val NORMALIZED_EXTENT = 1000
    private val allowedKinds = setOf("rectangle", "ellipse", "roundedRectangle", "triangle")
    private val allowedFields = setOf("kind", "description", "label", "left", "top", "width", "height", "fillColor", "lineColor")

    fun parse(slide: JSONObject): List<PptxFigure> {
        require(!slide.has("shapes") || slide.opt("shapes") is JSONArray) { "PPTX_SHAPE_ARRAY_REQUIRED" }
        val descriptors = slide.optJSONArray("shapes") ?: JSONArray()
        require(descriptors.length() <= MAX_PER_SLIDE) { "PPTX_SHAPE_LIMIT_PER_SLIDE" }
        return (0 until descriptors.length()).map { index ->
            val shape = descriptors.getJSONObject(index)
            require(shape.keys().asSequence().all { it in allowedFields }) { "PPTX_SHAPE_FIELD_UNSUPPORTED" }
            val kind = shape.getString("kind")
            require(kind in allowedKinds) { "PPTX_SHAPE_KIND_UNSUPPORTED" }
            val description = shape.getString("description")
            text(description, 200)
            require(description.isNotBlank()) { "PPTX_SHAPE_DESCRIPTION_REQUIRED" }
            val label = if (shape.has("label")) shape.getString("label") else null
            label?.let {
                text(it, 80)
                require(it.isNotBlank()) { "PPTX_SHAPE_LABEL_EMPTY" }
            }
            val left = integer(shape, "left")
            val top = integer(shape, "top")
            val width = integer(shape, "width")
            val height = integer(shape, "height")
            val right = left.toLong() + width.toLong()
            val bottom = top.toLong() + height.toLong()
            require(left in 0 until NORMALIZED_EXTENT && top in 0 until NORMALIZED_EXTENT &&
                width in 1..NORMALIZED_EXTENT && height in 1..NORMALIZED_EXTENT &&
                right <= NORMALIZED_EXTENT && bottom <= NORMALIZED_EXTENT) {
                "PPTX_SHAPE_BOUNDS_INVALID"
            }
            PptxFigure(
                kind = kind,
                description = description,
                label = label,
                left = left,
                top = top,
                width = width,
                height = height,
                fillColor = color(shape, "fillColor", "3E746D"),
                lineColor = color(shape, "lineColor", "1D2522"),
            )
        }
    }

    fun formatName(kind: String): String = when (kind) {
        "rectangle" -> "rect"
        "ellipse" -> "ellipse"
        "roundedRectangle" -> "roundRect"
        "triangle" -> "triangle"
        else -> error("PPTX_SHAPE_KIND_UNSUPPORTED")
    }

    fun contrastTextColor(background: String): String {
        val red = background.substring(0, 2).toInt(16) / 255.0
        val green = background.substring(2, 4).toInt(16) / 255.0
        val blue = background.substring(4, 6).toInt(16) / 255.0
        fun linear(value: Double) = if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        return if (luminance > 0.179) "000000" else "FFFFFF"
    }

    private fun integer(shape: JSONObject, key: String): Int {
        val raw = shape.opt(key)
        require(raw is Number) { "PPTX_SHAPE_COORDINATE_INTEGER_REQUIRED" }
        val value = raw.toDouble()
        require(value.isFinite() && value == value.toInt().toDouble()) { "PPTX_SHAPE_COORDINATE_INTEGER_REQUIRED" }
        return value.toInt()
    }

    private fun color(shape: JSONObject, key: String, default: String): String {
        if (!shape.has(key)) return default
        val value = shape.getString(key)
        require(Regex("[0-9A-Fa-f]{6}").matches(value)) { "PPTX_SHAPE_COLOR_INVALID" }
        return value.uppercase(Locale.ROOT)
    }

    private fun text(value: String, maximum: Int) {
        require(value.length <= maximum) { "PPTX_SHAPE_TEXT_LIMIT" }
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            require(codePoint == 9 || codePoint == 10 || codePoint == 13 ||
                codePoint in 32..0xD7FF || codePoint in 0xE000..0xFFFD || codePoint in 0x10000..0x10FFFF) {
                "PPTX_SHAPE_INVALID_UNICODE"
            }
            index += Character.charCount(codePoint)
        }
    }
}
