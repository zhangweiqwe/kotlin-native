package org.jetbrains.kotlin.konan.jsinterop.tool

import platform.posix.*

// This is (as of now) a poor man's IDL representation.

interface Type 
interface Member

object idlVoid: Type
object idlInt: Type
object idlFloat: Type
object idlDouble: Type
object idlString: Type
object idlObject: Type
object idlFunction: Type

data class Attribute(val name: String, val type: Type, 
    val readOnly: Boolean = false): Member

data class Arg(val name: String, val type: Type)

class Operation(val name: String, val returnType: Type, vararg val args: Arg): Member

data class idlInterfaceRef(val name: String): Type
class Interface(val name: String, vararg val members: Member)

fun kotlinHeader(): String {
    val packageName = "html5.minimal"
    return  "package $packageName\n" +
            "import kotlinx.wasm.jsinterop.*\n"
}

fun Type.toKotlinType(argName: String? = null): String {
    return when (this) {
        is idlVoid -> "Unit"
        is idlInt -> "Int"
        is idlFloat -> "Float"
        is idlDouble -> "Double"
        is idlString -> "String"
        is idlObject -> "JsValue"
        is idlFunction -> "KtFunction<R${argName!!}>"
        is idlInterfaceRef -> name
        else -> error("Unexpected type")
    }
}

fun Arg.wasmMapping(): String {
    return when (type) {
        is idlVoid -> error("An arg can not be idlVoid")
        is idlInt -> name
        is idlFloat -> name
        is idlDouble -> "doubleUpper($name), doubleLower($name)"
        is idlString -> "stringPointer($name), stringLengthBytes($name)"
        is idlObject -> TODO("implement me")
        is idlFunction -> "wrapFunction<R$name>($name), ArenaManager.currentArena"
        is idlInterfaceRef -> TODO("Implement me")
        else -> error("Unexpected type")
    }
}

fun Type.wasmReturnArg(): String = 
    when (this) {
        is idlVoid -> "ArenaManager.currentArena" // TODO: optimize.
        is idlInt -> "ArenaManager.currentArena"
        is idlFloat -> "ArenaManager.currentArena"
        is idlDouble -> "resultPtr"
        is idlString -> "ArenaManager.currentArena"
        is idlObject -> "ArenaManager.currentArena"
        is idlFunction -> "ArenaManager.currentArena"
        is idlInterfaceRef -> "ArenaManager.currentArena"
        else -> error("Unexpected type")
    }
val Operation.wasmReturnArg: String get() = returnType.wasmReturnArg()
val Attribute.wasmReturnArg: String get() = type.wasmReturnArg()

fun Arg.wasmArgNames(): List<String> {
    return when (type) {
        is idlVoid -> error("An arg can not be idlVoid")
        is idlInt -> listOf(name)
        is idlFloat -> listOf(name)
        is idlDouble -> listOf("${name}Upper", "${name}Lower")
        is idlString -> listOf("${name}Ptr", "${name}Len")
        is idlObject -> TODO("implement me (idlObject)")
        is idlFunction -> listOf("${name}Index", "${name}ResultArena")
        is idlInterfaceRef -> TODO("Implement me (idlInterfaceRef)")
        else -> error("Unexpected type")
    }
}

fun Type.wasmReturnMapping(value: String): String {
    return when (this) {
        is idlVoid -> ""
        is idlInt -> value
        is idlFloat -> value
        is idlDouble -> "doubleResult"
        is idlString -> "TODO(\"Implement me\")"
        is idlObject -> "JsValue(ArenaManager.currentArena, $value)"
        is idlFunction -> "TODO(\"Implement me\")"
        is idlInterfaceRef -> "$name(ArenaManager.currentArena, $value)"
        else -> error("Unexpected type")
    }
}

fun wasmFunctionName(functionName: String, interfaceName: String)
    = "knjs__${interfaceName}_$functionName"

fun wasmSetterName(propertyName: String, interfaceName: String)
    = "knjs_set__${interfaceName}_$propertyName"

