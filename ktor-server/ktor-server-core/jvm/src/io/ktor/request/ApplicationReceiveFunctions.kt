/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.request

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.io.*
import java.io.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Represents a subject for [ApplicationReceivePipeline]
 * @param typeInfo specifies the desired type for a receiving operation
 * @param value specifies current value being processed by the pipeline
 * @param reusableValue indicates whether the [value] instance can be reused. For example, a stream can't.
 */
class ApplicationReceiveRequest @KtorExperimentalAPI constructor(
    @KtorExperimentalAPI val typeInfo: KType,
    val value: Any,
    @KtorExperimentalAPI val reusableValue: Boolean = false
) {
    @Deprecated("Use typeOf to pass KType instead")
    constructor(type: KClass<*>, value: Any) : this(type.starProjectedType, value, false)

    /**
     * Star projected class computed from [typeInfo]
     */
    val type: KClass<*>
        get() = typeInfo.jvmErasure
}

/**
 * Pipeline for processing incoming content
 *
 * When executed, this pipeline starts with an instance of [ByteReadChannel] and should finish with the requested type.
 */
open class ApplicationReceivePipeline : Pipeline<ApplicationReceiveRequest, ApplicationCall>(Before, Transform, After) {
    /**
     * Pipeline phases
     */
    @Suppress("PublicApiImplicitType")
    companion object Phases {
        /**
         * Executes before any transformations are made
         */
        val Before = PipelinePhase("Before")

        /**
         * Executes transformations
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Executes after all transformations
         */
        val After = PipelinePhase("After")
    }
}

/**
 * Receives content for this request.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type.
 */
@UseExperimental(ExperimentalStdlibApi::class)
suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = receiveOrNull(typeOf<T>())

/**
 * Receives content for this request.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
@UseExperimental(ExperimentalStdlibApi::class)
suspend inline fun <reified T : Any> ApplicationCall.receive(): T = receive(typeOf<T>())

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
suspend fun <T : Any> ApplicationCall.receive(type: KClass<T>): T {
    return receive(type.starProjectedType)
}

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the requested type.
 */
suspend fun <T : Any> ApplicationCall.receive(type: KType): T {
    require(type != ApplicationReceiveRequest::class) { "ApplicationReceiveRequest can't be received" }

    val token = attributes.getOrNull(DoubleReceivePreventionTokenKey)

    if (token == null) {
        attributes.put(DoubleReceivePreventionTokenKey, DoubleReceivePreventionToken)
    }

    val incomingContent = token ?: request.receiveChannel()
    val receiveRequest = ApplicationReceiveRequest(type, incomingContent)
    val finishedRequest = request.pipeline.execute(this, receiveRequest)
    val transformed = finishedRequest.value

    when {
        transformed === DoubleReceivePreventionToken -> throw RequestAlreadyConsumedException()
        !type.jvmErasure.isInstance(transformed) -> throw CannotTransformContentToTypeException(type)
    }

    @Suppress("UNCHECKED_CAST")
    return transformed as T
}

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KType): T? {
    return try {
        receive(type)
    } catch (cause: ContentTransformationException) {
        application.log.debug("Conversion failed, null returned", cause)
        null
    }
}

/**
 * Receives content for this request.
 * @param type instance of `KClass` specifying type to be received.
 * @return instance of [T] received from this call, or `null` if content cannot be transformed to the requested type..
 */
suspend fun <T : Any> ApplicationCall.receiveOrNull(type: KClass<T>): T? {
    return try {
        receive(type)
    } catch (cause: ContentTransformationException) {
        application.log.debug("Conversion failed, null returned", cause)
        null
    }
}

/**
 * Receives incoming content for this call as [String].
 * @return text received from this call.
 * @throws ContentTransformationException when content cannot be transformed to the [String].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveText(): String = receive()

/**
 * Receives channel content for this call.
 * @return instance of [ByteReadChannel] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [ByteReadChannel].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveChannel(): ByteReadChannel = receive()

/**
 * Receives stream content for this call.
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveStream(): InputStream = receive()

/**
 * Receives multipart data for this call.
 * @return instance of [MultiPartData].
 * @throws ContentTransformationException when content cannot be transformed to the [MultiPartData].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveMultipart(): MultiPartData = receive()

/**
 * Receives form parameters for this call.
 * @return instance of [Parameters].
 * @throws ContentTransformationException when content cannot be transformed to the [Parameters].
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.receiveParameters(): Parameters = receive()

/**
 * Thrown when content cannot be transformed to the desired type.
 */
typealias ContentTransformationException = io.ktor.features.ContentTransformationException

/**
 * This object is attached to an [ApplicationCall] with [DoubleReceivePreventionTokenKey] when
 * the receive function is invoked. It is used to detect double receive invocation
 * that causes [RequestAlreadyConsumedException] to be thrown unless [DoubleReceive] feature installed.
 */
private object DoubleReceivePreventionToken

private val DoubleReceivePreventionTokenKey = AttributeKey<DoubleReceivePreventionToken>("DoubleReceivePreventionToken")

/**
 * Thrown when a request body has been already received.
 * Usually it is caused by double [ApplicationCall.receive] invocation.
 */
@KtorExperimentalAPI
class RequestAlreadyConsumedException : IllegalStateException("Request body has been already consumed (received).")
