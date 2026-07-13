package com.hujiayucc.hook.hooker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInstallerDslTest {
    private val method = InstallerFixture::class.java.getDeclaredMethod("value")
    private val installer = HookInstaller(
        ownerClassName = { "test.Owner" },
        ownerName = { "owner" },
        fallbackClassLoader = { null },
        handles = HookHandleRegistry()
    )

    @Test
    fun validDslBuildReturnsConfiguredDsl() {
        val dsl = installer.buildDsl(method) {
            before { }
            after { }
        }

        assertNotNull(dsl)
        assertTrue(dsl!!.isValid())
        assertEquals("before, after", dsl.describeCallbacks())
    }

    @Test
    fun emptyDslBuildIsRejected() {
        assertNull(installer.buildDsl(method) { })
    }

    @Test
    fun conflictingDslBuildIsRejected() {
        val dsl = installer.buildDsl(method) {
            replaceTo("replacement")
            after { }
        }

        assertNull(dsl)
    }

    @Test
    fun exceptionWhileBuildingDslIsContained() {
        val dsl = installer.buildDsl(method) {
            error("failed configuration")
        }

        assertNull(dsl)
    }

    private class InstallerFixture {
        fun value(): String = "value"
    }
}