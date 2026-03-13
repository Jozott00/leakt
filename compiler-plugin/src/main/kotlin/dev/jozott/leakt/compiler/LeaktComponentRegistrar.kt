package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
class LeaktComponentRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true
    override val pluginId: String = "dev.jozott.leakt"

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(LeaktIrGenerationExtension())
        FirExtensionRegistrarAdapter.registerExtension(LeaktFirExtensionRegistrar())
    }
}
