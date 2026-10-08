package dev.pocketdeck.adapter.limitless

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.meta.MetaDecklistSource
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.domain.meta.MetaDecklist
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Fetches the decks of the most recent well-attended Pokémon TCG Pocket tournaments from the public Limitless API.
 * The [client] must understand JSON (content negotiation); the adapter never owns it.
 */
class LimitlessMetaDecklistSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://play.limitlesstcg.com/api",
) : MetaDecklistSource {
    override suspend fun recentDecklists(): Outcome<List<MetaDecklist>, MetaSourceFailure> =
        try {
            val decklists =
                coroutineScope {
                    recentTournaments()
                        .map {
                            async {
                                standingsOf(
                                    it,
                                )
                            }
                        }.awaitAll()
                }.flatten()
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
        get<List<TournamentDto>>("tournaments") {
            parameter("game", GAME)
            parameter("limit", TOURNAMENTS_TO_LOOK_AT)
        }.filter { it.players >= MINIMUM_PLAYERS }.take(TOURNAMENTS_TO_USE)

    /** One tournament that cannot be read must not cost the player all the others. */
    private suspend fun standingsOf(tournament: TournamentDto): List<MetaDecklist> =
        try {
            get<List<StandingDto>>("tournaments/${tournament.id}/standings") {}
                .mapIndexedNotNull { index, standing -> standing.toDecklist(position = index + 1) }
        } catch (failure: UnsuccessfulResponse) {
            emptyList()
        }

    private suspend inline fun <reified T> get(
        path: String,
        crossinline configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): T {
        val response = client.get("$baseUrl/$path") { configure() }
        // Not swallowed per tournament: a limit hit means every further request fails too, and the player should hear why.
        if (response.status == HttpStatusCode.TooManyRequests) throw RateLimitedResponse()
        if (!response.status.isSuccess()) throw UnsuccessfulResponse("$path answered ${response.status}")
        return response.body()
    }

    private class RateLimitedResponse : Exception("rate limited")

    private class UnsuccessfulResponse(
        message: String,
    ) : Exception(message)

    private companion object {
        const val GAME = "POCKET"
        const val TOURNAMENTS_TO_LOOK_AT = 50
        const val TOURNAMENTS_TO_USE = 8
        const val MINIMUM_PLAYERS = 50
    }
}
