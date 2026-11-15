package com.example.corouterdemo.error

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/** Fails the build when a message key is missing from one of the supported locales. */
class MessageBundleConsistencyTest {
    private fun load(name: String) =
        Properties()
            .apply {
                MessageBundleConsistencyTest::class.java.classLoader
                    .getResourceAsStream(name)!!
                    .reader(Charsets.UTF_8)
                    .use { load(it) }
            }.stringPropertyNames()

    @Test
    fun `every locale defines the same keys`() {
        val base = load("messages.properties")
        val fr = load("messages_fr.properties")
        assertTrue((base - fr).isEmpty(), "Missing in messages_fr.properties: ${base - fr}")
        assertTrue((fr - base).isEmpty(), "Missing in messages.properties: ${fr - base}")
    }
}
