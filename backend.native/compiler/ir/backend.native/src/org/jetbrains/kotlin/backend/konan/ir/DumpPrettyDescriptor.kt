package org.jetbrains.kotlin.backend.konan.ir

import org.jetbrains.kotlin.backend.konan.serialization.printType
import org.jetbrains.kotlin.serialization.Flags
import org.jetbrains.kotlin.serialization.KonanLinkData
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.supertypes
import org.jetbrains.kotlin.utils.Printer

//-----------------------------------------------------------------------------//

class PackageFragmentPrinter(val packageFragment: KonanLinkData.PackageFragment, out: Appendable) {

    val printer            = Printer(out, "  ")
    val stringTable        = packageFragment.stringTable!!
    val qualifiedNameTable = packageFragment.nameTable!!

    //-------------------------------------------------------------------------//

    fun print() {
        printer.println("\n//--- Classes ----------------------------------------//\n")
        val protoClasses = packageFragment.classes.classesOrBuilderList                     // ProtoBuf classes
        protoClasses.forEach { protoClass ->
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
        val shortName     = getShortName(classFqNameId)
        val parentName    = getParentName(classFqNameId)
        val modality      = modalityToString(Flags.MODALITY.get(flags))
        printer.print("class $modality")

        printTypeParameters(protoClass.typeParameterList)
        printer.println("$parentName.$shortName {")

        val protoSupertypes = protoClass.supertypeList
        protoSupertypes.forEach(::printType)

        val protoConstructors = protoClass.constructorList
        protoConstructors.forEach { protoConstructor ->
            printConstructor(protoConstructor)
        }

        val protoFunctions = protoClass.functionList
        protoFunctions.forEach { protoFunction ->
            printFunction(protoFunction)
        }

        val protoProperties = protoClass.propertyList
        protoProperties.forEach { protoProperty ->
            printProperty(protoProperty)
        }

        printer.println("}\n")
    }

    //-------------------------------------------------------------------------//

    fun printConstructor(protoConstructor: ProtoBuf.ConstructorOrBuilder) {
        val flags          = protoConstructor.flags
        val visibility     = visibilityToString(Flags.VISIBILITY.get(flags))
        printer.print("  ${visibility}constructor")

        printValueParameters(protoConstructor.valueParameterList)
        printer.println()
    }

    //-------------------------------------------------------------------------//

    fun printFunction(protoFunction: ProtoBuf.FunctionOrBuilder) {
        val flags          = protoFunction.flags
        val functionNameId = protoFunction.name
        val shortName      = stringTable.getString(functionNameId)
        val visibility     = visibilityToString(Flags.VISIBILITY.get(flags))
        printer.print("  ${visibility}fun $shortName")

        printTypeParameters(protoFunction.typeParameterList)
        printValueParameters(protoFunction.valueParameterList)
        printer.println()
    }

    //-------------------------------------------------------------------------//

    fun printProperty(protoProperty: ProtoBuf.PropertyOrBuilder) {
        val flags          = protoProperty.flags
        val propertyNameId = protoProperty.name
        val shortName      = stringTable.getString(propertyNameId)
        val isVar          = if (Flags.IS_VAR.get(flags)) "var" else "val"
        val modality       = modalityToString(Flags.MODALITY.get(flags))
        val visibility     = visibilityToString(Flags.VISIBILITY.get(flags))
        val returnType     = typeToString(protoProperty.returnType)

        printer.println("  $modality$visibility$isVar $shortName: $returnType")
    }

    //-------------------------------------------------------------------------//

    fun printTypeParameters(typeParameters: List<ProtoBuf.TypeParameterOrBuilder>) {
        if (typeParameters.isEmpty()) return

        printer.print("<")
        typeParameters.dropLast(1).forEach { typeParameter ->
            printTypeParameter(typeParameter)
            printer.print(", ")
        }
        typeParameters.last().let { typeParameter ->
            printTypeParameter(typeParameter)
        }
        printer.print(">")
    }

    //-------------------------------------------------------------------------//

    fun printTypeParameter(protoTypeParameter: ProtoBuf.TypeParameterOrBuilder) {
        val parameterName = stringTable.getString(protoTypeParameter.name)
        val upperBounds   = upperBoundsToString(protoTypeParameter.upperBoundList)
        val isReified     = if (protoTypeParameter.reified) "reified " else ""
        val variance      = varianceToString(protoTypeParameter.variance)

        printer.print("$isReified$variance$parameterName$upperBounds")
    }

    //-------------------------------------------------------------------------//

    fun printValueParameters(valueParameters: List<ProtoBuf.ValueParameterOrBuilder>) {
        if (valueParameters.isEmpty()) return

        printer.print("(")
        valueParameters.dropLast(1).forEach { valueParameter ->
            printValueParameter(valueParameter)
            printer.print(", ")
        }
        valueParameters.last().let { valueParameter ->
            printValueParameter(valueParameter)
        }
        printer.print(")")
    }

    //-------------------------------------------------------------------------//

    fun printValueParameter(protoValueParameter: ProtoBuf.ValueParameterOrBuilder) {
        val parameterName = stringTable.getString(protoValueParameter.name)
        val type = typeToString(protoValueParameter.type)

        printer.print("$parameterName: $type")
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

    fun upperBoundsToString(upperBounds: List<ProtoBuf.Type>): String {
        var buff = ""
        upperBounds.forEach { upperBound ->
            buff += typeToString(upperBound)
        }
        return buff
    }

    //-------------------------------------------------------------------------//

    fun typeToString(type: ProtoBuf.Type): String {
        var buff = "type"
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
