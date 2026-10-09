package dev.flowstate.engine

import kotlinx.serialization.json.*
import java.time.*

object Compiler {
    val actions = setOf("message","ask","checklist","if","switch","wait","waitUntil","waitCondition","repeat","while","break","continue","stop","return","call","set","delete","log","assert","try","parallel","notifyCancel","breakpoint")
    private val unary = setOf("not","empty","trim","upper","lower","length","toString","toNumber","elapsed")
    private val binary = setOf("and","or","xor","eq","ne","gt","lt","ge","le","add","subtract","multiply","divide","mod","contains","starts","ends","concat","append","remove","item","join","betweenTime","addDuration","subtractDuration","format")
    private val zero = setOf("now","date","time","weekday","month","weekend","occupancy")
    fun compile(source: String, id: String, version: Int, locations: Set<String> = emptySet(), workflows: Set<String> = emptySet()): Definition {
        require(source.length <= 2_000_000) { "Workspace exceeds 2 MB" }
        val root = codec.parseToJsonElement(SafeInput.json(source)).jsonObject
        val tops = root["blocks"]?.jsonObject?.get("blocks")?.jsonArray ?: throw ValidationException(listOf(Issue("EMPTY", "Add a trigger and connect actions")))
        val issues = mutableListOf<Issue>(); val nodes = mutableListOf<Node>(); val seen = mutableSetOf<String>(); val vars = mutableListOf<Variable>()
        fun fields(b: JsonObject) = b["fields"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        fun input(b: JsonObject, name: String): JsonObject? { val i = b["inputs"]?.jsonObject?.get(name)?.jsonObject; return (i?.get("block") ?: i?.get("shadow"))?.jsonObject }
        fun identify(b: JsonObject, depth: Int): String { require(depth <= 64) { "Block nesting exceeds 64" }; require(seen.size < 2000) { "Too many blocks" }; require(b["disabled"]?.jsonPrimitive?.booleanOrNull!=true) { "Remove disabled blocks before compiling" }; val key = b["id"]?.jsonPrimitive?.content ?: error("Missing block ID"); require(seen.add(key)) { "Duplicate block ID $key" }; return key }
        fun expression(b: JsonObject?, depth: Int): Expr {
            if(b == null) { issues += Issue("INPUT", "Connect the required expression"); return Expr("literal", Value.NULL) }
            val bid = identify(b, depth); val type = b["type"]!!.jsonPrimitive.content; val f = fields(b)
            return when(type) {
                "fs_value" -> { val t = Type.valueOf(f["TYPE"] ?: "STRING"); val text = f["VALUE"] ?: ""; val v = when(t) { Type.BOOLEAN -> Value.bool(text.toBooleanStrict()); Type.INTEGER -> Value.integer(text.toLong()); Type.DECIMAL -> Value.number(text.toDouble()); Type.INSTANT -> Value(t, Instant.parse(text).toEpochMilli().toString()); Type.DURATION -> Value(t, Math.multiplyExact(text.toLong(),1000).toString()); Type.NULL -> Value.NULL; Type.LIST -> Value(Type.LIST, items = text.split('|').filter { it.isNotEmpty() }.map(Value::string)); else -> Value.string(text) }; Expr("literal",v) }
                "fs_get" -> Expr("get", name = f["NAME"] ?: "", scope = Scope.valueOf(f["SCOPE"] ?: "LOCAL"))
                "fs_expr" -> { val op = f["OP"] ?: "eq"; val n = when(op) { in unary, "beforeTime", "afterTime" -> 1; in binary -> 2; "range" -> 3; in zero -> 0; else -> { issues += Issue("EXPR", "Unsupported operation $op", bid); 0 } }; (n..2).forEach { if(input(b,listOf("A","B","C")[it])!=null)issues+=Issue("UNUSED","Remove unused expression input",bid) }; Expr(op, name=f["NAME"] ?: "", args=(0 until n).map { expression(input(b, listOf("A","B","C")[it]),depth+1) }) }
                else -> { issues += Issue("TYPE", "Not an expression: $type",bid); Expr("literal",Value.NULL) }
            }
        }
        fun chain(first: JsonObject?, depth: Int): String? {
            if(first == null) return null
            val bid = identify(first,depth); val type = first["type"]!!.jsonPrimitive.content; val f = fields(first)
            if(type == "fs_variable") {
                vars += Variable(f["NAME"] ?: "",Type.valueOf(f["TYPE"] ?: "STRING"),Scope.valueOf(f["SCOPE"] ?: "LOCAL"))
                return chain(first["next"]?.jsonObject?.get("block")?.jsonObject,depth)
            }
            val op = type.removePrefix("fs_")
            if(op !in actions) issues += Issue("BLOCK", "Unsupported action $type",bid)
            val expressions = mutableMapOf<String,Expr>()
            val expressionNames = when(op) { "if","while","assert","waitCondition" -> listOf("TEST"); "set","switch","waitUntil" -> listOf("VALUE"); "call" -> if(f["INPUTNAME"].isNullOrEmpty())emptyList() else listOf("INPUT"); "return" -> if(input(first,"VALUE")==null)emptyList() else listOf("VALUE"); else -> emptyList() }
            expressionNames.forEach { expressions[it] = expression(input(first,it),depth+1) }
            val branches = mutableMapOf<String,String?>()
            val names = when(op) { "if" -> listOf("YES","NO"); "ask" -> listOf("YES","NO","OTHER","CANCEL","TIMEOUT")+(if(f["KIND"]=="choice") (f["OPTIONS"] ?: "").split('|').filter {it.isNotBlank()}.indices.map {"CHOICE$it"} else emptyList()); "checklist" -> listOf("DONE","CANCEL","TIMEOUT"); "repeat","while","waitCondition" -> listOf("DO"); "try" -> listOf("DO","ERROR"); "parallel" -> listOf("A","B"); "switch" -> listOf("YES","NO","OTHER"); else -> emptyList() }
            names.forEach { branches[it] = chain(input(first,it),depth+1) }
            val next = chain(first["next"]?.jsonObject?.get("block")?.jsonObject,depth)
            nodes += Node(bid,op,f,expressions,branches,next)
            return bid
        }
        val triggers = tops.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "fs_trigger" }
        if(triggers.size != 1 || tops.size != 1) issues += Issue("ROOT", "Exactly one trigger is required; connect all actions to it")
        val top = triggers.firstOrNull()?.jsonObject ?: throw ValidationException(issues)
        identify(top,0); val f = fields(top)
        val trigger = Trigger(kind=f["KIND"] ?: "manual",times=(f["TIMES"] ?: "09:00").split(',').map { it.trim() },zone=f["ZONE"] ?: "device",recurrence=f["RECURRENCE"] ?: "daily",days=(f["DAYS"] ?: "1,2,3,4,5,6,7").split(',').filter { it.isNotBlank() }.map { it.trim().toInt() },dates=(f["DATES"] ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() },intervalDays=(f["INTERVAL"] ?: "1").toInt(),startDate=f["START"] ?: "",endDate=f["END"] ?: "",locationId=f["LOCATION"] ?: "",transition=f["TRANSITION"] ?: "exit",cooldownSeconds=(f["COOLDOWN"] ?: "300").toLong(),catchUp=f["CATCHUP"] ?: "skip",windowDay=(f["WINDOWDAY"] ?: "5").toInt())
        val entry = chain(top["next"]?.jsonObject?.get("block")?.jsonObject,0)
        val d = Definition(id=id,version=version,entry=entry,nodes=nodes,variables=vars,trigger=trigger)
        issues += validate(d,locations,workflows)
        if(issues.isNotEmpty()) throw ValidationException(issues)
        return d
    }
    fun validate(d: Definition, locations: Set<String> = emptySet(), workflows: Set<String> = emptySet()): List<Issue> {
        val errors = mutableListOf<Issue>()
        fun issue(message: String, id: String="") { errors += Issue("VALIDATION",message,id) }
        if(d.schema != 1 || d.nodes.size > 2000 || d.maxSteps !in 1..10000 || d.maxIterations !in 1..1000) issue("Unsupported schema or unsafe budget")
        val ids=d.nodes.map { it.id }.toSet(); if(ids.size != d.nodes.size) issue("Duplicate node IDs")
        if(d.entry == null || d.entry !in ids) issue("Connect an action to the trigger")
        val declarations=d.variables.associateBy { Expressions.key(it.scope,it.name,d.id) }
        if(declarations.size != d.variables.size) issue("Duplicate scoped variable")
        d.variables.forEach { if(!it.name.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) issue("Variable names must start with a letter and contain only letters, numbers or underscores") }
        fun infer(e: Expr, node: String, depth: Int=0): Type {
            if(depth>64) { issue("Expression too deep",node); return Type.NULL }
            if(e.op=="literal") return e.value?.type ?: Type.NULL
            if(e.op=="get") return declarations[Expressions.key(e.scope,e.name,d.id)]?.type ?: Type.NULL.also { issue("Declare variable ${e.name} first",node) }
            val arity=when(e.op) { in unary,"beforeTime","afterTime" -> 1; in binary -> 2; "range" -> 3; in zero -> 0; "list" -> e.args.size; else -> -1 }
            if(e.args.size != arity || arity<0) { issue("Invalid expression ${e.op}",node); return Type.NULL }
            val types=e.args.map { infer(it,node,depth+1) }
            if(e.op in setOf("and","or","xor","not") && types.any { it!=Type.BOOLEAN }) issue("Boolean input required",node)
            if(e.op in setOf("add","subtract","multiply","divide","mod","range") && types.any { !Expressions.numeric(it) }) issue("Numeric input required; convert explicitly",node)
            if(e.op in setOf("eq","ne","gt","ge","lt","le") && types.size==2 && types[0]!=types[1] && !types.all(Expressions::numeric)) issue("Comparison types differ",node)
            if(e.op in setOf("trim","upper","lower","toNumber","beforeTime","afterTime","concat","starts","ends","betweenTime") && types.any {it!=Type.STRING})issue("String input required",node)
            if(e.op in setOf("append","remove","item","join") && types.firstOrNull()!=Type.LIST)issue("List input required",node)
            if(e.op=="item" && types.getOrNull(1)!=Type.INTEGER)issue("List index must be Integer",node)
            if(e.op=="join" && types.getOrNull(1)!=Type.STRING)issue("Join separator must be String",node)
            if(e.op in setOf("elapsed","format","addDuration","subtractDuration") && types.firstOrNull()!=Type.INSTANT)issue("Timestamp input required",node)
            if(e.op in setOf("addDuration","subtractDuration") && types.getOrNull(1)!=Type.DURATION)issue("Duration input required",node)
            return when(e.op) { "and","or","xor","not","eq","ne","gt","ge","lt","le","range","empty","contains","starts","ends","weekend","betweenTime","beforeTime","afterTime" -> Type.BOOLEAN; "now","addDuration","subtractDuration" -> Type.INSTANT; "elapsed" -> Type.DURATION; "add","subtract","multiply","divide","mod","toNumber" -> Type.DECIMAL; "weekday","month","length" -> Type.INTEGER; "list","append","remove" -> Type.LIST; "item" -> Type.NULL; else -> Type.STRING }
        }
        d.nodes.forEach { n ->
            if(n.op !in actions) issue("Unsupported node ${n.op}",n.id)
            (n.branches.values+n.next).filterNotNull().forEach { if(it !in ids) issue("Missing target $it",n.id) }
            n.expressions.forEach { (key,e) -> val t=infer(e,n.id); if(key=="TEST" && t!=Type.BOOLEAN) issue("Condition must be Boolean",n.id) }
            if(n.op in setOf("repeat","while") && (n.fields["LIMIT"]?.toIntOrNull() ?: 0) !in 1..d.maxIterations) issue("Set loop limit between 1 and ${d.maxIterations}",n.id)
            if(n.op=="wait" && (n.fields["SECONDS"]?.toLongOrNull() ?: 0) !in 1..31536000) issue("Wait must be 1 second to 1 year",n.id)
            if(n.op=="waitCondition" && (n.fields["SECONDS"]?.toLongOrNull() ?: 0)<60) issue("Condition checks require at least 60 seconds",n.id)
            if(n.op=="call" && (n.fields["WORKFLOW"] !in workflows || n.fields["WORKFLOW"]==d.id)) issue("Invalid or recursive workflow reference",n.id)
            if(n.op=="ask" && n.fields["KIND"] in setOf("text","number") && n.fields["SCOPE"]!="LOCAL") issue("Store responses locally, then SET persistent values",n.id)
            if(n.op in setOf("ask","checklist")) {
                val options=(n.fields["OPTIONS"] ?: "Option 1|Option 2").split('|').filter {it.isNotBlank()}
                if(options.size>100 || options.size!=options.toSet().size || (n.op=="checklist" || n.fields["KIND"]=="choice") && options.isEmpty())issue("Provide 1–100 distinct options/items",n.id)
                if(((n.fields["TIMEOUT"] ?: "900").toLongOrNull() ?: -1L) !in 0L..31536000L)issue("Invalid interaction timeout",n.id)
            }
            if(n.op=="set") { val v=declarations[Expressions.key(Scope.valueOf(n.fields["SCOPE"] ?: "LOCAL"),n.fields["NAME"] ?: "",d.id)]; val t=n.expressions["VALUE"]?.let { infer(it,n.id) }; if(v==null || t!=v.type) issue("Assignment needs a declared variable with matching type",n.id) }
        }
        try { Scheduling.validate(d.trigger); if(d.trigger.kind=="location" && d.trigger.locationId !in locations) issue("Select an existing location") } catch(e: IllegalArgumentException) { issue(e.message ?: "Invalid trigger") }
        // Imported IR must be acyclic. Looping is represented only by guarded loop nodes and durable frames.
        val visited=mutableSetOf<String>(); val visiting=mutableSetOf<String>()
        fun walk(id: String?, depth: Int) { if(id==null || id in visited) return; if(depth>128 || !visiting.add(id)) { issue("Cycle or excessive graph depth",id); return }; val n=d.nodes.find { it.id==id }; n?.branches?.values?.forEach { walk(it,depth+1) }; walk(n?.next,depth+1); visiting.remove(id); visited.add(id) }
        walk(d.entry,0); if(visited.size != d.nodes.size) issue("Disconnected executable nodes")
        return errors
    }
}
