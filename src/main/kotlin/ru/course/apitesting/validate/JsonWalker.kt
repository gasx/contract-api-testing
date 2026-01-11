package ru.course.apitesting.validate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json

object JsonWalker {

    fun exists(root: JsonElement, segments: List<PathSegment>): Boolean {
        return existsRec(root, segments, 0)
    }

    private fun existsRec(node: JsonElement, segs: List<PathSegment>, idx: Int): Boolean {
        if (idx >= segs.size) return true

        return when (val seg = segs[idx]) {
            is PathSegment.Field -> {
                val obj = node as? JsonObject ?: return false
                val next = obj[seg.name] ?: return false
                existsRec(next, segs, idx + 1)
            }
            is PathSegment.ArrayAny -> {
                val obj = node as? JsonObject ?: return false
                val arrEl = obj[seg.name] ?: return false
                val arr = arrEl as? JsonArray ?: return false
                arr.any { el -> existsRec(el, segs, idx + 1) }
            }
        }
    }

    fun parseJson(text: String): JsonElement? = try {
        Json.parseToJsonElement(text)
    } catch (_: Exception) {
        null
    }
}
