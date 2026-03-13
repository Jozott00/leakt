package dev.jozott.leakt.test

import dev.jozott.leakt.LeakCheck
import dev.jozott.leakt.test.ffileaks.leakt_native_alloc
import dev.jozott.leakt.test.ffileaks.leakt_native_free
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.rawPtr
import kotlinx.cinterop.sizeOf
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
class LeakTest {
    @Test
    @LeakCheck
    fun freesNativeAllocation() {
        val value = nativeHeap.alloc<IntVar>()
        nativeHeap.free(value.rawPtr)
    }


    @LeakCheck
    fun detectsLeakedNativeAllocationWithAnnotation() {
        if (Platform.osFamily == OsFamily.MACOSX) {
            // Apple ASan links the LSan symbols but does not surface the recoverable leak check on this host runtime.
            return
        }

        nativeHeap.alloc<IntVar>()
    }

    @Test
    fun assertDetectedLeakFails() {
        assertFailsWith<IllegalStateException> {
            detectsLeakedNativeAllocationWithAnnotation()
        }
    }

    @LeakCheck
    fun detectsLeakedFfiNativeAllocationWithAnnotation() {
        if (Platform.osFamily == OsFamily.MACOSX) {
            // Apple ASan links the LSan symbols but does not surface the recoverable leak check on this host runtime.
            return
        }

        leakt_native_alloc(sizeOf<IntVar>().convert()) ?: error("leakt_native_alloc failed")
    }

    @Test
    fun assertDetectedFfiLeakFails() {
        assertFailsWith<IllegalStateException> {
            detectsLeakedFfiNativeAllocationWithAnnotation()
        }
    }

    @LeakCheck
    fun freesFfiNativeAllocation() {
        val raw = leakt_native_alloc(sizeOf<IntVar>().convert()) ?: error("leakt_native_alloc failed")
        leakt_native_free(raw)
    }
}
