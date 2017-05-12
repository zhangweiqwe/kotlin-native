/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.konan.ir

import java.io.File
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.SourceManager
import org.jetbrains.kotlin.ir.SourceRangeInfo
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrContainerExpressionBase
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBase
import org.jetbrains.kotlin.ir.symbols.IrFileSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.KotlinType

//-----------------------------------------------------------------------------//

interface IrReturnableBlock: IrBlock {
    val descriptor: CallableDescriptor
}

class IrReturnableBlockImpl(startOffset: Int, endOffset: Int, type: KotlinType,
                            override val descriptor: CallableDescriptor, origin: IrStatementOrigin? = null, val irFile: IrFile? = null)
    : IrContainerExpressionBase(startOffset, endOffset, type, origin), IrReturnableBlock {
    constructor(startOffset: Int, endOffset: Int, type: KotlinType,
                descriptor: CallableDescriptor, origin: IrStatementOrigin?, statements: List<IrStatement>, irFile: IrFile?) :
        this(startOffset, endOffset, type, descriptor, origin, irFile) {
        this.statements.addAll(statements)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitBlock(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        statements.forEach { it.accept(visitor, data) }
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        statements.forEachIndexed { i, irStatement ->
            statements[i] = irStatement.transform(transformer, data)
        }
    }
}

//-----------------------------------------------------------------------------//

class FileEntryImpl(override val name: String) : SourceManager.FileEntry {

    private val lineStartOffsets: IntArray

    //-------------------------------------------------------------------------//

    init {
        val file = File(name)
        val buffer = mutableListOf<Int>()
        var currentOffset = 0
        file.forEachLine { line ->
            buffer.add(currentOffset)
            currentOffset += line.length
        }
        lineStartOffsets = buffer.toIntArray()
    }

    //-------------------------------------------------------------------------//

    override fun getLineNumber(offset: Int): Int {
        val index = lineStartOffsets.binarySearch(offset)
        return if (index >= 0) index else -index - 1
    }

    //-------------------------------------------------------------------------//

    override fun getColumnNumber(offset: Int): Int {
        val lineNumber = getLineNumber(offset)
        return offset - lineStartOffsets[lineNumber]
    }

    //-------------------------------------------------------------------------//

    override val maxOffset: Int
        get() = TODO("not implemented")

    override fun getSourceRangeInfo(beginOffset: Int, endOffset: Int): SourceRangeInfo {
        TODO("not implemented")
    }
}

//-----------------------------------------------------------------------------//

class IrFileImpl(fileName: String) : IrFile {

    override val fileEntry = FileEntryImpl(fileName)

    //-------------------------------------------------------------------------//

    override val fileAnnotations: MutableList<AnnotationDescriptor>
        get() = TODO("not implemented")
    override val symbol: IrFileSymbol
        get() = TODO("not implemented")
    override val packageFragmentDescriptor: PackageFragmentDescriptor
        get() = TODO("not implemented")
    override val endOffset: Int
        get() = TODO("not implemented")
    override val startOffset: Int
        get() = TODO("not implemented")
    override val declarations: MutableList<IrDeclaration>
        get() = TODO("not implemented")
    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        TODO("not implemented")
    }
    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        TODO("not implemented")
    }
    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        TODO("not implemented")
    }
}

//-----------------------------------------------------------------------------//

interface IrSuspensionPoint : IrExpression {
    var suspensionPointIdParameter: IrVariable
    var result: IrExpression
    var resumeResult: IrExpression
}

interface IrSuspendableExpression : IrExpression {
    var suspensionPointId: IrExpression
    var result: IrExpression
}

class IrSuspensionPointImpl(startOffset: Int, endOffset: Int, type: KotlinType,
                            override var suspensionPointIdParameter: IrVariable,
                            override var result: IrExpression,
                            override var resumeResult: IrExpression)
    : IrExpressionBase(startOffset, endOffset, type), IrSuspensionPoint {

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitExpression(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        suspensionPointIdParameter.accept(visitor, data)
        result.accept(visitor, data)
        resumeResult.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        suspensionPointIdParameter = suspensionPointIdParameter.transform(transformer, data) as IrVariable
        result = result.transform(transformer, data)
        resumeResult = resumeResult.transform(transformer, data)
    }
}

class IrSuspendableExpressionImpl(startOffset: Int, endOffset: Int, type: KotlinType,
                                  override var suspensionPointId: IrExpression, override var result: IrExpression)
    : IrExpressionBase(startOffset, endOffset, type), IrSuspendableExpression {

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
            visitor.visitExpression(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        suspensionPointId.accept(visitor, data)
        result.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        suspensionPointId = suspensionPointId.transform(transformer, data)
        result = result.transform(transformer, data)
    }
}