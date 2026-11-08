package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Enterprise
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface EnterpriseRepository : CoroutineCrudRepository<Enterprise, Long>
