/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*


internal val HTTP_TEST_SERVER = "http://127.0.0.1:8080"
internal val HTTPS_TEST_SERVER = "https://127.0.0.1:8081"

/**
 * Helper interface to test client.
 */
expect abstract class ClientLoader() {

    var TEST_SERVER: String
        private set

    /**
     * Perform test against all clients from dependencies.
     */
    fun clientTests(
        vararg skipEngines: String,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    )

    fun dumpCoroutines()
}
