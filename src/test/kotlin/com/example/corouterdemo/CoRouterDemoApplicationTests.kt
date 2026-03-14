package com.example.corouterdemo

import com.example.corouterdemo.shared.config.SharedTestContainers
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CoRouterDemoApplicationTests : SharedTestContainers() {
    @Test
    fun contextLoads() {
    }
}
