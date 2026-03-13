package dev.jozott.leakt.compiler

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId

internal object LeaktNames {
    val leaktPackage = FqName("dev.jozott.leakt")
    val leakCheckAnnotation = FqName("dev.jozott.leakt.LeakCheck")
    val leakSanitizerClass = FqName("dev.jozott.leakt.LeakSanitizer")
    val leakReportingClass = FqName("dev.jozott.leakt.LeakReporting")
    val leakCheckAnnotationClassId = ClassId.topLevel(leakCheckAnnotation)
    val withLeakCheckFunction = Name.identifier("withLeakCheck")
    val scopeFunction = Name.identifier("scope")
    val reportingArgument = Name.identifier("reporting")
    val firstOnlyEnumEntry = Name.identifier("FIRST_ONLY")
}
