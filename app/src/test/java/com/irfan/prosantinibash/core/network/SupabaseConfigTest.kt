package com.irfan.prosantinibash.core.network

import org.junit.Assert.*
import org.junit.Test

class SupabaseConfigTest {
    @Test fun rejectsUnsafeAndMissingConfiguration() {
        val key = "sb_publishable_fake_test_key"
        assertNull(SupabaseConfig("https://example.supabase.co", key).validate())
        listOf("", "http://example.com", "https://user@example.com", "https://example.com?q=1", "https://example.com/#x")
            .forEach { assertNotNull(SupabaseConfig(it, key).validate()) }
        listOf("", "sb_secret_fake", "eyJ.fake.jwt").forEach {
            assertNotNull(SupabaseConfig("https://example.com", it).validate())
        }
    }
}
