package com.irfan.prosantinibash.core.network

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout

/** Lazy application-scoped client: startup and PIN setup never require cloud access. */
@OptIn(SupabaseInternal::class)
class SupabaseProvider(val config: SupabaseConfig) {
    val client: SupabaseClient by lazy {
        check(config.validate() == null) { "Cloud connection is not configured." }
        createSupabaseClient(config.url, config.publishableKey) {
            install(Postgrest)
            install(Storage)
            httpEngine = Android.create()
            httpConfig {
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
    }
}
