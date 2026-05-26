package com.qwe7002.telegram_sms.static_class

/**
 * A minimal Mustache-style template renderer shared by [Template] (plain text) and
 * [CcSend] (plain + JSON-escaped) so both engines support the same logic tags.
 *
 * Supported syntax (whitespace inside the braces is ignored):
 *  - `{{name}}`            variable substitution
 *  - `{{#name}}…{{/name}}` section: rendered only when `name` is *truthy*
 *  - `{{^name}}…{{/name}}` inverted section: rendered only when `name` is *falsy*
 *  - `{{! comment }}`      comment, removed from the output
 *
 * Values come from a flat `Map<String, String>`. A key is **falsy** when it is absent,
 * an empty string, or the literal `"false"` (case-insensitive); otherwise it is truthy.
 *
 * Backward-compatibility with the previous simple replacers is intentional:
 *  - an unknown `{{name}}` (no matching key) is left in the output verbatim;
 *  - substituted values are emitted as-is and never re-scanned for further tags, so a
 *    value that happens to contain `{{…}}` can no longer inject template syntax.
 */
object Mustache {

    // {{ <sigil?> <name> }} — sigil is one of # ^ / ! ; name captured lazily up to "}}".
    // The closing braces must be escaped: Android's ICU regex engine rejects a bare '}'
    // as a syntax error (unlike the desktop JVM's lenient java.util.regex used in tests).
    private val tokenRegex = Regex("""\{\{\s*([#^/!]?)\s*([^\{\}]*?)\s*\}\}""")

    private sealed interface Node
    private data class Text(val text: String) : Node
    private data class Variable(val name: String, val raw: String) : Node
    private data class Section(
        val name: String,
        val inverted: Boolean,
        val children: List<Node>
    ) : Node

    /**
     * Render [template] against [values].
     *
     * @param escape applied to every substituted variable value (not to literal template
     *   text). Defaults to identity; pass a JSON escaper for JSON bodies.
     */
    fun render(
        template: String,
        values: Map<String, String>,
        escape: (String) -> String = { it }
    ): String {
        val sb = StringBuilder(template.length)
        renderNodes(parse(template), values, escape, sb)
        return sb.toString()
    }

    private fun parse(template: String): List<Node> {
        // rootNodes is always the bottom of the stack; each open section pushes a new frame.
        val rootNodes = mutableListOf<Node>()
        val stack = ArrayDeque<MutableList<Node>>().apply { addLast(rootNodes) }
        val openSections = ArrayDeque<Pair<String, Boolean>>() // name to inverted

        var cursor = 0
        for (match in tokenRegex.findAll(template)) {
            if (match.range.first > cursor) {
                stack.last().add(Text(template.substring(cursor, match.range.first)))
            }
            cursor = match.range.last + 1

            val sigil = match.groupValues[1]
            val name = match.groupValues[2]
            when (sigil) {
                "!" -> { /* comment: drop it */ }
                "#", "^" -> {
                    openSections.addLast(name to (sigil == "^"))
                    stack.addLast(mutableListOf())
                }
                "/" -> {
                    // Tolerate a stray close tag with no matching open: treat it as text.
                    if (openSections.isEmpty()) {
                        stack.last().add(Text(match.value))
                    } else {
                        val children = stack.removeLast()
                        val (openName, inverted) = openSections.removeLast()
                        stack.last().add(Section(openName, inverted, children))
                    }
                }
                else -> stack.last().add(Variable(name, match.value))
            }
        }
        if (cursor < template.length) {
            stack.last().add(Text(template.substring(cursor)))
        }

        // Unbalanced input (sections opened but never closed): flush the dangling frames
        // best-effort so no captured text is silently lost.
        while (openSections.isNotEmpty()) {
            val children = stack.removeLast()
            val (openName, inverted) = openSections.removeLast()
            stack.last().add(Section(openName, inverted, children))
        }
        return rootNodes
    }

    private fun renderNodes(
        nodes: List<Node>,
        values: Map<String, String>,
        escape: (String) -> String,
        sb: StringBuilder
    ) {
        for (node in nodes) {
            when (node) {
                is Text -> sb.append(node.text)
                is Variable -> {
                    val value = values[node.name]
                    // Unknown key: keep the placeholder verbatim (legacy behavior).
                    if (value == null) sb.append(node.raw) else sb.append(escape(value))
                }
                is Section -> {
                    if (isTruthy(values[node.name]) != node.inverted) {
                        renderNodes(node.children, values, escape, sb)
                    }
                }
            }
        }
    }

    private fun isTruthy(value: String?): Boolean =
        !value.isNullOrEmpty() && !value.equals("false", ignoreCase = true)
}
