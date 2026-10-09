package dev.pocketdeck.adapter.limitless

import java.io.File
import java.time.Clock

/**
 * How the adapter talks to Limitless: who it says it is, where it may keep finished tournaments, and what time it is.
 * [userAgent] lets Limitless see which app is calling and where to reach its maker; [cacheDirectory] is optional.
 */
data class LimitlessSettings(
    val userAgent: String,
    val cacheDirectory: File? = null,
    val baseUrl: String = "https://play.limitlesstcg.com/api",
    val clock: Clock = Clock.systemUTC(),
)
