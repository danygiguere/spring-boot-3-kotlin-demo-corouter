package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.User
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface UserRepository : CoroutineCrudRepository<User, Long>
