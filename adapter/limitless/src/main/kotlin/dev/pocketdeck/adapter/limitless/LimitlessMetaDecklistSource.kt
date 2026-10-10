package dev.pocketdeck.adapter.limitless

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.meta.MetaDecklistSource
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.domain.meta.MetaDecklist
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Fetches the decks of the most recent well-attended Pokémon TCG Pocket tournaments from the public Limitless API.
 * The adapter never owns the [client]. It is polite: it names itself ([LimitlessSettings.userAgent]), asks for one tournament at a time,
 * keeps finished tournaments on disk and stops sending after a 429 until the time in `Retry-After` has passed.
 */
class LimitlessMetaDecklistSource(
    private val client: HttpClient,
    private val settings: LimitlessSettings,
) : MetaDecklistSource {
    private val cache = StandingsCache(settings.cacheDirectory)
    private val json = Json { ignoreUnknownKeys = true }

    // After a 429 nothing is sent until the time Limitless asked for has passed (it applies to the whole app).
    @Volatile private var blockedUntil: Instant = Instant.MIN

    override suspend fun recentDecklists(): Outcome<List<MetaDecklist>, MetaSourceFailure> =
        try {
            // One after the other: the API allows few requests in a window, and a burst of eight helps nobody.
            val decklists = recentTournaments().flatMap { standingsOf(it) }
            if (decklists.isEmpty()) Outcome.Failure(MetaSourceFailure.NoDecklists) else Outcome.Success(decklists)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IOException) {
            notReachable(failure)
        } catch (failure: SerializationException) {
            notReachable(failure)
        } catch (failure: ContentConvertException) {
            notReachable(failure)
        } catch (failure: RateLimitedResponse) {
            Outcome.Failure(MetaSourceFailure.RateLimited)
        } catch (failure: UnsuccessfulResponse) {
            notReachable(failure)
        }

    private fun notReachable(failure: Exception) =
        Outcome.Failure(MetaSourceFailure.NotReachable(failure.message ?: failure::class.simpleName.orEmpty()))

    private suspend fun recentTournaments(): List<TournamentDto> =
        json
            .decodeFromString<List<TournamentDto>>(
                fetch("tournaments") {
                    parameter("game", GAME)
                    parameter("limit", TOURNAMENTS_TO_LOOK_AT)
                },
            ).filter { it.players >= MINIMUM_PLAYERS }
            .take(TOURNAMENTS_TO_USE)

    /** One tournament that cannot be read must not cost the player all the others. */
    private suspend fun standingsOf(tournament: TournamentDto): List<MetaDecklist> =
        try {
            json
                .decodeFromString<List<StandingDto>>(standingsBody(tournament))
                .mapIndexedNotNull { index, standing -> standing.toDecklist(position = index + 1) }
        } catch (failure: UnsuccessfulResponse) {
            emptyList()
        }

    private suspend fun standingsBody(tournament: TournamentDto): String {
        cache.read(tournament.id)?.let { return it }
        val body = fetch("tournaments/${tournament.id}/standings") {}
        if (isFinished(tournament)) cache.write(tournament.id, body)
        return body
    }

    private fun isFinished(tournament: TournamentDto): Boolean {
        val started = tournament.date?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
        return started.plus(FINISHED_AFTER).isBefore(settings.clock.instant())
    }

    private suspend fun fetch(
        path: String,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): String {
        if (settings.clock.instant().isBefore(blockedUntil)) throw RateLimitedResponse()
        val response =
            client.get("${settings.baseUrl}/$path") {
                header(HttpHeaders.UserAgent, settings.userAgent)
                configure()
            }
        // Not swallowed per tournament: a limit hit means every further request fails too, and the player should hear why.
        if (response.status == HttpStatusCode.TooManyRequests) {
            blockedUntil = settings.clock.instant().plus(waitAsked(response.headers[HttpHeaders.RetryAfter]))
            throw RateLimitedResponse()
        }
        if (!response.status.isSuccess()) throw UnsuccessfulResponse("$path answered ${response.status}")
        return response.bodyAsText()
    }

    /** `Retry-After` in seconds; a missing or unreadable value (a date) counts as the default, and nothing waits for hours. */
    private fun waitAsked(retryAfter: String?): Duration =
        retryAfter
            ?.trim()
            ?.toLongOrNull()
            ?.let { Duration.ofSeconds(it) }
            ?.coerceIn(Duration.ZERO, LONGEST_WAIT)
            ?: DEFAULT_WAIT

    private class RateLimitedResponse : Exception("rate limited")

    private class UnsuccessfulResponse(
        message: String,
    ) : Exception(message)

    private companion object {
        const val GAME = "POCKET"
        const val TOURNAMENTS_TO_LOOK_AT = 50
        const val TOURNAMENTS_TO_USE = 8
        const val MINIMUM_PLAYERS = 50
        val FINISHED_AFTER: Duration = Duration.ofDays(1)
        val DEFAULT_WAIT: Duration = Duration.ofMinutes(1)
        val LONGEST_WAIT: Duration = Duration.ofMinutes(10)
    }
}
