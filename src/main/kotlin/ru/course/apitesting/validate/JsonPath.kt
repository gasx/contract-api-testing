package ru.course.apitesting.validate

sealed class PathSegment {
    data class Field(val name: String) : PathSegment()
    data class ArrayAny(val name: String) : PathSegment()
}

object JsonPath {
    fun parse(path: String): List<PathSegment> {
        val clean = path.trim().removePrefix(".")
        if (clean.isEmpty()) return emptyList()

        return clean.split(".").map { token ->
            if (token.endsWith("[*]")) {
                val name = token.removeSuffix("[*]")
                PathSegment.ArrayAny(name)
            } else {
                PathSegment.Field(token)
            }
        }
    }
}
