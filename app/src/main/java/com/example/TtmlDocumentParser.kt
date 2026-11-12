package com.example

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/** Projects TTML into the native renderer's model, retaining optional source metadata.
 * Only explicit, valid source timestamps become word timing. XML entities never use the network. */
internal fun parseTtmlDocument(xml: String): List<LyricLine> = runCatching {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
    // Better Lyrics links translations/transliterations in <head> to the paragraph's key.
    // Reading only inline role spans silently discarded this documented response shape.
    val linkedTranslation = mutableMapOf<String, String>()
    val linkedRomanization = mutableMapOf<String, String>()
    val linkedTexts = document.getElementsByTagNameNS("*", "text")
    for (index in 0 until linkedTexts.length) {
        val entry = linkedTexts.item(index) as? Element ?: continue
        val key = entry.getAttribute("for").removePrefix("#")
        if (key.isBlank()) continue
        val ancestors = generateSequence(entry.parentNode) { it.parentNode }.filterIsInstance<Element>()
            .map { it.localName ?: it.nodeName }.toSet()
        val target = when {
            "translation" in ancestors -> linkedTranslation
            "transliteration" in ancestors -> linkedRomanization
            else -> continue
        }
        val value = entry.textContent.trim().takeIf { it.isNotBlank() } ?: continue
        target.putIfAbsent(key, value)
    }
    val paragraphs = document.getElementsByTagNameNS("*", "p")
    buildList {
        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val start = parseTtmlTimeMs(paragraph.getAttribute("begin")) ?: continue
            val words = mutableListOf<LyricWord>()
            val text = StringBuilder()
            val auxiliary = mutableMapOf<String, MutableList<String>>()
            var pendingSpace = false

            fun append(raw: String, begin: Long? = null, end: Long? = null) {
                if (raw.isBlank()) {
                    if (raw.isNotEmpty() && '\n' !in raw && '\r' !in raw) pendingSpace = true
                    return
                }
                val prefix = if (text.isNotEmpty() && pendingSpace && !text.last().isWhitespace() &&
                    !raw.first().isWhitespace()) " " else ""
                val piece = prefix + raw
                text.append(piece)
                pendingSpace = false
                if (begin != null && end != null && end > begin) words += LyricWord(begin, end, piece)
            }

            fun walk(node: Node) {
                if (node.nodeType == Node.TEXT_NODE) {
                    append(node.textContent.orEmpty())
                    return
                }
                val element = node as? Element ?: return
                val role = element.localAttribute("role")
                if (role in setOf("x-bg", "x-translation", "x-roman", "x-romanization")) {
                    auxiliary.getOrPut(role) { mutableListOf() }.add(element.textContent.trim())
                    return
                }
                val begin = parseTtmlTimeMs(element.getAttribute("begin"))
                val end = parseTtmlTimeMs(element.getAttribute("end"))
                    ?: parseTtmlTimeMs(element.getAttribute("dur"))?.let { duration -> begin?.plus(duration) }
                val nestedElements = element.getElementsByTagNameNS("*", "span")
                if (element.localName == "span" && begin != null && end != null && nestedElements.length == 0) {
                    append(element.textContent.orEmpty(), begin, end)
                } else {
                    for (child in 0 until element.childNodes.length) walk(element.childNodes.item(child))
                }
            }
            for (child in 0 until paragraph.childNodes.length) walk(paragraph.childNodes.item(child))
            val mainText = text.toString().trim()
            if (mainText.isEmpty()) continue
            fun extra(vararg roles: String): String? = roles.flatMap { auxiliary[it].orEmpty() }
                .filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotEmpty() }
            val sourceKey = paragraph.localAttribute("key").ifBlank { paragraph.localAttribute("id") }.ifBlank { null }
            add(LyricLine(
                timeMs = start, text = mainText, words = words.takeIf { it.isNotEmpty() },
                endMs = (parseTtmlTimeMs(paragraph.getAttribute("end"))
                    ?: parseTtmlTimeMs(paragraph.getAttribute("dur"))?.plus(start))?.takeIf { it > start },
                sourceKey = sourceKey,
                agent = paragraph.localAttribute("agent").ifBlank { null },
                language = generateSequence(paragraph as Node?) { it.parentNode }
                    .filterIsInstance<Element>().map { it.localAttribute("lang") }.firstOrNull { it.isNotBlank() },
                translation = extra("x-translation") ?: linkedTranslation[sourceKey],
                romanization = extra("x-roman", "x-romanization") ?: linkedRomanization[sourceKey],
                backgroundText = extra("x-bg"),
            ))
        }
    }.sortedBy { it.timeMs }
}.getOrDefault(emptyList())

private fun Element.localAttribute(local: String): String {
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        if ((attribute.localName ?: attribute.nodeName.substringAfter(':')) == local) return attribute.nodeValue.orEmpty()
    }
    return ""
}

/** Clock and offset expressions. Unsupported frame/tick expressions are rejected, never zeroed. */
internal fun parseTtmlTimeMs(raw: String): Long? {
    val value = raw.trim()
    val offset = Regex("^(\\d+(?:\\.\\d+)?)(ms|h|m|s)?$").matchEntire(value)
    val millis = if (offset != null) {
        val unit = when (offset.groupValues[2]) { "ms" -> 1.0; "h" -> 3_600_000.0; "m" -> 60_000.0; else -> 1000.0 }
        offset.groupValues[1].toDoubleOrNull()?.times(unit)
    } else {
        val parts = value.split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        if (numbers.any { !it.isFinite() || it < 0 } || numbers.last() >= 60 ||
            (parts.size == 3 && numbers[1] >= 60)) return null
        numbers.fold(0.0) { total, part -> total * 60 + part } * 1000
    } ?: return null
    return millis.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()
}