fun wasmGetterName(propertyName: String, interfaceName: String)
    = "knjs_get__${interfaceName}_$propertyName"

val Operation.kotlinTypeParameters: String get() {
    val lambdaRetTypes = args.filter { it.type is idlFunction }
        .map { "R${it.name}" }. joinToString(", ")
    return if (lambdaRetTypes == "") "" else "<$lambdaRetTypes>"
}

val Interface.wasmReceiverArgs get() = 
    if (this.name != "__Global") 
        listOf("this.arena", "this.index")
    else emptyList()

fun Operation.generateKotlin(parent: Interface): String {
    val argList = args.map {
        "${it.name}: ${it.type.toKotlinType(it.name)}"
    }.joinToString(",")

    val wasmArgList = (parent.wasmReceiverArgs + args.map{it.wasmMapping()} + wasmReturnArg).joinToString(",")

    // TODO: there can be multiple Rs.
    return "  fun $kotlinTypeParameters $name(" + 
    argList + 
    "): ${returnType.toKotlinType()} {\n" +
    "    ${if (returnType == idlDouble) "val resultPtr = allocateDouble()" else ""}\n" +
    "    ${if (returnType != idlVoid) "val wasmRetVal = " else ""}${wasmFunctionName(name, parent.name)}($wasmArgList)\n" +
    "    ${if (returnType == idlDouble) "val doubleResult = heapDouble(wasmRetVal)" else ""}\n" +
    "    ${if (returnType == idlDouble) "deallocateDouble(resultPtr)" else ""}\n" +
    "    return ${returnType.wasmReturnMapping("wasmRetVal")}\n"+
    "  }\n"
}

fun Attribute.generateKotlinSetter(parent: Interface): String {
    val kotlinType = type.toKotlinType(name)
    return "    set(value: $kotlinType) {\n" +
    "      ${wasmSetterName(name, parent.name)}(" +
        (parent.wasmReceiverArgs + Arg("value", type).wasmMapping()).joinToString(", ") + 
        ")\n" + 
    "    }\n"
}

fun Attribute.generateKotlinGetter(parent: Interface): String {
    return "    get() {\n" +
    "      val wasmRetVal = ${wasmGetterName(name, parent.name)}(${(parent.wasmReceiverArgs + wasmReturnArg).joinToString(", ")})\n" + 
    "      return ${type.wasmReturnMapping("wasmRetVal")}\n"+
    "    }\n" +
    "  \n"
}

fun Attribute.generateKotlin(parent: Interface): String {
    val kotlinType = type.toKotlinType(name)
    val varOrVal = if (readOnly) "val" else "var"
    return "  $varOrVal $name: $kotlinType\n" +
    generateKotlinGetter(parent) +
    if (!readOnly) generateKotlinSetter(parent) else ""
}

val Interface.wasmTypedReceiverArgs get() = 
    if (this.name != "__Global") 
        listOf("arena: Int", "index: Int")
    else emptyList()
/*
val Type.wasmReturnTypeArgs get() =
    when (this) {
        idlDouble -> listOf("returnPtr: Int")
        else -> emptyList()
    }
val Operation.wasmReturnTypeArgs get() = returnType.wasmReturnTypeArgs

val Attribute.wasmReturnTypeArgs get() = type.wasmReturnTypeArgs
*/

fun Operation.generateWasmStub(parent: Interface): String {
    val wasmName = wasmFunctionName(this.name, parent.name)
    val allArgs = (parent.wasmTypedReceiverArgs + args.toList().wasmTypedMapping() + wasmTypedReturnMapping).joinToString(", ")
    return "@SymbolName(\"$wasmName\")\n" +
    "external public fun $wasmName($allArgs): ${returnType.wasmReturnTypeMapping()}\n\n"
}
fun Attribute.generateWasmSetterStub(parent: Interface): String {
    val wasmSetter = wasmSetterName(this.name, parent.name)
    val allArgs = (parent.wasmTypedReceiverArgs + Arg("value", this.type).wasmTypedMapping()).joinToString(", ")
    return "@SymbolName(\"$wasmSetter\")\n" +
    "external public fun $wasmSetter($allArgs): Unit\n\n"
}
fun Attribute.generateWasmGetterStub(parent: Interface): String {
    val wasmGetter = wasmGetterName(this.name, parent.name)
    val allArgs = (parent.wasmTypedReceiverArgs + wasmTypedReturnMapping).joinToString(", ")
    return "@SymbolName(\"$wasmGetter\")\n" +
    "external public fun $wasmGetter($allArgs): Int\n\n"
}
fun Attribute.generateWasmStubs(parent: Interface) =
    generateWasmGetterStub(parent) +
    if (!readOnly) generateWasmSetterStub(parent) else ""

