package com.factoryops

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class FactoryOpsApplication {

    fun onStart(@Observes event: StartupEvent) {
        logger.info { "Factory Ops Backend started successfully" }
    }
}
