package dev.jozott.leakt

public fun leakCheckedTest(name: String? = null, block: () -> Unit) {
    LeakSanitizer.scope(name = name, block = block)
}