// TODO: consider using virtual mathods
fun Member.generateKotlin(parent: Interface): String {
    return when (this) {
        is Operation -> this.generateKotlin(parent)
        is Attribute -> this.generateKotlin(parent)
        else -> error("Unexpected member")
    }
}

// TODO: consider using virtual mathods
fun Member.generateWasmStub(parent: Interface) =
    when (this) {
        is Operation -> this.generateWasmStub(parent)
        is Attribute -> this.generateWasmStubs(parent)
        else -> error("Unexpected member")

    }

fun Arg.wasmTypedMapping() 
    = this.wasmArgNames().map { "$it: Int" } .joinToString(", ")

fun Type.wasmTypedReturnMapping(): String =
    when (this) {
        idlDouble -> "resultPtr: Int"
        else -> "resultArena: Int"
            // TODO: all types.
    }

val Operation.wasmTypedReturnMapping get() = returnType.wasmTypedReturnMapping()

val Attribute.wasmTypedReturnMapping get() = type.wasmTypedReturnMapping()

fun List<Arg>.wasmTypedMapping()
    = this.map{ it.wasmTypedMapping() }

// TODO: more complex return types, such as returning a pair of Ints
// will require a more complex approach.
fun Type.wasmReturnTypeMapping()
    = if (this == idlVoid) "Unit" else "Int"

fun Interface.generateMemberWasmStubs() =
    members.map {
        it.generateWasmStub(this)
    }.joinToString("")

fun Interface.generateKotlinMembers() =
    members.map {
        it.generateKotlin(this)
    }.joinToString("")

fun Interface.generateKotlinClassHeader() =
    "open class $name(arena: Int, index: Int): JsValue(arena, index) {\n" +
    "  constructor(jsValue: JsValue): this(jsValue.arena, jsValue.index)\n"
    
fun Interface.generateKotlinClassFooter() =
    "}\n"

fun Interface.generateKotlinClassConverter() =
    "val JsValue.as$name: $name\n" +
    "  get() {\n" +
    "    return $name(this.arena, this.index)\n"+
    "  }\n"

fun Interface.generateKotlin(): String {

    fun String.skipForGlobal() = 
        if (this@generateKotlin.name != "__Global") this 
        else ""

    return generateMemberWasmStubs() + 
        generateKotlinClassHeader().skipForGlobal() +
        generateKotlinMembers() + 
        generateKotlinClassFooter().skipForGlobal() +
        generateKotlinClassConverter().skipForGlobal()
}

fun generateKotlin(interfaces: List<Interface>) =
    kotlinHeader() + 
    interfaces.map {
        it.generateKotlin()
    }.joinToString("\n") +
    "fun <R> setInterval(interval: Int, lambda: KtFunction<R>) = setInterval(lambda, interval)\n"

/////////////////////////////////////////////////////////

fun Arg.composeWasmArgs(): String {
    return when (type) {
        is idlVoid -> error("An arg can not be idlVoid")
        is idlInt -> ""
        is idlFloat -> ""
        is idlDouble -> 
            "    var $name = twoIntsToDouble(${name}Upper, ${name}Lower);\n"
        is idlString -> 
            "    var $name = toUTF16String(${name}Ptr, ${name}Len);\n"
        is idlObject -> TODO("implement me")
        is idlFunction -> 
            "    var $name = konan_dependencies.env.Konan_js_wrapLambda(lambdaResultArena, ${name}Index);\n"

        is idlInterfaceRef -> TODO("Implement me")
        else -> error("Unexpected type")
    }
}

