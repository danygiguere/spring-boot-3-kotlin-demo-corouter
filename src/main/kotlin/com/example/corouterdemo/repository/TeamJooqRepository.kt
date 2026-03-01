package com.example.corouterdemo.repository

import com.example.corouterdemo.dto.TeamSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository

@Repository
class TeamJooqRepository(
    private val dsl: DSLContext,
) {
    // JOOQ join query: teams with enterprise info
    fun findTeamsWithEnterpriseInfo(): Flow<TeamSummary> =
        dsl
            .select(
                field("t.id"),
                field("t.name"),
                field("t.description"),
                field("e.name").`as`("enterprise_name"),
                field("e.email").`as`("enterprise_email"),
            ).from(table("teams").`as`("t"))
            .join(table("enterprises").`as`("e"))
            .on(field("t.enterprise_id").eq(field("e.id")))
            .asFlow()
            .map { record ->
                TeamSummary(
                    teamId = record[field("t.id", Long::class.java)],
                    teamName = record[field("t.name", String::class.java)] ?: "",
                    teamDescription = record[field("t.description", String::class.java)],
                    enterpriseName = record[field("enterprise_name", String::class.java)] ?: "",
                    enterpriseEmail = record[field("enterprise_email", String::class.java)] ?: "",
                )
            }
}
