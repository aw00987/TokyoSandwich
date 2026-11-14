package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import kotlinx.serialization.Serializable

/**
 * What a card on the home tab shows: a utility's usage and cost ([Provider]), a cost's amount ([Cost]) or a set's
 * make-up as a ring chart ([Pie]).
 */
@Serializable
enum class HomeCardKind { Provider, Cost, Pie }

/**
 * One card of the home tab. A [HomeCardKind.Provider] card names its [provider]; the others the cost item they show
 * ([itemId]). An item has at most one card.
 */
@Serializable
data class HomeCard(
    val kind: HomeCardKind,
    val provider: ProviderKind? = null,
    val itemId: Long? = null,
) {
    /** Stable while the card exists; the key of its row on the home tab. */
    val key: String get() = if (kind == HomeCardKind.Provider) "p:$provider" else "i:$itemId"
}

/** The cards a new installation starts with: the three utilities. */
val defaultHomeCards: List<HomeCard> =
    listOf(ProviderKind.Water, ProviderKind.Electricity, ProviderKind.Gas)
        .map { HomeCard(HomeCardKind.Provider, provider = it) }

/** The card that shows [item]: a utility's own card for its built-in item, a ring chart for a set. */
fun homeCardOf(item: CostItem): HomeCard = when {
    item.provider != null -> HomeCard(HomeCardKind.Provider, provider = item.provider)
    item.kind == ItemKind.Set -> HomeCard(HomeCardKind.Pie, itemId = item.id)
    else -> HomeCard(HomeCardKind.Cost, itemId = item.id)
}

/** The home tab's cards from top to bottom, one per row. Part of the settings, so an export carries it. */
@Serializable
data class HomeLayout(val cards: List<HomeCard> = defaultHomeCards) {
    operator fun contains(card: HomeCard): Boolean = cards.any { it.key == card.key }

    /** With [card] at the bottom, or at [index]; unchanged when the layout has it already. */
    fun plus(card: HomeCard, index: Int = cards.size): HomeLayout =
        if (card in this) this
        else copy(cards = cards.toMutableList().apply { add(index.coerceIn(0, size), card) })

    fun minus(card: HomeCard): HomeLayout = copy(cards = cards.filter { it.key != card.key })

    /** With the card [key] at position [to] (counted without it); unchanged when there is no such card. */
    fun moved(key: String, to: Int): HomeLayout {
        val card = cards.firstOrNull { it.key == key } ?: return this
        val rest = cards.filter { it.key != key }
        return copy(cards = rest.toMutableList().apply { add(to.coerceIn(0, size), card) })
    }

    /**
     * Without the cards that fit no item of the tree: an item that is gone or is of the other kind, a card without
     * what it names, a second card of one item.
     */
    fun pruned(items: List<CostItem>): HomeLayout {
        val byId = items.associateBy { it.id }
        val kept = cards.filter { card ->
            when (card.kind) {
                HomeCardKind.Provider -> card.provider != null
                HomeCardKind.Cost, HomeCardKind.Pie ->
                    card.itemId?.let(byId::get)?.let { homeCardOf(it) } == card
            }
        }.distinctBy { it.key }
        return if (kept == cards) this else copy(cards = kept)
    }
}
