package ru.course.apitesting.cli

data class Args(
    val runFile: String,
    val outDir: String,
    val web: Boolean = false
) {
    companion object {
        fun parse(raw: Array<String>): Args? {
            var run: String? = null
            var out: String? = null
            var web = false

            var i = 0
            while (i < raw.size) {
                when (raw[i]) {
                    "--run" -> {
                        if (i + 1 >= raw.size) return null
                        run = raw[i + 1]
                        i += 2
                    }

                    "--out" -> {
                        if (i + 1 >= raw.size) return null
                        out = raw[i + 1]
                        i += 2
                    }

                    "--web" -> {
                        web = true
                        i += 1
                    }

                    else -> return null
                }
            }

            if (run.isNullOrBlank() || out.isNullOrBlank()) return null

            return Args(
                runFile = run,
                outDir = out,
                web = web
            )
        }
    }
}