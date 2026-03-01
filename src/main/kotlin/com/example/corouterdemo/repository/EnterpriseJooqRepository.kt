package com.example.corouterdemo.repository

import com.example.corouterdemo.dto.EnterpriseWithTeams
import com.example.corouterdemo.dto.TeamWithMembers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.multiset
import org.jooq.impl.DSL.select
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository

@Repository
class EnterpriseJooqRepository(
    private val dsl: DSLContext,
) {
    // JOOQ MULTISET query: search enterprises with their teams and users (no N+1)
    fun searchByNameJooq(name: String): Flow<EnterpriseWithTeams> {
        val usersField =
            multiset(
                select(field("u.id", Long::class.java))
                    .from(table("users").`as`("u"))
                    .join(table("team_members").`as`("tm"))
                    .on(field("tm.user_id", Long::class.java).eq(field("u.id", Long::class.java)))
                    .where(field("tm.team_id", Long::class.java).eq(field("t.id", Long::class.java))),
            ).`as`("users").convertFrom { r -> r.map { it.value1() ?: 0L } }

        val teamsField =
            multiset(
                select(
                    field("t.id", Long::class.java),
                    field("t.name", String::class.java),
                    field("t.description", String::class.java),
                    usersField,
                ).from(table("teams").`as`("t"))
                    .where(field("t.enterprise_id", Long::class.java).eq(field("e.id", Long::class.java))),
            ).`as`("teams").convertFrom { r ->
                r.map { tr ->
                    TeamWithMembers(
                        id = tr.value1(),
                        name = tr.value2() ?: "",
                        description = tr.value3(),
                        userIds = tr.value4(),
                    )
                }
            }

        return dsl
            .select(
                field("e.id", Long::class.java),
                field("e.name", String::class.java),
                field("e.phone_number", String::class.java),
                field("e.website", String::class.java),
                field("e.email", String::class.java),
                field("e.description", String::class.java),
                teamsField,
            ).from(table("enterprises").`as`("e"))
            .where(field("LOWER(e.name)").like("%${name.lowercase()}%"))
            .asFlow()
            .map { record ->
                EnterpriseWithTeams(
                    id = record.value1(),
                    name = record.value2() ?: "",
                    phoneNumber = record.value3(),
                    website = record.value4(),
                    email = record.value5() ?: "",
                    description = record.value6(),
                    teams = record.value7(),
                )
            }
    }
}
