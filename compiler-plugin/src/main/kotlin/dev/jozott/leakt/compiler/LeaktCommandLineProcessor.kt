package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class LeaktCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.jozott.leakt"
    override val pluginOptions: Collection<AbstractCliOption> = emptyList()

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        // No options yet
    }
}
