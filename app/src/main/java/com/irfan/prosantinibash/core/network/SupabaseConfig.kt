package com.irfan.prosantinibash.core.network

import java.net.URI

/** Accepts client-safe configuration; returned messages never expose input values. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    fun validate(): String? {
        val uri = try { URI(url) } catch (_: Exception) { return "Cloud connection is not configured." }
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.query != null || uri.fragment != null || uri.path !in listOf("", "/")) {
            return "Cloud connection is not configured."
        }
        if (!Regex("sb_publishable_[A-Za-z0-9_-]+").matches(publishableKey)) {
            return "Cloud connection is not configured."
        }
        return null
    }
}
