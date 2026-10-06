package dev.pocketdeck.domain.builder

import dev.pocketdeck.domain.card.Attack
import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardCatalog
import dev.pocketdeck.domain.card.CardId
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import dev.pocketdeck.domain.collection.CardCollection
import dev.pocketdeck.domain.collection.Inventory
import dev.pocketdeck.domain.deck.Deck
import dev.pocketdeck.domain.deck.DeckValidator
import dev.pocketdeck.domain.meta.Archetype
import dev.pocketdeck.domain.meta.ArchetypeName
import dev.pocketdeck.domain.meta.CardPopularity
import dev.pocketdeck.domain.meta.DecklistEntry
import dev.pocketdeck.domain.meta.MetaDecklist
import dev.pocketdeck.domain.meta.MetaSnapshot
import dev.pocketdeck.domain.meta.TournamentResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HeuristicDeckBuilderTest {
    private fun pokemon(
        name: String,
        type: EnergyType = EnergyType.FIRE,
        stage: Stage = Stage.BASIC,
        from: String? = null,
        hp: Int = 70,
        attack: Attack = Attack("Hit", "${type.symbol}C", 40, null),
        ability: dev.pocketdeck.domain.card.Ability? = null,
    ) = PokemonCard(
        id = CardId("p-$name"),
        name = CardName(name),
        energyType = type,
        stage = stage,
        hp = hp,
        evolvesFrom = from?.let(::CardName),
        gameplayKey = GameplayKey("$name#p"),
        attacks = listOf(attack),
        ability = ability,
    )

    private fun trainer(
        name: String,
        kind: TrainerKind,
        text: String?,
    ) = TrainerCard(CardId("t-$name"), CardName(name), kind, GameplayKey("$name#t"), effect = text)

    private val pokeBall =
        trainer("Poké Ball", TrainerKind.ITEM, "Put 1 random Basic Pokémon from your deck into your hand.")
    private val research = trainer("Research", TrainerKind.SUPPORTER, "Draw 2 cards.")
    private val potion = trainer("Potion", TrainerKind.ITEM, "Heal 20 damage from 1 of your Pokémon.")
    private val blank = trainer("Blank", TrainerKind.TOOL, null)
    private val trainers = listOf(pokeBall, research, potion, blank)

    private val charmander = pokemon("Charmander", hp = 60)
    private val charmeleon = pokemon("Charmeleon", stage = Stage.STAGE_1, from = "Charmander", hp = 90)
    private val charizard = pokemon("Charizard ex", stage = Stage.STAGE_2, from = "Charmeleon", hp = 180)
    private val magmar = pokemon("Magmar", hp = 70)
    private val vulpix = pokemon("Vulpix", hp = 60)
    private val growlithe = pokemon("Growlithe", hp = 70)
    private val squirtle = pokemon("Squirtle", type = EnergyType.WATER, hp = 60)
    private val fire = listOf(charmander, charmeleon, charizard, magmar, vulpix, growlithe)

    private fun inventory(
        cards: List<Card>,
        copies: Int = 2,
        catalog: List<Card> = cards,
    ): Pair<CardCatalog, Inventory> {
        val everything = CardCatalog((catalog + cards).distinct())
        val owned = cards.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, copies) }
        return everything to Inventory.of(owned, everything)
    }

    private fun build(
        cards: List<Card>,
        focus: DeckFocus = DeckFocus(),
        copies: Int = 2,
    ): BuiltDeck? {
        val (catalog, inventory) = inventory(cards, copies)
        return HeuristicDeckBuilder().build(catalog, inventory, focus)
    }

    @Test
    fun `builds a legal deck of twenty cards from what the player owns`() {
        val built = build(fire + trainers)!!

        built.deck.size shouldBe Deck.SIZE
        DeckValidator().validate(built.deck) shouldBe emptyList()
    }

    @Test
    fun `never uses a card the player does not own`() {
        val (catalog, inventory) = inventory(fire + trainers, catalog = fire + trainers + squirtle)

        val deck = HeuristicDeckBuilder().build(catalog, inventory)!!.deck

        deck.entries.none { it.card == squirtle } shouldBe true
    }

    @Test
    fun `never uses more copies than the player owns`() {
        val built = build(fire + trainers, copies = 1)

        built?.deck?.entries?.all { it.copies <= 1 } ?: true shouldBe true
    }

    @Test
    fun `plays a whole evolution line instead of an evolution without its predecessors`() {
        val deck = build(fire + trainers, DeckFocus(core = charizard))!!.deck

        val names = deck.entries.map { it.card.name.value }
        (listOf("Charmander", "Charmeleon", "Charizard ex").all { it in names }) shouldBe true
    }

    @Test
    fun `builds around the card the player chose`() {
        val deck = build(fire + trainers, DeckFocus(core = magmar))!!.deck

        deck.entries.any { it.card == magmar && it.copies == 2 } shouldBe true
    }

    @Test
    fun `only plays energy the deck can produce`() {
        val (catalog, inventory) = inventory(fire + squirtle + trainers)

        val deck =
            HeuristicDeckBuilder()
                .build(
                    catalog,
                    inventory,
                    DeckFocus(energyTypes = setOf(EnergyType.FIRE)),
                )!!
                .deck

        deck.energyTypes shouldBe setOf(EnergyType.FIRE)
        deck.entries.none { it.card == squirtle } shouldBe true
    }

    @Test
    fun `prefers trainers that draw and search over ones without an effect`() {
        val deck = build(fire + trainers)!!.deck

        deck.entries.any { it.card == research } shouldBe true
        deck.entries.any { it.card == pokeBall } shouldBe true
    }

    @Test
    fun `chooses the energy that gives the strongest deck when none is given`() {
        val waterStars =
            (1..8).map {
                pokemon("Wave $it", type = EnergyType.WATER, hp = 120, attack = Attack("Surf", "WC", 120, null))
            }

        val deck = build(fire + waterStars + trainers)!!.deck

        deck.energyTypes shouldBe setOf(EnergyType.WATER)
    }

    @Test
    fun `cannot build when the player owns too few cards`() {
        build(listOf(charmander, magmar)) shouldBe null
    }

    @Test
    fun `builds the same deck every time`() {
        build(fire + trainers)!!.deck shouldBe build(fire + trainers)!!.deck
    }

    @Test
    fun `reports the centrepiece of the deck`() {
        build(fire + trainers, DeckFocus(core = charizard))!!.centrepiece shouldBe charizard
    }

    @Test
    fun `plays no more copies of an evolution than of the card it evolves from`() {
        val everything = fire + pokemon("Ponyta") + pokemon("Slugma") + trainers
        val catalog = CardCatalog(everything)
        val owned =
            everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, if (card == charmander) 1 else 2) }

        val deck =
            HeuristicDeckBuilder()
                .build(
                    catalog,
                    Inventory.of(owned, catalog),
                    DeckFocus(core = charizard),
                )!!
                .deck

        fun copies(card: Card) = deck.entries.filter { it.card == card }.sumOf { it.copies }
        (copies(charmeleon) <= copies(charmander)) shouldBe true
        (copies(charizard) <= copies(charmeleon)) shouldBe true
    }

    @Test
    fun `prefers the trainer strong players choose over one that only looks useful`() {
        val shiny = trainer("Shiny", TrainerKind.SUPPORTER, "Draw 2 cards. Heal 20 damage.")
        val staple = trainer("Staple", TrainerKind.ITEM, null)
        val everything = fire + shiny + staple + (1..6).map { trainer("Filler $it", TrainerKind.TOOL, null) }
        val catalog = CardCatalog(everything)
        val owned = everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val meta = MetaSnapshot(listOf(Archetype(ArchetypeName("A"), listOf(playing(staple), playing(staple)), 1.0)))

        val popular = CardPopularity.of(meta, catalog)
        val deck =
            HeuristicDeckBuilder().build(catalog, Inventory.of(owned, catalog), DeckFocus(), popular)!!.deck

        val trainerCopies = deck.entries.filter { it.card is TrainerCard }.associate { it.card to it.copies }
        (trainerCopies.getValue(staple) >= trainerCopies[shiny] ?: 0) shouldBe true
    }

    @Test
    fun `prefers the pokemon strong players choose when two are otherwise alike`() {
        val twin = pokemon("Twin", hp = 70)
        val cards = (1..4).map { pokemon("Rival $it", hp = 70) }
        val everything = cards + twin + trainers + (1..4).map { trainer("Filler $it", TrainerKind.TOOL, null) }
        val catalog = CardCatalog(everything)
        val owned = everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val meta = MetaSnapshot(listOf(Archetype(ArchetypeName("A"), listOf(playing(twin)), 1.0)))

        val built =
            HeuristicDeckBuilder().build(
                catalog,
                Inventory.of(owned, catalog),
                DeckFocus(),
                CardPopularity.of(meta, catalog),
            )!!

        built.centrepiece shouldBe twin
    }

    private fun playing(card: Card) =
        MetaDecklist(
            ArchetypeName("A"),
            listOf(DecklistEntry(card.id, 2)),
            setOf(EnergyType.FIRE),
            TournamentResult(1, 3, 1, 0),
        )

    @Test
    fun `follows what is played together with the chosen pokemon`() {
        val partner = trainer("Partner", TrainerKind.ITEM, null)
        val crowd = trainer("Crowd", TrainerKind.ITEM, null)
        val everything = fire + partner + crowd + (1..6).map { trainer("Filler $it", TrainerKind.TOOL, null) }
        val catalog = CardCatalog(everything)
        val owned = everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        // The crowd favourite is in many more decks, but the decks of the chosen Pokémon play the partner.
        val decks = List(6) { playing(magmar, partner) } + List(40) { playing(vulpix, crowd) }
        val popularity = CardPopularity.of(MetaSnapshot(listOf(Archetype(ArchetypeName("A"), decks, 1.0))), catalog)

        val deck =
            HeuristicDeckBuilder()
                .build(
                    catalog,
                    Inventory.of(owned, catalog),
                    DeckFocus(core = magmar),
                    popularity,
                )!!
                .deck

        fun copies(card: Card) = deck.entries.filter { it.card == card }.sumOf { it.copies }
        (copies(partner) > copies(crowd)) shouldBe true
    }

    private fun playing(vararg cards: Card) =
        MetaDecklist(
            ArchetypeName("A"),
            cards.map { DecklistEntry(it.id, 2) },
            setOf(EnergyType.FIRE),
            TournamentResult(1, 3, 1, 0),
        )

    @Test
    fun `leaves out a trainer that has nothing to act on in the deck, however popular it is`() {
        val waterHeal =
            trainer(
                "Irida",
                TrainerKind.SUPPORTER,
                "Heal 40 damage from each of your Pokémon that has any [ W ] Energy attached.",
            )
        val rocketSearch =
            trainer(
                "Rocket Researcher",
                TrainerKind.SUPPORTER,
                "Put a random Pokémon that has “Team Rocket” in its name from your deck into your hand.",
            )
        val everything =
            fire + waterHeal + rocketSearch + trainers + (1..4).map { trainer("Filler $it", TrainerKind.TOOL, null) }
        val catalog = CardCatalog(everything)
        val owned = everything.fold(CardCollection.EMPTY) { c, card -> c.withCopies(card.id, 2) }
        val meta =
            MetaSnapshot(listOf(Archetype(ArchetypeName("A"), List(10) { playing(waterHeal, rocketSearch) }, 1.0)))

        val deck =
            HeuristicDeckBuilder()
                .build(
                    catalog,
                    Inventory.of(owned, catalog),
                    DeckFocus(energyTypes = setOf(EnergyType.FIRE)),
                    CardPopularity.of(meta, catalog),
                )!!
                .deck

        deck.entries.none { it.card == waterHeal || it.card == rocketSearch } shouldBe true
    }
}
