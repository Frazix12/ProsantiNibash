package com.irfan.prosantinibash.feature.tenants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.irfan.prosantinibash.core.util.Money
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.UUID

/** Saved fields and request ID survive rotation, background locking and failed retries. */
@Composable
fun rentForm(initial: Map<String, String>): SnapshotStateMap<String, String> = rememberSaveable(
    saver = mapSaver(save = { it.toMap() }, restore = { restored ->
        mutableStateMapOf<String, String>().apply { restored.forEach { (key, value) -> put(key, value as String) } }
    })
) { mutableStateMapOf<String, String>().apply { putAll(initial); put("request", UUID.randomUUID().toString()) } }

fun paisaInput(paisa: Long) = BigDecimal.valueOf(paisa, 2).toPlainString()
fun rentPayload(fields: Map<String, String>, moneyKeys: Set<String> = emptySet(), numberKeys: Set<String> = emptySet()): JsonObject =
    JsonObject(fields.filterKeys { it != "request" && it != "error" }.mapValues { (key, value) ->
        when (key) {
            in moneyKeys -> JsonPrimitive(Money.parsePaisa(value) ?: 0)
            in numberKeys -> JsonPrimitive(value.toLongOrNull() ?: 0)
            else -> if (value.isBlank()) JsonNull else JsonPrimitive(value.trim())
        }
    })
