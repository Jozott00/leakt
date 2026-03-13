package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.hasBody
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit

internal object LeaktUnsupportedLeakCheckFunctionChecker : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (!declaration.hasAnnotation(LeaktNames.leakCheckAnnotationClassId, context.session)) {
            return
        }

        val annotationSource =
            declaration.getAnnotationByClassId(LeaktNames.leakCheckAnnotationClassId, context.session)?.source
                ?: declaration.source

        if (!declaration.hasBody) {
            reporter.reportOn(
                annotationSource,
                FirErrors.UNSUPPORTED,
                "@LeakCheck requires a function body."
            )
        }

        if (declaration.isSuspend) {
            reporter.reportOn(
                annotationSource,
                FirErrors.UNSUPPORTED,
                "@LeakCheck does not support suspend functions."
            )
        }

        val returnType = declaration.returnTypeRef.coneType
        if (!returnType.isUnit) {
            reporter.reportOn(
                annotationSource,
                FirErrors.UNSUPPORTED,
                "@LeakCheck requires Unit return type."
            )
        }
    }
}
