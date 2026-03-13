package dev.jozott.leakt

public expect object LeakSanitizer {
    public fun disable()
    public fun enable()
    public fun checkLeaks(): Boolean
    public fun scope(name: String? = null, block: () -> Unit)
}
