package dev.flowstate.engine

object SafeInput {
    fun json(source: String, maxBytes: Int = 2_000_000): String {
        require(maxBytes in 1..16_000_000)
        require(source.length <= maxBytes && source.toByteArray(Charsets.UTF_8).size <= maxBytes) {
            "Input exceeds ${maxBytes / 1_000_000} MB"
        }
        var depth = 0
        var quoted = false
        var escaped = false
        source.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else
                when (c) {
                    '"' -> quoted = true
                    '{',
                    '[' -> {
                        depth++
                        require(depth <= 128) { "JSON nesting exceeds 128" }
                    }
                    '}',
                    ']' -> {
                        depth--
                        require(depth >= 0) { "Unbalanced JSON" }
                    }
                }
        }
        require(depth == 0 && !quoted) { "Unbalanced JSON" }
        return source
    }
}
