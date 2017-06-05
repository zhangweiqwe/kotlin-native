package org.jetbrains.kotlin.backend.konan.ir

import org.jetbrains.kotlin.serialization.Flags
import org.jetbrains.kotlin.serialization.KonanLinkData
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.utils.Printer

//-----------------------------------------------------------------------------//

class PackageFragmentPrinter(val packageFragment: KonanLinkData.PackageFragment, out: Appendable) {

    val printer            = Printer(out, "  ")
    val stringTable        = packageFragment.stringTable!!
    val qualifiedNameTable = packageFragment.nameTable!!
    var typeTable: ProtoBuf.TypeTable? = null

    //-------------------------------------------------------------------------//

    fun print() {
        printer.println("\n//--- Classes ----------------------------------------//\n")
        val protoClasses = packageFragment.classes.classesOrBuilderList                     // ProtoBuf classes
        protoClasses.forEach { protoClass ->
            typeTable = protoClass.typeTable
            printClass(protoClass)
        }

        printer.println("\n//--- Functions --------------------------------------//\n")
        val protoFunctions = packageFragment.`package`.functionOrBuilderList
        protoFunctions.forEach { protoFunction ->
            printFunction(protoFunction)
        }

        printer.println("\n//--- Properties -------------------------------------//\n")
        val protoProperties = packageFragment.`package`.propertyOrBuilderList
        protoProperties.forEach { protoProperty ->
            printProperty(protoProperty)
        }

        printer.println("Ok")
    }

    //-------------------------------------------------------------------------//

    fun printClass(protoClass: ProtoBuf.ClassOrBuilder) {
        val classFqNameId = protoClass.fqName
        val flags         = protoClass.flags
        val className     = getShortName(classFqNameId)
        val modality      = modalityToString(Flags.MODALITY.get(flags))

        val protoConstructors = protoClass.constructorList
        val protoFunctions    = protoClass.functionList
        val protoProperties   = protoClass.propertyList

        printer.print("class $modality")
        printer.print(typeParametersToString(protoClass.typeParameterList))
        printer.print(className)
        printer.print(primaryConstructorToString(protoConstructors))
        printer.print(supertypesToString(protoClass.supertypeIdList))

        printer.println(" {")
        printer.print(secondaryConstructorsToString(protoConstructors))
        protoFunctions.forEach  { printFunction(it) }
        protoProperties.forEach { printProperty(it) }
        printer.println("}\n")

        // protoClass.typeAliasList
        // protoClass.sealedSubclassFqNameList
        // protoClass.companionObjectName
        // protoClass.enumEntryList
        // protoClass.nestedClassNameList
    }

    //-------------------------------------------------------------------------//

    fun printFunction(protoFunction: ProtoBuf.FunctionOrBuilder) {
        val flags          = protoFunction.flags
        val functionNameId = protoFunction.name
        val shortName      = stringTable.getString(functionNameId)
        val visibility     = visibilityToString(Flags.VISIBILITY.get(flags))
        printer.print("  ${visibility}fun $shortName")

        printer.println(typeParametersToString(protoFunction.typeParameterList))
        printer.println(valueParametersToString(protoFunction.valueParameterList))
        printer.println()
    }

    //-------------------------------------------------------------------------//

    fun printProperty(protoProperty: ProtoBuf.PropertyOrBuilder) {
        val nameId     = protoProperty.name
        val name       = stringTable.getString(nameId)
        val flags      = protoProperty.flags
        val isVar      = if (Flags.IS_VAR.get(flags)) "var" else "val"
        val modality   = modalityToString(Flags.MODALITY.get(flags))
        val visibility = visibilityToString(Flags.VISIBILITY.get(flags))
        val returnType = typeToString(protoProperty.returnTypeId)

        printer.println("  $modality$visibility$isVar $name: $returnType")
    }

    //-------------------------------------------------------------------------//

    fun supertypesToString(supertypesId: List<Int>): String {
        var buff = ": "
        supertypesId.dropLast(1).forEach { supertypeId ->
            val supertype = typeToString(supertypeId)
            if (supertype != "Any") buff += "$supertype, "
        }
        supertypesId.last().let { supertypeId ->
            val supertype = typeToString(supertypeId)
            if (supertype != "Any") buff += supertype
        }

        if (buff == ": ") return ""
        return buff
    }

    //-------------------------------------------------------------------------//

    fun primaryConstructorToString(protoConstructors: List<ProtoBuf.ConstructorOrBuilder>): String {
        val primaryConstructor = protoConstructors.firstOrNull { protoConstructor ->
            !Flags.IS_SECONDARY.get(protoConstructor.flags)
        } ?: return ""

        val flags = primaryConstructor.flags
        val visibility = visibilityToString(Flags.VISIBILITY.get(flags))
        val valueParameters = constructorValueParametersToString(primaryConstructor.valueParameterList)
        return "$visibility$valueParameters"
    }

    //-------------------------------------------------------------------------//

    fun secondaryConstructorsToString(protoConstructors: List<ProtoBuf.ConstructorOrBuilder>): String {
        val secondaryConstructors = protoConstructors.filter { protoConstructor ->
            Flags.IS_SECONDARY.get(protoConstructor.flags)
        }

        var buff = ""
        secondaryConstructors.forEach { protoConstructor ->
            val flags       = protoConstructor.flags
            val visibility  = visibilityToString(Flags.VISIBILITY.get(flags))
            val valueParameters = valueParametersToString(protoConstructor.valueParameterList)
            buff += "  ${visibility}constructor$valueParameters\n"
        }
        return buff
    }

