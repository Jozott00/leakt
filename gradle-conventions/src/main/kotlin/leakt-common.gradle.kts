import java.io.File

fun findVersionFile(startDir: File): File {
    var current: File? = startDir
    while (current != null) {
        val candidate = current.resolve("VERSION")
        if (candidate.isFile) {
            return candidate
        }
        current = current.parentFile
    }

    error("Unable to locate VERSION file from ${startDir.absolutePath}")
}

val repoVersion = findVersionFile(rootDir).readText().trim().also {
    require(it.isNotEmpty()) { "VERSION file must not be empty" }
}

group = "dev.jozott.leakt"
version = repoVersion
