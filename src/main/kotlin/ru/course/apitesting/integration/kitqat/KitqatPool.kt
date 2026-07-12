package ru.course.apitesting.integration.kitqat

enum class KitqatPool(
    val templateName: String
) {
    BLACK_TFPLCU_3_0("Black TFPLCU3.0");

    companion object {
        fun fromCode(code: String): KitqatPool {
            return values().firstOrNull { it.name == code }
                ?: error("Неизвестный Kitqat pool: $code. Доступные значения: ${values().joinToString { it.name }}")
        }
    }
}