val Interface.receiver get() = 
    if (this.name == "__Global") "" else  "kotlinObject(arena, obj)."


val Interface.wasmReceiverArgName get() =
    if (this.name != "__Global") listOf("arena", "obj") else emptyList()

val Operation.wasmReturnArgName get() = 
    returnType.wasmReturnArgName

val Attribute.wasmReturnArgName get() = 
    type.wasmReturnArgName

val Type.wasmReturnArgName get() =
    when (this) {
        is idlVoid -> emptyList()
        is idlInt -> emptyList()
        is idlFloat -> emptyList()
        is idlDouble -> listOf("resultPtr")
        is idlString -> listOf("resultArena")
        is idlObject -> listOf("resultArena")
        is idlInterfaceRef -> listOf("resultArena")
        else -> error("Unexpected type: $this")
    }

val Type.wasmReturnExpression get() =
    when(this) {
        is idlVoid -> ""
        is idlInt -> "result"
        is idlFloat -> "result" // TODO: can we really pass floats as is?
        is idlDouble -> "doubleToHeap(result, resultPtr)"
        is idlString -> "toArena(resultArena, result)"
        is idlObject -> "toArena(resultArena, result)"
        is idlInterfaceRef -> "toArena(resultArena, result)"
        else -> error("Unexpected type: $this")
    }

fun Operation.generateJs(parent: Interface): String {
    val allArgs = parent.wasmReceiverArgName + args.map { it.wasmArgNames() }.flatten() + wasmReturnArgName
    val wasmMapping = allArgs.joinToString(", ")
    val argList = args.map { it.name }. joinToString(", ")
    val composedArgsList = args.map { it.composeWasmArgs() }. joinToString("")

    return "\n  ${wasmFunctionName(this.name, parent.name)}: function($wasmMapping) {\n" +
        composedArgsList +
        "    var result = ${parent.receiver}$name($argList);\n" +
        "    return ${returnType.wasmReturnExpression};\n" +
    "  }"
}

fun Attribute.generateJsSetter(parent: Interface): String {
    val valueArg = Arg("value", type)
    val allArgs = parent.wasmReceiverArgName + valueArg.wasmArgNames()
    val wasmMapping = allArgs.joinToString(", ")
    return "\n  ${wasmSetterName(name, parent.name)}: function($wasmMapping) {\n" +
        valueArg.composeWasmArgs() +
        "    ${parent.receiver}$name = value;\n" +
    "  }"
}

fun Attribute.generateJsGetter(parent: Interface): String {
    val allArgs = parent.wasmReceiverArgName + wasmReturnArgName
    val wasmMapping = allArgs.joinToString(", ")
    return "\n  ${wasmGetterName(name, parent.name)}: function($wasmMapping) {\n" +
        "    var result = ${parent.receiver}$name;\n" +
        "    return ${type.wasmReturnExpression};\n" +
    "  }"
}

fun Attribute.generateJs(parent: Interface) =
    generateJsGetter(parent) + 
    if (!readOnly) ",\n${generateJsSetter(parent)}" else ""

fun Member.generateJs(parent: Interface): String {
    return when (this) {
        is Operation -> this.generateJs(parent)
        is Attribute -> this.generateJs(parent)
        else -> error("Unexpected member")
    }
}

fun generateJs(interfaces: List<Interface>): String =
    "konan.libraries.push ({\n" +
    interfaces.map { interf ->
        interf.members.map { member -> 
            member.generateJs(interf) 
        }
    }.flatten() .joinToString(",\n") + 
    "\n})\n"

fun String.writeToFile(name: String) {
    val file = fopen(name, "wb")!!
    fputs(this, file)
    fclose(file)
}
fun main(args: Array<String>) {
    generateKotlin(all).writeToFile("kotlin_stubs.kt")
    generateJs(all).writeToFile("js_stubs.js")
}

