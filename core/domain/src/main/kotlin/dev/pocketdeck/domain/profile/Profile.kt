package dev.pocketdeck.domain.profile

/** Identifies one game account the player manages in the app. */
@JvmInline
value class ProfileId(
    val value: Long,
)

/** What the player calls an account, e.g. "Main". Unique among the player's profiles, ignoring case. */
@JvmInline
value class ProfileName(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ProfileName must not be blank" }
    }

    fun sameAs(other: ProfileName) = value.trim().equals(other.value.trim(), ignoreCase = true)
}

/** One game account: the player can run several instances of the game, each with its own collection. */
data class Profile(
    val id: ProfileId,
    val name: ProfileName,
)

/** All profiles and the one the app currently works with. */
data class Profiles(
    val all: List<Profile>,
    val active: Profile,
)
