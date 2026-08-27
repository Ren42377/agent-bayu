package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.RouteFailure
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

internal object StreamingHttp {

    val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    const val CONNECT_TIMEOUT_MILLIS = 15_000L
    const val ERROR_SNIPPET_LENGTH = 512

    fun stream(
        client: OkHttpClient,
        request: Request,
        idleTimeoutMillis: Long,
        parse: (String) -> List<WireEvent>
    ): Flow<WireEvent> = flow {
        val scoped = client.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(idleTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val call = scoped.newCall(request)
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()?.take(ERROR_SNIPPET_LENGTH).orEmpty()
                    emit(
                        WireEvent.Failure(
                            FailureClassifier.classifyHttp(
                                response.code,
                                body,
                                response.header("Retry-After")
                            )
                        )
                    )
                    return@flow
                }

                val source = response.body?.source()
                if (source == null) {
                    emit(
                        WireEvent.Failure(
                            RouteFailure(kind = FailureKind.RETRYABLE, message = "empty response body")
                        )
                    )
                    return@flow
                }

                val reader = SseReader()
                var stop = false
                var failed = false
                while (!stop) {
                    val line = source.readUtf8Line() ?: break
                    for (signal in reader.accept(line)) {
                        when (signal) {
                            SseSignal.Done -> stop = true
                            is SseSignal.Data -> for (event in parse(signal.json)) {
                                emit(event)
                                if (event is WireEvent.Failure) {
                                    failed = true
                                    stop = true
                                }
                            }
                        }
                    }
                }
                if (!stop) {
                    for (signal in reader.flush()) {
                        if (signal is SseSignal.Data) {
                            for (event in parse(signal.json)) {
                                emit(event)
                                if (event is WireEvent.Failure) failed = true
                            }
                        }
                    }
                }
                if (!failed) emit(WireEvent.Done)
            }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            emit(WireEvent.Failure(FailureClassifier.classifyError(error)))
        } finally {
            cancelHandle.dispose()
        }
    }.flowOn(Dispatchers.IO)
}
