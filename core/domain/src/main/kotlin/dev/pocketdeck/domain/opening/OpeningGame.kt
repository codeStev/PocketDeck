package dev.pocketdeck.domain.opening

import dev.pocketdeck.domain.card.Card
import dev.pocketdeck.domain.card.CardName
import dev.pocketdeck.domain.card.EnergyType
import dev.pocketdeck.domain.card.GameplayKey
import dev.pocketdeck.domain.card.PokemonCard
import dev.pocketdeck.domain.card.Stage
import dev.pocketdeck.domain.card.TrainerCard
import dev.pocketdeck.domain.card.TrainerKind
import kotlin.random.Random

/** One simulation setup: who starts, and the evolution line the player builds the turns around (empty: no favourite). */
internal class Scenario(
    val order: TurnOrder,
    val focus: Set<CardName> = emptySet(),
)

/**
 * One shuffled deck being played: hand, board, Energy Zone and discard pile. Methods are single moves; the order they
 * are tried in is the player's policy and lives in [OpeningSimulator].
 */
internal class OpeningGame(
    private val plan: DeckPlan,
    private val random: Random,
    private val scenario: Scenario,
) {
    private val flow = CardFlow(plan.cards, random, ::onTaken)
    private val board = Board()
    private val discardPile = mutableListOf<EnergyType>()

    /** The one energy waiting in the Energy Zone. It stays there, blocking the next, until it is attached. */
    private var zone: EnergyType? = null
    private var turn = 0
    private var turnEnded = false
    private var supporterUsed = false
    private var retreated = false
    private var retreatDiscount = 0

    /** Cards that are playable by now: trainers once held, Pokémon once on the board (evolutions only after evolving). */
    val playable = mutableSetOf<GameplayKey>()

    /** Pokémon that have attacked by now, that have used their strongest attack by now, and that attacked this turn. */
    val attacking = mutableSetOf<GameplayKey>()
    val attackingFully = mutableSetOf<GameplayKey>()
    val attackedNow = mutableSetOf<GameplayKey>()

    private val order get() = scenario.order

    /** Deals the opening hand and sets up the Basics. The game guarantees a Basic in the starting hand. */
    fun deal() {
        flow.dealOpening { isBasic(it) }
        flow.hand.forEach(::onTaken)
        placeBasics()
    }

    fun beginTurn(number: Int) {
        turn = number
        turnEnded = false
        supporterUsed = false
        retreated = false
        retreatDiscount = 0
        attackedNow.clear()
        flow.draw(1)
        if (zone == null) zone = plan.energyTypes.randomOrNull(random)
    }

    /** Does every move that is free and helps, until nothing more changes. */
    fun settle() {
        while (placeBasics() || evolve() || useAbilities() || playItem()) {
            // each pass can open the next: a searched Basic can be placed, a placed Basic can be evolved
        }
    }

    /** Plays the supporter that helps most: energy first, then drawing; reshuffling throws cards away, so it is last. */
    fun playSupporter() {
        val chosen = usable(TrainerKind.SUPPORTER).minByOrNull { (_, effect) -> supporterRank(effect) } ?: return
        supporterUsed = true
        play(chosen.first, chosen.second)
    }

    /** Attaches the Energy Zone's energy (once per turn), unless the first player's first turn forbids it. */
    fun attachZoneEnergy() {
        val type = zone ?: return
        if (turn == 1 && !order.getsEnergyOnFirstTurn) return
        val slot = (if (needsRetreatFunding()) board.active else chooseSlot(listOf(type), null)) ?: return
        slot.energy += type
        zone = null
    }

    /** Brings the Pokémon the deck is built around into the Active Spot: a free switch first, a retreat otherwise. */
    fun promote() {
        val goal = promotionGoal() ?: return
        val free = findFreeSwitch(goal)
        if (free != null) applySwitch(free, goal) else retreat(goal)
    }

    /** Ends the turn: a turn-ending energy move if no attack is ready anyway, the attack, then end-of-turn abilities. */
    fun finishTurn() {
        if (!attackReady()) useTurnEndingEffect()
        attack()
        if (turn == 1) board.all.forEach(::useFirstTurnAbility)
    }

    private fun onTaken(card: Card) {
        if (card !is PokemonCard) playable += card.gameplayKey
    }

    private fun isBasic(card: Card) = card is PokemonCard && card.stage == Stage.BASIC

    private fun isFocus(slot: InPlay) = slot.card.name in scenario.focus

    // --- board ---

    private fun placeBasics(): Boolean {
        var placed = false
        while (board.hasRoom) {
            val basic =
                flow.hand
                    .filterIsInstance<PokemonCard>()
                    .filter { isBasic(it) }
                    .minWithOrNull(SETUP_ORDER) ?: break
            flow.hand.remove(basic)
            board.put(basic, turn)
            playable += basic.gameplayKey
            placed = true
        }
        return placed
    }

    private val SETUP_ORDER: Comparator<PokemonCard>
        get() =
            compareBy({
                if (it.name in
                    scenario.focus
                ) {
                    0
                } else {
                    1
                }
            }, { it.attacks.minOfOrNull { attack -> attack.cost.length } ?: Int.MAX_VALUE })

    /** The first player does not evolve on their first turn; nobody evolves a Pokémon that arrived or evolved this turn. */
    private fun evolve(): Boolean {
        if (turn == 1 && !order.canEvolveOnFirstTurn) return false
        for (slot in board.all.filter { it.changedOnTurn < turn }.sortedBy { if (isFocus(it)) 0 else 1 }) {
            val next =
                flow.hand.filterIsInstance<PokemonCard>().firstOrNull { it.evolvesFrom == slot.card.name } ?: continue
            flow.hand.remove(next)
            evolveInto(slot, next)
            return true
        }
        return false
    }

    private fun evolveInto(
        slot: InPlay,
        next: PokemonCard,
    ) {
        slot.card = next
        slot.changedOnTurn = turn
        playable += next.gameplayKey
        val effect = plan.effectOf(next) as? OpeningEffect.AttachEnergy
        if (effect?.trigger == AbilityTrigger.ON_EVOLVE) attach(effect, slot)
    }

    // --- trainers ---

    private fun playItem(): Boolean {
        val (card, effect) = usable(TrainerKind.ITEM).firstOrNull() ?: return false
        play(card, effect)
        return true
    }

    private fun usable(kind: TrainerKind): List<Pair<Card, OpeningEffect>> =
        flow.hand
            .filter { it is TrainerCard && it.kind == kind }
            .mapNotNull { card -> plan.effectOf(card)?.takeIf(::isUseful)?.let { card to it } }

    private fun supporterRank(effect: OpeningEffect): Int =
        when (effect) {
            is OpeningEffect.AttachEnergy, is OpeningEffect.MoveEnergy, is OpeningEffect.EvolveFromDeck -> 0
            is OpeningEffect.ShuffleHandAndDraw -> 2
            else -> 1
        }

    private fun isUseful(effect: OpeningEffect): Boolean =
        when (effect) {
            is OpeningEffect.Draw, is OpeningEffect.LookAtTop -> !flow.isLibraryEmpty
            is OpeningEffect.Search -> flow.libraryHas(effect.filter)
            is OpeningEffect.ShuffleHandAndDraw -> flow.hand.size - 1 <= SHUFFLE_HAND_LIMIT
            OpeningEffect.RareCandy -> candyTarget() != null
            is OpeningEffect.EvolveFromDeck -> deckEvolution(effect) != null
            is OpeningEffect.MoveEnergy -> movesFor(effect, null) != null
            is OpeningEffect.AttachEnergy -> !effect.endsTurn && canAttach(effect)
            is OpeningEffect.SwitchOwn, is OpeningEffect.RetreatDiscount, OpeningEffect.FreeRetreat -> false
        }

    private fun play(
        card: Card,
        effect: OpeningEffect,
    ) {
        flow.hand.remove(card)
        when (effect) {
            is OpeningEffect.Draw -> {
                flow.draw(effect.count)
            }

            is OpeningEffect.Search -> {
                flow.search(scaled(effect.count, effect.coin), effect.filter)
            }

            is OpeningEffect.LookAtTop -> {
                flow.lookAtTop(effect.count, effect.filter)
            }

            is OpeningEffect.ShuffleHandAndDraw -> {
                flow.shuffleHandAndDraw(effect.redrawCount)
            }

            OpeningEffect.RareCandy -> {
                candyTarget()?.let { (slot, stage2) ->
                    flow.hand.remove(stage2).also { evolveInto(slot, stage2) }
                }
            }

            is OpeningEffect.EvolveFromDeck -> {
                evolveFromDeck(effect)
            }

            is OpeningEffect.MoveEnergy -> {
                movesFor(effect, null)?.let(::applyMoves)
            }

            is OpeningEffect.AttachEnergy -> {
                attach(effect, null)
            }

            is OpeningEffect.SwitchOwn, is OpeningEffect.RetreatDiscount, OpeningEffect.FreeRetreat -> {
                // played only by promote(), which decides when a switch or a discount is worth it
            }
        }
    }

    /** Rare Candy's own text forbids it on the player's first turn, whoever starts. */
    private fun candyTarget(): Pair<InPlay, PokemonCard>? {
        if (turn == 1) return null
        return board.all
            .filter { it.card.stage == Stage.BASIC && it.changedOnTurn < turn }
            .firstNotNullOfOrNull { slot -> stage2InHandFor(slot)?.let { slot to it } }
    }

    private fun stage2InHandFor(slot: InPlay): PokemonCard? =
        flow.hand.filterIsInstance<PokemonCard>().firstOrNull {
            it.stage == Stage.STAGE_2 &&
                plan.basicOf(it) == slot.card.name
        }

    /** Quick-Grow Extract: a random evolution from the deck lands on a Pokémon that has been in play since an earlier turn. */
    private fun deckEvolution(effect: OpeningEffect.EvolveFromDeck): InPlay? {
        if (turn == 1) return null
        return board.all
            .filter { it.changedOnTurn < turn && effect.target.matches(it.card, board.isActive(it)) }
            .sortedBy { if (isFocus(it)) 0 else 1 }
            .firstOrNull { slot -> flow.libraryHas { evolvesFrom(it, slot) && effect.evolution.matches(it) } }
    }

    private fun evolvesFrom(
        card: Card,
        slot: InPlay,
    ) = card is PokemonCard && card.evolvesFrom == slot.card.name

    private fun evolveFromDeck(effect: OpeningEffect.EvolveFromDeck) {
        val slot = deckEvolution(effect) ?: return
        val next = flow.pull { evolvesFrom(it, slot) && effect.evolution.matches(it) } as? PokemonCard ?: return
        evolveInto(slot, next)
    }

    // --- energy ---

    private class Moves(
        val destination: InPlay,
        val units: List<Pair<InPlay, EnergyType>>,
    )

    /**
     * The energy a move effect would carry to its destination, or null when it is not worth playing: nothing to move, or
     * the destination gains nothing. Energy is never taken from the Pokémon the deck is built around.
     */
    private fun movesFor(
        effect: OpeningEffect.MoveEnergy,
        bearer: InPlay?,
    ): Moves? {
        val destination =
            (if (effect.to == null) bearer else board.active?.takeIf { effect.to.matches(it.card, true) })
                ?: return null
        val sources =
            board.all.filter {
                it !== destination && !isFocus(it) &&
                    effect.from.matches(it.card, board.isActive(it))
            }
        val units =
            (if (effect.fromEach) sources else sources.take(1)).flatMap { source ->
                source.energy.filter { effect.types == null || it in effect.types }.map { source to it }
            }
        val carried = if (effect.repeatable) units else effect.count?.let(units::take) ?: units
        val worthIt = isFocus(destination) || carried.any { (_, type) -> benefit(destination, type) > 0 }
        return Moves(destination, carried).takeIf { carried.isNotEmpty() && worthIt }
    }

    private fun applyMoves(moves: Moves) {
        moves.units.forEach { (source, type) ->
            source.energy.remove(type)
            moves.destination.energy += type
        }
    }

    private fun canAttach(effect: OpeningEffect.AttachEnergy): Boolean =
        availableTypes(effect).isNotEmpty() &&
            (effect.target == null || chooseSlot(availableTypes(effect), effect.target) != null)

    /** The types the effect can hand out now: the zone makes the named type, the discard pile only what is in it. */
    private fun availableTypes(effect: OpeningEffect.AttachEnergy): List<EnergyType> =
        when {
            effect.source == EnergySource.ENERGY_ZONE -> listOfNotNull(effect.energy)
            effect.energy == null -> discardPile.distinct()
            effect.energy in discardPile -> listOf(effect.energy)
            else -> emptyList()
        }

    private fun attach(
        effect: OpeningEffect.AttachEnergy,
        bearer: InPlay?,
    ) {
        val slot = (if (effect.target == null) bearer else chooseSlot(availableTypes(effect), effect.target)) ?: return
        repeat(grantedCount(effect)) {
            val type = availableTypes(effect).maxByOrNull { benefit(slot, it) } ?: return
            if (effect.source == EnergySource.DISCARD_PILE) discardPile.remove(type)
            slot.energy += type
        }
    }

    private fun grantedCount(effect: OpeningEffect.AttachEnergy): Int = scaled(effect.count, effect.coin)

    private fun scaled(
        count: Int,
        coin: CoinRule,
    ): Int =
        when (coin) {
            CoinRule.NONE -> count
            CoinRule.FLIP_ONCE -> if (random.nextBoolean()) count else 0
            CoinRule.UNTIL_TAILS -> count * generateSequence { random.nextBoolean() }.takeWhile { it }.count()
        }

    private fun useAbilities(): Boolean {
        for (slot in board.all.filter { it.abilityUsedOnTurn != turn }) {
            if (useAttachAbility(slot) || useMoveAbility(slot)) {
                slot.abilityUsedOnTurn = turn
                return true
            }
        }
        return false
    }

    private fun useAttachAbility(slot: InPlay): Boolean {
        val effect = plan.effectOf(slot.card) as? OpeningEffect.AttachEnergy ?: return false
        if (!canUseAbility(effect, slot) || effect.endsTurn) return false
        attach(effect, slot)
        return true
    }

    private fun useMoveAbility(slot: InPlay): Boolean {
        val effect = plan.effectOf(slot.card) as? OpeningEffect.MoveEnergy ?: return false
        val moves = movesFor(effect, slot) ?: return false
        applyMoves(moves)
        return true
    }

    private fun canUseAbility(
        effect: OpeningEffect.AttachEnergy,
        slot: InPlay,
    ): Boolean =
        effect.trigger == AbilityTrigger.ON_DEMAND &&
            slot.abilityUsedOnTurn != turn &&
            placementAllows(effect.bearerPlacement, slot) &&
            canAttach(effect)

    private fun placementAllows(
        placement: Placement,
        slot: InPlay,
    ): Boolean =
        when (placement) {
            Placement.ANYWHERE -> true
            Placement.ACTIVE -> board.isActive(slot)
            Placement.BENCH -> !board.isActive(slot)
        }

    private fun useFirstTurnAbility(slot: InPlay) {
        val effect = plan.effectOf(slot.card) as? OpeningEffect.AttachEnergy ?: return
        if (effect.trigger == AbilityTrigger.END_OF_FIRST_TURN) attach(effect, slot)
    }

    /** Kiawe or an ability whose text ends the turn: worth it only when no attack was ready to be given up. */
    private fun useTurnEndingEffect() {
        val ability =
            board.all.firstOrNull { slot ->
                (plan.effectOf(slot.card) as? OpeningEffect.AttachEnergy)?.let { endsTurnAndUsable(it, slot) } ==
                    true
            }
        if (ability != null) {
            ability.abilityUsedOnTurn = turn
            attach(plan.effectOf(ability.card) as OpeningEffect.AttachEnergy, ability)
            turnEnded = true
            return
        }
        val supporter = supporterEndingTurn() ?: return
        supporterUsed = true
        play(supporter.first, supporter.second)
        turnEnded = true
    }

    private fun endsTurnAndUsable(
        effect: OpeningEffect.AttachEnergy,
        slot: InPlay,
    ) = effect.endsTurn && canUseAbility(effect, slot)

    private fun supporterEndingTurn(): Pair<Card, OpeningEffect>? {
        if (supporterUsed) return null
        return flow.hand
            .filter { it is TrainerCard && it.kind == TrainerKind.SUPPORTER }
            .mapNotNull { card ->
                (plan.effectOf(card) as? OpeningEffect.AttachEnergy)
                    ?.takeIf {
                        it.endsTurn &&
                            canAttach(it)
                    }?.let { card to it }
            }.firstOrNull()
    }

    /** The slot energy goes to: the deck's favourite line, then where it makes an attack payable, then the Active Pokémon. */
    private fun chooseSlot(
        types: List<EnergyType>,
        filter: SlotFilter?,
    ): InPlay? =
        board.all
            .filter { filter == null || filter.matches(it.card, board.isActive(it)) }
            .maxWithOrNull(
                compareBy<InPlay>(
                    { if (isFocus(it)) 1 else 0 },
                    { slot -> types.maxOfOrNull { benefit(slot, it) } ?: 0 },
                    { if (board.isActive(it)) 1 else 0 },
                    { it.card.stage },
                ),
            )

    private fun benefit(
        slot: InPlay,
        type: EnergyType,
    ): Int =
        when {
            payable(slot, type) && !payable(slot, null) -> 2
            AttackCost.helps(slot.card.attacks, slot.energy, type) -> 1
            else -> 0
        }

    private fun payable(
        slot: InPlay,
        extra: EnergyType?,
    ): Boolean = slot.card.attacks.any { AttackCost.canPay(it.cost, slot.energy + listOfNotNull(extra)) }

    // --- active spot ---

    private fun promotionGoal(): InPlay? {
        val active = board.active ?: return null
        if (scenario.focus.isEmpty() || isFocus(active)) return null
        return board.bench.filter(::isFocus).maxByOrNull { it.card.stage }
    }

    private class SwitchSource(
        val bearer: InPlay?,
        val supporter: Card?,
    )

    private fun findFreeSwitch(goal: InPlay): SwitchSource? {
        val active = board.active ?: return null
        val bearer = board.all.firstOrNull { canSwitchWith(it, goal, active) }
        if (bearer != null) return SwitchSource(bearer, null)
        if (supporterUsed) return null
        val card = flow.hand.firstOrNull { isSwitchSupporter(it, active) } ?: return null
        return SwitchSource(null, card)
    }

    private fun canSwitchWith(
        slot: InPlay,
        goal: InPlay,
        active: InPlay,
    ): Boolean {
        val effect = plan.effectOf(slot.card) as? OpeningEffect.SwitchOwn ?: return false
        if (slot.abilityUsedOnTurn == turn) return false
        return if (effect.bearerComesIn) slot === goal else effect.active.matches(active.card, true)
    }

    private fun isSwitchSupporter(
        card: Card,
        active: InPlay,
    ): Boolean {
        val effect = plan.effectOf(card) as? OpeningEffect.SwitchOwn
        return card is TrainerCard && card.kind == TrainerKind.SUPPORTER && effect != null && !effect.bearerComesIn &&
            effect.active.matches(active.card, true)
    }

    private fun applySwitch(
        source: SwitchSource,
        goal: InPlay,
    ) {
        source.bearer?.abilityUsedOnTurn = turn
        source.supporter?.let {
            flow.hand.remove(it)
            supporterUsed = true
        }
        board.swapActiveWith(goal)
    }

    private fun retreatCost(slot: InPlay): Int {
        val free = board.all.any { plan.effectOf(it.card) == OpeningEffect.FreeRetreat }
        return if (free) 0 else (slot.card.retreatCost - retreatDiscount).coerceAtLeast(0)
    }

    /** Pays the Retreat Cost with the Active Pokémon's own energy (it goes to the discard pile) and swaps [goal] in. */
    private fun retreat(goal: InPlay) {
        val active = board.active ?: return
        if (retreated) return
        while (active.energy.size < retreatCost(active) && playRetreatDiscount()) {
            // a discount card can make the retreat affordable
        }
        val cost = retreatCost(active)
        if (active.energy.size < cost) return
        repeat(cost) { discardPile += active.energy.removeAt(0) }
        board.swapActiveWith(goal)
        retreated = true
    }

    private fun playRetreatDiscount(): Boolean {
        val card =
            flow.hand.firstOrNull {
                it is TrainerCard && it.kind == TrainerKind.ITEM &&
                    plan.effectOf(it) is OpeningEffect.RetreatDiscount
            }
                ?: flow.hand.firstOrNull {
                    !supporterUsed && it is TrainerCard && it.kind == TrainerKind.SUPPORTER &&
                        plan.effectOf(it) is OpeningEffect.RetreatDiscount
                }
                ?: return false
        flow.hand.remove(card)
        if ((card as TrainerCard).kind == TrainerKind.SUPPORTER) supporterUsed = true
        retreatDiscount += (plan.effectOf(card) as OpeningEffect.RetreatDiscount).amount
        return true
    }

    /** Whether the Active Pokémon is short only of the energy to retreat, with no free switch to rely on. */
    private fun needsRetreatFunding(): Boolean {
        val goal = promotionGoal() ?: return false
        val active = board.active ?: return false
        return !retreated && retreatCost(active) > active.energy.size && findFreeSwitch(goal) == null
    }

    // --- attacking ---

    private fun attackReady(): Boolean = board.active?.let { payable(it, null) } ?: false

    /** The Active Pokémon uses its strongest attack it can pay; attacks that drain energy do so. */
    private fun attack() {
        val slot = board.active ?: return
        if (turnEnded || (turn == 1 && !order.canAttackOnFirstTurn)) return
        val chosen =
            slot.card.attacks
                .filter { AttackCost.canPay(it.cost, slot.energy) }
                .maxByOrNull { it.damage ?: 0 }
                ?: return
        val key = slot.card.gameplayKey
        attacking += key
        attackedNow += key
        if (chosen == AttackCost.mainOf(slot.card.attacks)) attackingFully += key
        plan.discardOf(chosen)?.let { discard(slot, it) }
    }

    private fun discard(
        slot: InPlay,
        rule: EnergyDiscard,
    ) {
        val removed = if (rule.onePerType) onePerType(slot, rule.types.orEmpty()) else chosenByRule(slot, rule)
        removed.forEach { slot.energy.remove(it) }
        discardPile += removed
    }

    private fun onePerType(
        slot: InPlay,
        types: Set<EnergyType>,
    ): List<EnergyType> = types.filter { it in slot.energy }

    private fun chosenByRule(
        slot: InPlay,
        rule: EnergyDiscard,
    ): List<EnergyType> {
        val pool = slot.energy.filter { rule.types == null || it in rule.types }
        val ordered = if (rule.random) pool.shuffled(random) else pool
        return rule.count?.let(ordered::take) ?: ordered
    }

    private companion object {
        /** A reshuffle pays off when the hand is nearly empty; with more cards it throws good ones away. */
        const val SHUFFLE_HAND_LIMIT = 3
    }
}
