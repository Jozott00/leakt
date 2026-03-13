package dev.jozott.leaktest

public fun leakCheckedTest(name: String? = null, block: () -> Unit) {
    LeakSanitizer.scope(name = name, block = block)
}
