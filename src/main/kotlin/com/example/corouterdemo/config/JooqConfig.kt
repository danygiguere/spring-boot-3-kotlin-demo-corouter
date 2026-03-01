package com.example.corouterdemo.config

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JooqConfig {
    @Bean
    fun dslContext(connectionFactory: ConnectionFactory): DSLContext {
        val configuration =
            DefaultConfiguration()
                .set(connectionFactory)
                .set(SQLDialect.POSTGRES)
        return DSL.using(configuration)
    }
}
