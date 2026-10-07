package com.ubad.academy.data.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Converts between JSON and Firestore's value model.
 *
 * This matters for interoperability: the web app writes `cloudState.appData` as
 * a real nested Firestore map (`db.collection('users').doc(uid).set({cloudState},
 * {merge:true})`), not as a JSON string. Android has to write the same structure,
 * otherwise the two clients could not read each other's data.
 */
internal object FirestoreValues {

    fun toFirestore(element: JsonElement?): Any? = when (element) {
        null, is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            else -> element.booleanOrNull
                ?: element.longOrNull
                ?: element.doubleOrNull
                ?: element.content
        }
        is JsonObject -> element.entries.associate { (k, v) -> k to toFirestore(v) }
        is JsonArray -> element.map { toFirestore(it) }
    }

    fun toFirestoreMap(obj: JsonObject): Map<String, Any?> =
        obj.entries.associate { (k, v) -> k to toFirestore(v) }

    fun fromFirestore(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to fromFirestore(v) } }.toMap(),
        )
        is List<*> -> JsonArray(value.map { fromFirestore(it) })
        // Firestore also surfaces Timestamp, GeoPoint, Blob and friends. The
        // payload the app writes only ever contains maps/scalars/lists, so this
        // branch is a safety net rather than an expected path.
        else -> JsonPrimitive(value.toString())
    }

    fun fromFirestoreMap(map: Map<*, *>?): JsonObject =
        map?.let { fromFirestore(it) as? JsonObject } ?: JsonObject(emptyMap())
}
