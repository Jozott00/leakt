@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.getValueArgument
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class LeaktIrElementTransformer(private val pluginContext: IrPluginContext) : IrElementTransformerVoid() {
    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.hasAnnotation(LeaktNames.leakCheckAnnotation)) {
            return super.visitFunction(declaration)
        }

        val body = declaration.body ?: return super.visitFunction(declaration)
        declaration.body = wrapWithLeakCheck(declaration, body)

        return super.visitFunction(declaration)
    }

    private fun wrapWithLeakCheck(function: IrFunction, body: IrBody): IrBody {
        val leakReportingClassId = ClassId.topLevel(LeaktNames.leakReportingClass)
        val leakReportingClass = pluginContext.referenceClass(leakReportingClassId)
            ?: error("Could not find LeakReporting enum class")

        val withLeakCheckFunctions = pluginContext.referenceFunctions(
            CallableId(LeaktNames.leaktPackage, LeaktNames.withLeakCheckFunction)
        )
        val withLeakCheckFunction = withLeakCheckFunctions.firstOrNull {
            it.owner.parameters.count { parameter ->
                parameter.kind == IrParameterKind.Context || parameter.kind == IrParameterKind.Regular
            } == 2
        } ?: error("Could not find withLeakCheck(reporting, block). Found: ${withLeakCheckFunctions.map { it.owner.render() }}")

        val reportingEnumValue = resolveReportingEnumValue(function, leakReportingClass)

        return DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            val lambdaFunction = context.irFactory.buildFun {
                name = SpecialNames.ANONYMOUS
                returnType = pluginContext.irBuiltIns.unitType
                origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                visibility = function.visibility
            }.apply {
                parent = function
                val blockBody = context.irFactory.createBlockBody(body.startOffset, body.endOffset)
                val remapper = ReturnTargetRemapper(function.symbol, this.symbol)
                when (body) {
                    is IrBlockBody -> {
                        blockBody.statements.addAll(
                            body.statements.map { statement ->
                                statement.transform(remapper, null) as IrStatement
                            }
                        )
                    }

                    is IrExpressionBody -> {
                        blockBody.statements.add(body.expression.transform(remapper, null))
                    }

                    else -> error("Unsupported function body type for leak check transformation: ${body::class.simpleName}")
                }
                this.body = blockBody
            }

            val lambdaExpression = IrFunctionExpressionImpl(
                startOffset,
                endOffset,
                pluginContext.irBuiltIns.functionN(0).typeWith(pluginContext.irBuiltIns.unitType),
                lambdaFunction,
                IrStatementOrigin.LAMBDA
            )

            +irCall(withLeakCheckFunction.owner.symbol).apply {
                arguments[0] = reportingEnumValue
                arguments[1] = lambdaExpression
            }
        }
    }

    private fun resolveReportingEnumValue(
        function: IrFunction,
        leakReportingClass: IrClassSymbol,
    ): IrExpression {
        val annotation = function.getAnnotation(LeaktNames.leakCheckAnnotation)
        val explicitValue = annotation?.getValueArgument(LeaktNames.reportingArgument) as? IrGetEnumValue
        val explicitEntryName = explicitValue?.symbol?.owner?.name
        val selectedEntry = findLeakReportingEntry(leakReportingClass, explicitEntryName ?: LeaktNames.firstOnlyEnumEntry)
            ?: error("Could not find LeakReporting.${explicitEntryName ?: LeaktNames.firstOnlyEnumEntry}")
        return IrGetEnumValueImpl(
            function.startOffset,
            function.endOffset,
            leakReportingClass.defaultType,
            selectedEntry.symbol,
        )
    }

    private fun findLeakReportingEntry(
        leakReportingClass: IrClassSymbol,
        entryName: Name,
    ): IrEnumEntry? {
        return leakReportingClass.owner.declarations
            .filterIsInstance<IrEnumEntry>()
            .firstOrNull { it.name == entryName }
    }

    private class ReturnTargetRemapper(
        private val from: IrReturnTargetSymbol,
        private val to: IrReturnTargetSymbol,
    ) : IrElementTransformerVoid() {
        override fun visitReturn(expression: IrReturn): IrExpression {
            expression.transformChildren(this, null)
            if (expression.returnTargetSymbol == from) {
                expression.returnTargetSymbol = to
            }
            return expression
        }
    }
}