    //-------------------------------------------------------------------------//

    fun typeParametersToString(typeParameters: List<ProtoBuf.TypeParameterOrBuilder>): String {
        if (typeParameters.isEmpty()) return ""

        var buff = "<"
        typeParameters.dropLast(1).forEach { buff += typeParameterToString(it) + ", " }
        typeParameters.last().let          { buff += typeParameterToString(it) }
        buff += "> "
        return buff
    }

    //-------------------------------------------------------------------------//

    fun typeParameterToString(protoTypeParameter: ProtoBuf.TypeParameterOrBuilder): String {
        val parameterName = stringTable.getString(protoTypeParameter.name)
        val upperBounds   = upperBoundsToString(protoTypeParameter.upperBoundIdList)
        val isReified     = if (protoTypeParameter.reified) "reified " else ""
        val variance      = varianceToString(protoTypeParameter.variance)

        return "$isReified$variance$parameterName$upperBounds"
    }

    //-------------------------------------------------------------------------//

    fun constructorValueParametersToString(valueParameters: List<ProtoBuf.ValueParameterOrBuilder>): String {
        if (valueParameters.isEmpty()) return ""

        var buff = "("
        valueParameters.dropLast(1).forEach { valueParameter ->
            val flags = valueParameter.flags
            val isVar = if (Flags.IS_VAR.get(flags)) "var" else "val"
            val parameter = valueParameterToString(valueParameter)
            buff += "$isVar $parameter, "
        }
        valueParameters.last().let { valueParameter ->
            val flags = valueParameter.flags
            val isVar = if (Flags.IS_VAR.get(flags)) "var" else "val"
            val parameter = valueParameterToString(valueParameter)
            buff += "$isVar $parameter"
        }
        buff += ")"
        return buff
    }

    //-------------------------------------------------------------------------//

    fun valueParametersToString(valueParameters: List<ProtoBuf.ValueParameterOrBuilder>): String {
        if (valueParameters.isEmpty()) return ""

        var buff = "("
        valueParameters.dropLast(1).forEach { valueParameter ->
            val parameter = valueParameterToString(valueParameter)
            buff += "$parameter, "
        }
        valueParameters.last().let { valueParameter ->
            val parameter = valueParameterToString(valueParameter)
            buff += parameter
        }
        buff += ")"
        return buff
    }

    //-------------------------------------------------------------------------//

    fun valueParameterToString(protoValueParameter: ProtoBuf.ValueParameterOrBuilder): String {
        val parameterName = stringTable.getString(protoValueParameter.name)
        val type = typeToString(protoValueParameter.typeId)
        return "$parameterName: $type"
    }

    //--- Helpers -------------------------------------------------------------//

    fun getShortName(id: Int): String {
        val shortQualifiedName = qualifiedNameTable.getQualifiedName(id)
        val shortStringId      = shortQualifiedName.shortName
        val shortName          = stringTable.getString(shortStringId)
        return shortName
    }

    //-------------------------------------------------------------------------//

    fun getParentName(id: Int): String {
        val childQualifiedName  = qualifiedNameTable.getQualifiedName(id)
        val parentQualifiedId   = childQualifiedName.parentQualifiedName
        val parentQualifiedName = qualifiedNameTable.getQualifiedName(parentQualifiedId)
        val parentStringId      = parentQualifiedName.shortName
        val parentName          = stringTable.getString(parentStringId)
        return parentName
    }

    //-------------------------------------------------------------------------//

    fun varianceToString(variance: ProtoBuf.TypeParameter.Variance): String {
        if (variance == ProtoBuf.TypeParameter.Variance.INV) return ""
        return variance.toString().toLowerCase()
    }

    //-------------------------------------------------------------------------//

    fun upperBoundsToString(upperBounds: List<Int>): String {
        var buff = ""
        upperBounds.forEach { upperBound ->
            buff += ": " + typeToString(upperBound)
        }
        return buff
    }

    //-------------------------------------------------------------------------//

    fun typeToString(typeId: Int): String {
        val type        = typeTable!!.getType(typeId)
        val className   = qualifiedNameTable.getQualifiedName(type.className)
        val shortNameId = className.shortName
        val shortName   = stringTable.getString(shortNameId)

        var buff = shortName
        if (type.nullable) buff += "?"
        return buff
    }

    //-------------------------------------------------------------------------//

    fun modalityToString(modality: ProtoBuf.Modality) =
        when (modality) {
            ProtoBuf.Modality.FINAL    -> ""
            ProtoBuf.Modality.OPEN     -> "open "
            ProtoBuf.Modality.ABSTRACT -> "abstract "
            ProtoBuf.Modality.SEALED   -> "sealed "
        }

    //-------------------------------------------------------------------------//

    fun visibilityToString(visibility: ProtoBuf.Visibility) =
        when (visibility) {
            ProtoBuf.Visibility.INTERNAL        -> "internal "
            ProtoBuf.Visibility.PRIVATE         -> "private "
            ProtoBuf.Visibility.PROTECTED       -> "protected "
            ProtoBuf.Visibility.PUBLIC          -> ""
            ProtoBuf.Visibility.PRIVATE_TO_THIS -> "private "
            ProtoBuf.Visibility.LOCAL           -> "local "
        }
}