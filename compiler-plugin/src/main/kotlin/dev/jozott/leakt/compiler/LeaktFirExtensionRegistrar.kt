package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class LeaktFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::LeaktFirAdditionalCheckersExtension
    }
}
