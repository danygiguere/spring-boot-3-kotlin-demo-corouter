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

@Configuration
class I18nConfig {
    @Bean
    fun messageSource(): MessageSource =
        ReloadableResourceBundleMessageSource().apply {
            setBasename("classpath:messages")
            setDefaultEncoding("UTF-8")
        }

    @Bean
    fun validator(messageSource: MessageSource): LocalValidatorFactoryBean {
        val interpolator =
            LocaleAwareMessageInterpolator(
                ResourceBundleMessageInterpolator(
                    MessageSourceResourceBundleLocator(messageSource),
                ),
            )
        return LocalValidatorFactoryBean().apply {
            setMessageInterpolator(interpolator)
        }
    }

    @Bean
    fun localeContextResolver(): LocaleContextResolver =
        AcceptHeaderLocaleContextResolver().apply {
            setDefaultLocale(Locale.ENGLISH)
        }
}

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
