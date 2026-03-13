package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId

internal object LeaktNames {
    val leakCheckAnnotation = FqName("dev.jozott.leakt.LeakCheck")
    val leakSanitizerClass = FqName("dev.jozott.leakt.LeakSanitizer")
    val leakCheckAnnotationClassId = ClassId.topLevel(leakCheckAnnotation)
    val scopeFunction = Name.identifier("scope")
}
