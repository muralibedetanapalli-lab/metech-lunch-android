package com.metech.lunch.backend

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads KEY=VALUE settings from a local `.env` file so secrets such as the database
 * password stay out of the repository (`.env` is git-ignored). Real environment
 * variables always win over the file, which is how hosting platforms pass settings.
 */
object EnvFile {

    /** Looks for `.env` in the working directory, then its parent (backend/ or the repo root). */
    fun load(env: Map<String, String> = System.getenv(), cwd: Path = Paths.get("").toAbsolutePath()): Map<String, String> {
        val file = listOfNotNull(cwd.resolve(".env"), cwd.parent?.resolve(".env")).firstOrNull { Files.isRegularFile(it) }
            ?: return env
        return parse(Files.readAllLines(file)) + env
    }

    fun parse(lines: List<String>): Map<String, String> =
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { line ->
                val key = line.substringBefore("=").trim().removePrefix("export ").trim()
                var value = line.substringAfter("=").trim()
                if (value.length >= 2 && value.first() == value.last() && value.first() in "\"'") {
                    value = value.substring(1, value.length - 1)
                }
                key to value
            }
}
