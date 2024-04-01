import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.time.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

const val MAX_REQUESTS = 15
const val MAX_REQUEST_DURATION_MILLIS = 10_000
const val MIN_REQUEST_DURATION_MILLIS = 50

suspend fun makeRequest(duration: Duration, id: Int): Boolean {
    try {
        delay(duration.toJavaDuration())
    } catch (e: CancellationException) {
        println("Request $id is cancelled")
        throw e
    }
    val isOk = isSuccessful()
    println("Request $id completed, result = $isOk, execution time: $duration")
    return isOk
}

fun isSuccessful(): Boolean {
    return (0..5).random() <= 3
}

fun CoroutineScope.makeRequests(): List<Deferred<Boolean>> {
    val numberOfRequests = (1..MAX_REQUESTS).random()
    println("There will be $numberOfRequests request(s)")

    return (1..numberOfRequests).map { id ->
        async(Dispatchers.IO) {
            makeRequest((MIN_REQUEST_DURATION_MILLIS..MAX_REQUEST_DURATION_MILLIS).random().milliseconds, id)
        }
    }
}

suspend fun getFirstSuccessOrReturnLastFailedResponse(requests: List<Deferred<Boolean>>): Boolean {
    return coroutineScope {
        while (requests.any { !it.isCompleted }) {
            val response = select {
                requests
                    .onNotCompleted()
                    .forEach { deferred -> deferred.onAwait { it } }
            }
            if (response) {
                requests
                    .onNotCompleted()
                    .forEach { it.cancelAndJoin() }
                return@coroutineScope true
            }
        }
        return@coroutineScope false
    }
}

fun<T> List<Deferred<T>>.onNotCompleted() = asSequence().filter { !it.isCompleted }

fun main() = runBlocking {
    val requests = makeRequests()
    val response = getFirstSuccessOrReturnLastFailedResponse(requests)
    println("Response: $response")
}
