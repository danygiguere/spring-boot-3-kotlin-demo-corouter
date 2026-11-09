package com.example.corouterdemo.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/** Registers the bean only when `app.dev-token.enabled` (env `DEV_TOKEN_ENABLED`) is `true`. Off by default. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(name = ["app.dev-token.enabled"], havingValue = "true")
annotation class DevTokenEnabled
