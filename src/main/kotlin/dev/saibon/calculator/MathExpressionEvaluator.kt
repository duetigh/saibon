package dev.saibon.calculator

/**
 * Recursive-descent evaluator for `+ - * / % ^ ( )` over decimals, right-
 * associative `^`, and unary `+`/`-` — NEU-style "type math into the search
 * bar and see the answer" support. [looksLikeMath] gates which inputs are
 * even attempted, so a plain item-name search (letters, no operator) never
 * gets routed here instead of the normal query parser.
 */
object MathExpressionEvaluator {
    private val VALID_CHARS = Regex("""^[0-9+\-*/%^().\s]+$""")
    private val HAS_OPERATOR = Regex("""[+\-*/%^]""")

    fun looksLikeMath(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !VALID_CHARS.matches(trimmed)) return false
        return HAS_OPERATOR.containsMatchIn(trimmed)
    }

    fun evaluate(input: String): Double? {
        val result = runCatching { Parser(input).parseTop() }.getOrNull() ?: return null
        return result.takeUnless { it.isNaN() || it.isInfinite() }
    }

    fun format(value: Double): String {
        val rounded = Math.round(value * 10_000.0) / 10_000.0
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            return rounded.toLong().toString()
        }
        return rounded.toString().trimEnd('0').trimEnd('.')
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parseTop(): Double {
            val result = parseExpr()
            skipWhitespace()
            require(pos == input.length) { "trailing input at $pos" }
            return result
        }

        private fun parseExpr(): Double {
            var left = parseTerm()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> { pos++; left += parseTerm() }
                    '-' -> { pos++; left -= parseTerm() }
                    else -> return left
                }
            }
        }

        private fun parseTerm(): Double {
            var left = parsePower()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> { pos++; left *= parsePower() }
                    '/' -> { pos++; left /= parsePower() }
                    '%' -> { pos++; left %= parsePower() }
                    else -> return left
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipWhitespace()
            if (peek() == '^') {
                pos++
                return Math.pow(base, parsePower())
            }
            return base
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            return when (peek()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> parseAtom()
            }
        }

        private fun parseAtom(): Double {
            skipWhitespace()
            if (peek() == '(') {
                pos++
                val value = parseExpr()
                skipWhitespace()
                require(peek() == ')') { "expected )" }
                pos++
                return value
            }
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            require(pos > start) { "expected number at $start" }
            return input.substring(start, pos).toDouble()
        }

        private fun peek(): Char? = input.getOrNull(pos)
        private fun skipWhitespace() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
    }
}
