package dev.jozott.leakt.test

import dev.jozott.leakt.LeakCheck
import dev.jozott.leakt.leakCheckedTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.rawPtr
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
}
