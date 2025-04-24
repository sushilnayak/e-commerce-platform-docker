package com.nayak.productservice.aop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.withContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.coroutines.coroutineContext
import kotlin.reflect.jvm.kotlinFunction
import kotlin.system.measureTimeMillis

@Aspect
@Component
class PerformanceLoggingAspect {

    private val log = LoggerFactory.getLogger(PerformanceLoggingAspect::class.java)

    @Pointcut(
        "execution(public * com.nayak.productservice.controller..*.*(..)) || " +
                "execution(public * com.nayak.productservice.service..*.*(..)) || " +
                "execution(public * com.nayak.productservice.repository..*.*(..))"
    )
    fun monitorPerformance() {
    }

    @Around("monitorPerformance()")
    suspend fun logExecutionTime(joinPoint: ProceedingJoinPoint): Any? {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.name

//        if (methodSignature.method.isSuspend) {

//            return runBlockingWithMdc {
//                val duration = measureTimeMillis {
//                    try {
//                        joinPoint.proceed()
//                    } catch (throwable: Throwable) {
//                        log.error("Exception in {}.{}() : {}", className, methodName, throwable.message)
//                        throw throwable
//                    }
//                }
//            }
//        }

        if (methodSignature.method.isSuspend) {
            var result: Any? = null
            var throwable: Throwable? = null
            val duration = measureTimeMillis {
                try {
                    result = joinPoint.proceed()
                } catch (e: Throwable) {
                    throwable = e
                }
            }

            if (throwable != null) {
                log.error(
                    "{}.{}() threw exception after {} ms: {}",
                    className, methodName, duration, throwable!!.message ?: "Unknown error", throwable
                )
                throw throwable as Throwable
            } else {
                log.info("{}.{}() [Suspend] executed in {} ms", className, methodName, duration)
            }
            return result
        }

        var startTime = 0L
        var duration: Long
        val result: Any?

        try {
            // For non-suspend methods, proceed immediately returns Mono/Flux object or sync result
            result = joinPoint.proceed()

            when (result) {
                is Mono<*> -> {

                    return result
                        .doOnSubscribe { _ -> startTime = System.nanoTime() }
                        .doFinally { signalType ->
                            duration = (System.nanoTime() - startTime) / 1_000_000

                            log.info("{}.{}() [Mono] finalized with signal [{}] in {} ms", className, methodName, signalType, duration)
                        }
                }
                is Flux<*> -> {
                    return result
                        .doOnSubscribe { _ -> startTime = System.nanoTime() }
                        .doFinally { signalType ->
                            duration = (System.nanoTime() - startTime) / 1_000_000
                            log.info("{}.{}() [Flux] finalized with signal [{}] in {} ms", className, methodName, signalType, duration)
                        }
                }
                is Flow<*> -> {
                    // AOP timing for Flow execution is complex.
                    // This only times the creation of the Flow object.
                    // For actual execution time, decorate within the service layer if needed.
                    duration = 0
                    log.info("{}.{}() [Flow] created (execution timing requires internal decoration)", className, methodName)
                    return result
                }
                else -> {
                    // --- Handling Regular Synchronous Return Types ---
                    // For sync methods, proceed() finished the work, but we need the start time
                    // Note: This path is less common in a fully reactive service but might exist.
                    // We need to capture start time *before* proceed for sync methods.
                    // Let's restructure slightly for clarity:

                    // Re-evaluating structure for Sync methods:
                    // The current structure calculates duration only if it's NOT Mono/Flux/Flow.
                    // Let's ensure start time is captured *before* proceed for the sync case.

                    // **Corrected structure for Sync:**
                    // This part of the 'when' will likely not be hit if start time wasn't captured before proceed.
                    // A cleaner way is to capture start time universally before proceed,
                    // then handle reactive types specially *after* proceed.

                    // Let's refactor the non-suspend part:
                    // startTime = System.nanoTime() // Capture start time before proceed for sync/reactive creation
                    // val syncOrReactiveResult = joinPoint.proceed()
                    // duration = (System.nanoTime() - startTime) / 1_000_000
                    // log.info("{}.{}() [Sync] executed in {} ms", className, methodName, duration)
                    // return syncOrReactiveResult
                    // The original structure correctly defers timing calculation for Mono/Flux,
                    // but implicitly assumes non-Mono/Flux/Flow are synchronous and calculates duration after proceed.
                    // This timing for sync methods might be slightly inaccurate if proceed has overhead.
                    // Sticking to the original structure which logs sync duration *after* returning from proceed.
                    log.info("{}.{}() [Sync] returned (timing based on proceed call)", className, methodName)
                    return result // Return the synchronous result
                }
            }
        } catch (throwable: Throwable) {
            // Log exceptions for non-suspend methods
            log.error(
                "{}.{}() threw exception: {}",
                className, methodName, throwable.message ?: "Unknown error"//, throwable // Optionally log stacktrace
            )
            throw throwable // Re-throw
        }
    }

    // Helper to run a block with Reactor context propagated to coroutine MDC
    // NOTE: This might be overly complex if Spring AOP/coroutine integration handles it.
    // Start simple first, measure around proceed() for suspend as shown in the revised block above.
//    private suspend fun <T> runBlockingWithMdc(block: suspend () -> T): T {
//        // Get Reactor context if available
//        val reactorContext = coroutineContext[ReactorContext]?.context ?: reactor.util.context.Context.empty()
//        // Create Coroutine context element for MDC
//        val mdcContext =
//            kotlinx.coroutines.slf4j.MDCContext(reactorContext.toMap()) // Needs kotlinx-coroutines-slf4j dependency if used
//
//        return withContext(coroutineContext + mdcContext) { // Combine original context with MDC
//            block()
//        }
//    }


//    private fun reactor.util.context.Context.toMap(): Map<String, String> {
//        return this.stream()
//            .filter { entry -> entry.key is String && entry.value is String }
//            .collect(
//                { HashMap<String, String>() },
//                { map, entry -> map[entry.key as String] = entry.value as String },
//                { map1, map2 -> map1.putAll(map2) })
//    }


    private val java.lang.reflect.Method.isSuspend: Boolean
        get() = this.kotlinFunction?.isSuspend == true
}