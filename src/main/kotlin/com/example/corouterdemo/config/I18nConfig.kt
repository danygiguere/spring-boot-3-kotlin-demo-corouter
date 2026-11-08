package com.example.corouterdemo.config

import jakarta.validation.MessageInterpolator
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

/** English by default; `Accept-Language: fr` for French. */
@Configuration
class I18nConfig {
    @Bean
    fun messageSource(): MessageSource =
        ReloadableResourceBundleMessageSource().apply {
            setBasename("classpath:messages")
            setDefaultEncoding("UTF-8")
            // Unsupported locales fall back to messages.properties, not to the server's system locale.
            setFallbackToSystemLocale(false)
        }

    /** Bean Validation messages go through the same [MessageSource], so they are localized too. */
    @Bean
    fun validator(messageSource: MessageSource): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().apply {
            messageInterpolator =
                LocaleAwareMessageInterpolator(
                    ResourceBundleMessageInterpolator(MessageSourceResourceBundleLocator(messageSource)),
                )
        }

    @Bean
    fun localeContextResolver(): LocaleContextResolver =
        AcceptHeaderLocaleContextResolver().apply {
            defaultLocale = Locale.ENGLISH
        }
}

/** Interpolates in the request's locale (set by `validateAndThrow`) rather than the JVM's. */
private class LocaleAwareMessageInterpolator(
    private val delegate: MessageInterpolator,
) : MessageInterpolator {
    override fun interpolate(
        messageTemplate: String,
        context: MessageInterpolator.Context,
    ): String = delegate.interpolate(messageTemplate, context, LocaleContextHolder.getLocale())

    override fun interpolate(
        messageTemplate: String,
        context: MessageInterpolator.Context,
        locale: Locale,
    ): String = delegate.interpolate(messageTemplate, context, locale)
}
