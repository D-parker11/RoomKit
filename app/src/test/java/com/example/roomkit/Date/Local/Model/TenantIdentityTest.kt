package com.example.roomkit.Date.Local.Model

import org.junit.Assert.*
import org.junit.Test

class TenantIdentityTest {
    @Test fun cambodianFormatsUseTheSameKey() {
        val expected = "012345678"
        listOf("012 345 678", "+855 12 345 678", "00855-12-345-678", "+855 (0)12 345678").forEach {
            assertEquals(expected, TenantIdentity.phone(it))
        }
    }
    @Test fun identityIsCaseInsensitiveAndOptional() {
        assertEquals("TEST-A", TenantIdentity.document(" test-a "))
        assertEquals("", TenantIdentity.document(null))
        assertNotEquals(TenantIdentity.phone("012345678"), TenantIdentity.phone("012345679"))
    }
}
