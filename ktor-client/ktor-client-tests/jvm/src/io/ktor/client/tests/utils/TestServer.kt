/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*
import java.io.*

private val DEFAULT_PORT: Int = 8080
private val DEFAULT_SSL_PORT: Int = 8081

internal fun startServer(): ApplicationEngine {
    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.ERROR

    val keyStoreFile = File("build/temp.jks")
    val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withECDSA", keySizeInBits = 256)

    val environment = applicationEngineEnvironment {

        connector {
            port = DEFAULT_PORT
        }

        sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
            port = DEFAULT_SSL_PORT
        }

        module {
            tests()
            benchmarks()
        }
    }

    return embeddedServer(Jetty, environment).start()
}

/**
 * Start server for tests.
 */
fun main() {
    startServer()
}
