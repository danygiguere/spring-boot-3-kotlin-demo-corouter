package com.example.corouterdemo.error

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import java.io.File
import java.util.Properties

/**
 * Fails the build when the code uses a message key that no bundle defines, or passes a sentence
 * where a key is expected. Either way the client would otherwise get a generic message, and the
 * gap would only show up in the logs.
 *
 * The scan reads string literals, so it finds keys written directly in the code:
 * - any literal starting with `error.`;
 * - any literal passed as `messageKey = "..."`;
 * - the first literal argument of every [ApiException] subclass, such as `NotFoundException("...")`.
 *
 * Comments are ignored. Keys built at runtime (such as `error.title.${status}`) or held in
 * variables are not checked.
 */
class MessageKeyUsageTest {
    private val sourceRoot = File("src/main/kotlin")

    private fun bundleKeys(name: String): Set<String> =
        Properties()
            .apply {
                MessageKeyUsageTest::class.java.classLoader
                    .getResourceAsStream(name)!!
                    .reader(Charsets.UTF_8)
                    .use { load(it) }
            }.stringPropertyNames()

    private fun apiExceptionNames(): Set<String> =
        ClassPathScanningCandidateComponentProvider(false)
            .apply { addIncludeFilter(AssignableTypeFilter(ApiException::class.java)) }
            .findCandidateComponents("com.example.corouterdemo")
            .mapNotNull { it.beanClassName?.substringAfterLast('.') }
            .toSet()

    private fun usedKeys(): Map<String, Set<String>> {
        val exceptionCall = Regex("""\b(?:${apiExceptionNames().joinToString("|")})\(\s*"([^"]*)"""")
        val patterns =
            listOf(
                Regex(""""(error\.[^"$]*)""""),
                Regex("""messageKey\s*=\s*"([^"]*)""""),
                exceptionCall,
            )
        val usages = mutableMapOf<String, MutableSet<String>>()
        sourceRoot.walk().filter { it.extension == "kt" }.forEach { file ->
            val text = withoutComments(file.readText())
            patterns.forEach { pattern ->
                pattern.findAll(text).forEach { usages.getOrPut(it.groupValues[1]) { sortedSetOf() } += file.name }
            }
        }
        return usages
    }

    /** Removes block comments (including KDoc) and whole-line `//` comments. */
    private fun withoutComments(source: String): String =
        source
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    @Test
    fun `every message key used in the code is a key and exists in every bundle`() {
        val used = usedKeys()
        assertTrue(used.size >= 10, "the scan found too few keys, check the patterns: ${used.keys}")

        val notKeys = used.filterKeys { !KEY.matches(it) }
        if (notKeys.isNotEmpty()) {
            fail<Unit>("Not message keys. Use a key and put details in internalMessage: $notKeys")
        }

        val problems =
            BUNDLES.mapNotNull { bundle ->
                val missing = used.filterKeys { it !in bundleKeys(bundle) }
                missing.takeIf { it.isNotEmpty() }?.let { "$bundle is missing $it" }
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private companion object {
        val BUNDLES = listOf("messages.properties", "messages_fr.properties")
        val KEY = Regex("""^[a-z][A-Za-z0-9]*(\.[A-Za-z0-9_]+)+$""")
    }
}
