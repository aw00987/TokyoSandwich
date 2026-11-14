package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ROOT_ID
import com.github.aw00987.tokyosandwich.core.data.ledger.defaultTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val rent = CostItem(5, ROOT_ID, "rent", kind = ItemKind.Cost, createdAt = 5)
    private val fun_ = CostItem(6, ROOT_ID, "fun", kind = ItemKind.Set, createdAt = 6)
    private val items = defaultTree().items + rent + fun_
    private val water = items.first { it.provider == ProviderKind.Water }

    private fun keys(layout: HomeLayout) = layout.cards.map { it.key }

    @Test
    fun aNewInstallationHasTheThreeUtilities() {
        assertEquals(listOf("p:Water", "p:Electricity", "p:Gas"), keys(AppSettings().home))
        assertEquals(
            AppSettings().home,
            decodeStored(AppSettings.serializer(), "{}".encodeToByteArray()).home,
        )
    }

    @Test
    fun theLayoutIsSavedWithTheSettings() {
        val settings = AppSettings(
            costs = defaultTree(),
            home = HomeLayout(listOf(homeCardOf(fun_), homeCardOf(water))),
        )
        val bytes = encodeStored(AppSettings.serializer(), settings)
        assertEquals(settings, decodeStored(AppSettings.serializer(), bytes))
        // An emptied home tab stays empty: it is not the default.
        val empty = AppSettings(home = HomeLayout(emptyList()))
        assertEquals(
            empty,
            decodeStored(AppSettings.serializer(), encodeStored(AppSettings.serializer(), empty)),
        )
    }

    @Test
    fun anItemsCardFollowsWhatItIs() {
        assertEquals(
            HomeCard(HomeCardKind.Provider, provider = ProviderKind.Water),
            homeCardOf(water)
        )
        assertEquals(HomeCard(HomeCardKind.Cost, itemId = 5), homeCardOf(rent))
        assertEquals(HomeCard(HomeCardKind.Pie, itemId = 6), homeCardOf(fun_))
    }

    @Test
    fun anItemHasOneCard() {
        val layout = HomeLayout().plus(homeCardOf(rent))
        assertEquals(listOf("p:Water", "p:Electricity", "p:Gas", "i:5"), keys(layout))
        assertTrue(homeCardOf(rent) in layout)
        assertFalse(homeCardOf(fun_) in layout)
        assertSame(layout, layout.plus(homeCardOf(rent)))
        assertSame(layout, layout.plus(homeCardOf(water)))
        assertEquals(listOf("p:Water", "p:Gas", "i:5"), keys(layout.minus(homeCardOf(items[1]))))
        // Put back where it was (undo).
        assertEquals(
            keys(layout),
            keys(layout.minus(homeCardOf(items[1])).plus(homeCardOf(items[1]), 1)),
        )
    }

    @Test
    fun aCardMovesToAPosition() {
        val layout = HomeLayout().plus(homeCardOf(rent))
        assertEquals(
            listOf("i:5", "p:Water", "p:Electricity", "p:Gas"),
            keys(layout.moved("i:5", 0))
        )
        assertEquals(
            listOf("p:Electricity", "p:Gas", "p:Water", "i:5"),
            keys(layout.moved("p:Water", 2))
        )
        assertEquals(
            listOf("p:Electricity", "p:Gas", "i:5", "p:Water"),
            keys(layout.moved("p:Water", 9))
        )
        assertSame(layout, layout.moved("i:99", 0))
    }

    @Test
    fun cardsOfItemsThatAreGoneAreDropped() {
        val layout = HomeLayout().plus(homeCardOf(rent)).plus(homeCardOf(fun_))
        assertSame(layout, layout.pruned(items))
        assertEquals(
            listOf("p:Water", "p:Electricity", "p:Gas", "i:6"),
            keys(layout.pruned(items - rent))
        )
        // A card of the wrong kind for its item, one that names nothing, and a second card of an item.
        val broken = HomeLayout(
            listOf(
                HomeCard(HomeCardKind.Pie, itemId = 5),
                HomeCard(HomeCardKind.Cost, itemId = 6),
                HomeCard(HomeCardKind.Provider),
                HomeCard(HomeCardKind.Cost),
                homeCardOf(rent),
                homeCardOf(rent),
            ),
        )
        assertEquals(listOf("i:5"), keys(broken.pruned(items)))
    }
}
