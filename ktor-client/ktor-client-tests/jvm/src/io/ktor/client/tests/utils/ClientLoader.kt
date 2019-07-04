/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.jetty.*
import kotlinx.coroutines.debug.*
import kotlinx.coroutines.debug.junit4.*
import org.eclipse.jetty.util.ssl.*
import org.junit.*
import org.junit.runner.*
import org.junit.runners.*
import org.slf4j.*
import java.util.*

/**
 * Helper interface to test client.
 */
@RunWith(Parameterized::class)
actual abstract class ClientLoader {

    @Parameterized.Parameter
    lateinit var engine: HttpClientEngineContainer

    @get:Rule
    open val timeout = CoroutinesTimeout.seconds(30)

    actual var TEST_SERVER: String = HTTP_TEST_SERVER
        private set

    @Before
    fun setup() {
        val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        logger.level = Level.WARN
    }

    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        vararg skipEngines: String,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        val engineName = engine.toString()
        Assume.assumeFalse(engineName in skipEngines)

        TEST_SERVER = when (engineName) {
            "Jetty" -> HTTPS_TEST_SERVER
            else -> HTTP_TEST_SERVER
        }

        val factory = when (engine.factory) {
            Jetty -> {
                Jetty.config {
                    sslContextFactory = SslContextFactory(true)
                }
            }
            else -> engine.factory
        }

        clientTest(factory, block)
    }

    actual fun dumpCoroutines() {
        DebugProbes.dumpCoroutines()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun engines(): List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
            ServiceLoader.load(it, it.classLoader).toList()
        }
    }

}
