/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.engine.*
import kotlinx.coroutines.*

/**
 * Helper interface to test client.
 */
actual abstract class ClientLoader {
    actual var TEST_SERVER: String = HTTP_TEST_SERVER
        private set

    /**
     * Perform test against all clients from dependencies.
     */
    actual fun clientTests(
        vararg skipEngines: String,
        block: suspend TestClientBuilder<HttpClientEngineConfig>.() -> Unit
    ) {
        engines.forEach {
            if (it.toString() in skipEngines) return@forEach

            clientTest(it) {
                withTimeout(3000) {
                    block()
                }
            }
        }
    }

    actual fun dumpCoroutines() {
        error("Debug probes unsupported native.")
    }
}
