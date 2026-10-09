package dev.pocketdeck.adapter.limitless

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.meta.MetaSourceFailure
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.EnergyType
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class LimitlessMetaDecklistSourceTest {
    private val tournaments =
        """[{"id":"big","name":"Big","players":300,"game":"POCKET"},
            |{"id":"small","name":"Small","players":12,"game":"POCKET"},
            |{"id":"broken","name":"Broken","players":80,"game":"POCKET"}]
        """.trimMargin()

    private val standings =
        """[{"name":"a","placing":1,"record":{"wins":11,"losses":2,"ties":0},
            | "deck":{"id":"arcanine-ex-moltres-ex-a1","name":"Arcanine ex Moltres ex"},
            | "decklist":{"pokemon":[{"count":2,"set":"A1","number":"41","name":"Arcanine ex"}],
            |  "trainer":[{"count":2,"set":"P-A","number":"7","name":"Professor's Research"}],
            |  "energy":["Fire"]}},
            |{"name":"b","placing":2,"record":null,"deck":null,"decklist":null}]
        """.trimMargin()

    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val clock = SettableClock(now)

    @TempDir
    lateinit var cacheDirectory: File

    private fun source(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): LimitlessMetaDecklistSource {
        val client =
            HttpClient(MockEngine { request -> handler(request) }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return LimitlessMetaDecklistSource(
            client,
            LimitlessSettings(
                "PocketDeck/test (+https://example.test)",
                cacheDirectory,
                "https://limitless.test/api",
                clock,
            ),
        )
    }

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData =
        when {
            request.url.encodedPath.endsWith("/tournaments") -> json(tournaments)
            request.url.encodedPath.endsWith("/big/standings") -> json(standings)
            else -> respondError(HttpStatusCode.InternalServerError)
        }

    @Test
    fun `turns the standings of well attended tournaments into decklists`() =
        runTest {
            val outcome = source { answer(it) }.recentDecklists() as Outcome.Success

            val list = outcome.value.single()
            list.archetype.value shouldBe "Arcanine ex Moltres ex"
            list.entries.map { it.cardId to it.copies } shouldBe listOf(CardId("a1-041") to 2, CardId("pa-007") to 2)
            list.energyTypes shouldBe setOf(EnergyType.FIRE)
            list.result.placing shouldBe 1
            list.result.wins shouldBe 11
        }

    @Test
    fun `asks only for pocket tournaments`() =
        runTest {
            val asked = mutableListOf<String>()

            source {
                asked += it.url.toString()
                answer(it)
            }.recentDecklists()

            asked.first().contains("game=POCKET") shouldBe true
        }

    @Test
    fun `skips a tournament whose standings cannot be read`() =
        runTest {
            // "broken" is large enough but answers with an error; "big" still delivers.
            (source { answer(it) }.recentDecklists() is Outcome.Success) shouldBe true
        }

    @Test
    fun `reports a missing connection`() =
        runTest {
            val outcome = source { throw IOException("no network") }.recentDecklists()

            outcome shouldBe Outcome.Failure(MetaSourceFailure.NotReachable("no network"))
        }

    @Test
    fun `reports when the api limits the requests`() =
        runTest {
            val outcome =
                source { request ->
                    if (request.url.encodedPath.endsWith("/tournaments")) {
                        json(tournaments)
                    } else {
                        respond("", HttpStatusCode.TooManyRequests)
                    }
                }.recentDecklists()

            outcome shouldBe Outcome.Failure(MetaSourceFailure.RateLimited)
        }

    @Test
    fun `reports when no tournament has a decklist`() =
        runTest {
            val outcome =
                source { request ->
                    if (request.url.encodedPath.endsWith("/tournaments")) json(tournaments) else json("[]")
                }.recentDecklists()

            outcome shouldBe Outcome.Failure(MetaSourceFailure.NoDecklists)
        }

    @Test
    fun `reports an answer that is not what the api promises`() =
        runTest {
            val outcome = source { json("""{"unexpected":true}""") }.recentDecklists()

            (outcome as Outcome.Failure).reason.javaClass shouldBe MetaSourceFailure.NotReachable::class.java
        }

    @Test
    fun `names itself in every request`() =
        runTest {
            val agents = mutableSetOf<String?>()

            source {
                agents += it.headers[HttpHeaders.UserAgent]
                answer(it)
            }.recentDecklists()

            agents shouldBe setOf("PocketDeck/test (+https://example.test)")
        }

    private val finishedAndRunning =
        """[{"id":"big","players":300,"date":"2026-10-01T10:00:00Z"},
            |{"id":"live","players":300,"date":"2026-10-08T10:00:00Z"}]
        """.trimMargin()

    private fun MockRequestHandleScope.answerWithTimes(request: HttpRequestData): HttpResponseData =
        when {
            request.url.encodedPath.endsWith("/tournaments") -> json(finishedAndRunning)
            request.url.encodedPath.endsWith("/standings") -> json(standings)
            else -> respondError(HttpStatusCode.InternalServerError)
        }

    @Test
    fun `asks for the standings of a finished tournament only once`() =
        runTest {
            val asked = mutableListOf<String>()

            fun fresh() =
                source {
                    asked += it.url.encodedPath
                    answerWithTimes(it)
                }

            fresh().recentDecklists()
            fresh().recentDecklists()

            asked.count { it.endsWith("/big/standings") } shouldBe 1
            asked.count { it.endsWith("/live/standings") } shouldBe 2
        }

    @Test
    fun `sends nothing more until the time retry-after asked for has passed`() =
        runTest {
            var requests = 0
            var limited = true
            val source =
                source {
                    requests++
                    if (limited) {
                        respond(
                            "",
                            HttpStatusCode.TooManyRequests,
                            headersOf(HttpHeaders.RetryAfter, "120"),
                        )
                    } else {
                        answer(it)
                    }
                }

            source.recentDecklists() shouldBe Outcome.Failure(MetaSourceFailure.RateLimited)
            source.recentDecklists() shouldBe Outcome.Failure(MetaSourceFailure.RateLimited)
            requests shouldBe 1

            limited = false
            clock.instant = now.plusSeconds(121)
            (source.recentDecklists() is Outcome.Success) shouldBe true
        }

    @Test
    fun `energy names are understood in any case`() {
        "fire".toEnergyType() shouldBe EnergyType.FIRE
        "Psychic".toEnergyType() shouldBe EnergyType.PSYCHIC
        "Banana".toEnergyType() shouldBe null
    }

    @Test
    fun `promo set codes lose their dash`() {
        CardLineDto(1, "P-A", "7").cardId() shouldBe CardId("pa-007")
        CardLineDto(1, "A2b", "12").cardId() shouldBe CardId("a2b-012")
    }
}

private class SettableClock(
    var instant: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant
}
