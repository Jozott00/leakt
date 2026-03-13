package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers

internal object LeaktDeclarationCheckers : DeclarationCheckers() {
    override val functionCheckers = setOf(LeaktUnsupportedLeakCheckFunctionChecker)
}
