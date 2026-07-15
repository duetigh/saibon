package dev.saibon.search.query

/**
 * Recursive-descent parser for the search query grammar:
 * ```
 * orExpr  := andExpr ('|' andExpr)*
 * andExpr := notExpr (('&')? notExpr)*   // adjacent terms with no operator are still ANDed
 * notExpr := '!' notExpr | atom
 * atom    := '(' orExpr ')' | IDENT ':' VALUE | VALUE
 * ```
 * `VALUE` supports `"quoted multi-word"` substrings. Any parse error falls
 * back to treating the whole input as one [SearchQuery.Bare] term, so
 * search-as-you-type never dead-ends on a trailing `&` or a bare `enchant:`.
 */
object SearchParser {
    fun parse(input: String): SearchQuery {
        if (input.isBlank()) return SearchQuery.Bare("")
        return runCatching { Parser(Tokenizer.tokenize(input)).parseTop() }
            .getOrElse { SearchQuery.Bare(input.trim()) }
    }

    private sealed class Token {
        data class Word(val text: String) : Token()
        object And : Token()
        object Or : Token()
        object Not : Token()
        object Colon : Token()
        object LParen : Token()
        object RParen : Token()
    }

    private object Tokenizer {
        private const val SPECIAL_CHARS = "&|!:() \t\""

        fun tokenize(input: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < input.length) {
                when (val c = input[i]) {
                    ' ', '\t' -> i++
                    '&' -> { tokens += Token.And; i++ }
                    '|' -> { tokens += Token.Or; i++ }
                    '!' -> { tokens += Token.Not; i++ }
                    ':' -> { tokens += Token.Colon; i++ }
                    '(' -> { tokens += Token.LParen; i++ }
                    ')' -> { tokens += Token.RParen; i++ }
                    '"' -> {
                        val end = input.indexOf('"', i + 1)
                        require(end >= 0) { "unterminated quote" }
                        tokens += Token.Word(input.substring(i + 1, end))
                        i = end + 1
                    }
                    else -> {
                        val start = i
                        while (i < input.length && input[i] !in SPECIAL_CHARS) i++
                        require(i > start) { "empty token at $start (unexpected '$c')" }
                        tokens += Token.Word(input.substring(start, i))
                    }
                }
            }
            return tokens
        }
    }

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token = tokens[pos++]

        fun parseTop(): SearchQuery {
            val result = parseOr()
            require(pos == tokens.size) { "trailing tokens after position $pos" }
            return result
        }

        private fun parseOr(): SearchQuery {
            var left = parseAnd()
            while (peek() is Token.Or) {
                next()
                left = SearchQuery.Or(left, parseAnd())
            }
            return left
        }

        private fun parseAnd(): SearchQuery {
            var left = parseNot()
            while (true) {
                val token = peek() ?: break
                if (token is Token.Or || token is Token.RParen) break
                if (token is Token.And) next()
                left = SearchQuery.And(left, parseNot())
            }
            return left
        }

        private fun parseNot(): SearchQuery {
            if (peek() is Token.Not) {
                next()
                return SearchQuery.Not(parseNot())
            }
            return parseAtom()
        }

        private fun parseAtom(): SearchQuery {
            when (val token = next()) {
                is Token.LParen -> {
                    val inner = parseOr()
                    require(next() is Token.RParen) { "expected )" }
                    return inner
                }
                is Token.Word -> {
                    if (peek() is Token.Colon) {
                        next()
                        val value = next()
                        require(value is Token.Word) { "expected value after :" }
                        return SearchQuery.Field(token.text.lowercase(), value.text)
                    }
                    return SearchQuery.Bare(token.text)
                }
                else -> throw IllegalArgumentException("unexpected token $token")
            }
        }
    }
}
