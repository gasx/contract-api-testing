package ru.course.apitesting.validate

import kotlinx.serialization.json.JsonElement

object JsonPathExtensions {
    fun exists(root: JsonElement, path: String): Boolean {
        val segments = JsonPath.parse(path)
        return JsonWalker.exists(root, segments)
    }
}
