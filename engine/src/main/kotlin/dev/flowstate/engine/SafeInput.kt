package dev.flowstate.engine

object SafeInput {
    fun json(source: String): String {
        require(source.length<=2_000_000) { "Input exceeds 2 MB" }
        var depth=0;var quoted=false;var escaped=false
        source.forEach { c ->
            if(quoted) { if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false }
            else when(c) { '"' -> quoted=true; '{','[' -> {depth++;require(depth<=128){"JSON nesting exceeds 128"}}; '}',']' -> {depth--;require(depth>=0){"Unbalanced JSON"}} }
        }
        require(depth==0 && !quoted) { "Unbalanced JSON" };return source
    }
}
