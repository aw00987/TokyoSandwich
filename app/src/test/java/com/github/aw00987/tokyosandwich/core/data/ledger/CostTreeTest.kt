package com.github.aw00987.tokyosandwich.core.data.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostTreeTest {
    private fun item(id: Long, parent: Long?, kind: ItemKind, provider: ProviderKind? = null) =
        CostItem(
            id = id,
            parentId = parent,
            title = "item $id",
            kind = kind,
            provider = provider,
            createdAt = id
        )

    private fun set(id: Long, parent: Long?) = item(id, parent, ItemKind.Set)
    private fun cost(id: Long, parent: Long) = item(id, parent, ItemKind.Cost)

    // 1 ─┬─ 2 (water)
    //    ├─ 3 ─┬─ 5 ── 6
    //    │     └─ 7
    //    ├─ 4
    //    └─ 8 ── 9
    // Sets: 1, 3, 5, 4, 8, 9. Costs: 2, 6, 7.
    private val tree = listOf(
        set(1, null),
        item(2, 1, ItemKind.Cost, ProviderKind.Water),
        set(3, 1),
        set(4, 1),
        set(5, 3),
        cost(6, 5),
        cost(7, 3),
        set(8, 1),
        set(9, 8),
    )

    @Test
    fun findsTheRootChildrenAndPath() {
        assertEquals(1L, tree.root()?.id)
        assertEquals(listOf(2L, 3L, 4L, 8L), tree.childrenOf(1).map { it.id })
        assertEquals(listOf(1L, 3L, 5L, 6L), tree.pathTo(6).map { it.id })
        assertEquals(emptyList<CostItem>(), tree.pathTo(99))
    }

    @Test
    fun aSubtreeIsTheItemAndEverythingBelow() {
        assertEquals(setOf(3L, 5L, 6L, 7L), tree.subtreeIds(3))
        assertEquals(setOf(4L), tree.subtreeIds(4))
    }

    @Test
    fun anItemMovesIntoAnotherFolderOrUp() {
        assertTrue(tree.canMove(4, 3))
        assertTrue(tree.canMove(6, 3)) // up one level
        assertTrue(tree.canMove(6, 1))
        assertTrue(tree.canMove(2, 4)) // a utility's item can be filed anywhere
        assertTrue(tree.canMove(3, 4)) // a set into another set
    }

    @Test
    fun aMoveThatWouldBreakTheTreeIsRefused() {
        assertFalse(tree.canMove(3, 3)) // into itself
        assertFalse(tree.canMove(3, 6)) // into its own subtree
        assertFalse(tree.canMove(1, 4)) // the root stays the root
        assertFalse(tree.canMove(4, 2)) // a utility's item takes no sub-items
        assertFalse(tree.canMove(4, 7)) // nor does any cost
        assertFalse(tree.canMove(5, 3)) // already there
        assertFalse(tree.canMove(4, 99))
        assertFalse(tree.canMove(99, 4))
    }

    private fun byId(id: Long) = tree.first { it.id == id }

    @Test
    fun onlySetsHoldItemsAndOnlyCostsHoldBills() {
        assertTrue(byId(1).acceptsChildren)
        assertFalse(byId(1).acceptsBills)
        assertFalse(byId(7).acceptsChildren)
        assertTrue(byId(7).acceptsBills)
        assertFalse(byId(2).acceptsChildren)
        assertFalse(byId(2).acceptsBills) // the utility's bills come from its sync
    }

    @Test
    fun theRootAndTheUtilitiesCannotBeDeleted() {
        assertFalse(byId(1).deletable)
        assertFalse(byId(2).deletable)
        assertTrue(byId(3).deletable)
        assertTrue(byId(7).deletable)
    }

    @Test
    fun aCostIsDeletedOnlyWithoutBills() {
        assertTrue(tree.deletionCheck(7, bills = 0).allowed)
        val withBills = tree.deletionCheck(7, bills = 2)
        assertFalse(withBills.allowed)
        assertEquals(ItemKind.Cost, withBills.kind)
        assertEquals(2, withBills.bills)
    }

    @Test
    fun aSetIsDeletedOnlyWithoutCostsBelowIt() {
        assertEquals(
            DeletionCheck(ItemKind.Set, bills = 0, costs = 2, sets = 1),
            tree.deletionCheck(3, bills = 0)
        )
        assertFalse(tree.deletionCheck(3, bills = 0).allowed)
        // Empty sets below go with it.
        assertEquals(
            DeletionCheck(ItemKind.Set, bills = 0, costs = 0, sets = 1),
            tree.deletionCheck(8, bills = 0)
        )
        assertTrue(tree.deletionCheck(8, bills = 0).allowed)
        assertTrue(tree.deletionCheck(4, bills = 0).allowed)
    }

    @Test
    fun aBrokenTreeWithACycleDoesNotHang() {
        val cycle = listOf(set(1, 2), set(2, 1))
        assertTrue(cycle.pathTo(1).size <= 3)
        assertEquals(setOf(1L, 2L), cycle.subtreeIds(1))
    }

    @Test
    fun theDefaultTreeHasTheAppsOwnNames() {
        val items = defaultTree().items
        assertEquals(BuiltInTitle.Root, items.single { it.id == ROOT_ID }.builtIn)
        assertEquals(
            mapOf(
                ProviderKind.Electricity to BuiltInTitle.Electricity,
                ProviderKind.Gas to BuiltInTitle.Gas,
                ProviderKind.Water to BuiltInTitle.Water,
            ),
            items.filter { it.provider != null }.associate { it.provider to it.builtIn },
        )
        assertTrue(items.all { it.title.isEmpty() })
    }

    @Test
    fun namesAreComparedInOneUnicodeForm() {
        assertEquals("water bill", nameKey("  Ｗａｔｅｒ\u3000 Bill "))
        assertEquals(nameKey("カ\u3099"), nameKey("ガ")) // decomposed and composed
        assertEquals(nameKey("ｶﾞｽ"), nameKey("ガス")) // half-width katakana
        assertFalse(nameKey("电费") == nameKey("電費"))
    }

    @Test
    fun aNameClashesOnlyWithinItsLevel() {
        val names = BuiltInNames.None
        val probe = CostItem(99, 1, "ITEM 3", kind = ItemKind.Cost, createdAt = 0)
        assertEquals(3L, tree.nameClash(1, probe, names)?.id)
        assertEquals(null, tree.nameClash(3, probe, names)) // another level
        assertEquals(null, tree.nameClash(1, byId(3), names)) // an item is not its own clash
        assertFalse(tree.hasNameClash(names))
        assertTrue((tree + probe).hasNameClash(names))
    }

    @Test
    fun aBuiltInNameClashesInEveryLanguage() {
        val names = BuiltInNames {
            if (it == BuiltInTitle.Electricity) setOf(
                "Electricity",
                "东京电力"
            ) else emptySet()
        }
        val other =
            CostItem(10, 3, "", kind = ItemKind.Cost, createdAt = 0, builtIn = BuiltInTitle.Electricity)
        val items = tree + other
        assertEquals(
            10L,
            items.nameClash(
                3,
                CostItem(11, 3, "东京电力", kind = ItemKind.Set, createdAt = 0),
                names
            )?.id
        )
        assertEquals(
            10L,
            items.nameClash(
                3,
                CostItem(11, 3, "electricity", kind = ItemKind.Cost, createdAt = 0),
                names
            )?.id
        )
        // Two built-in items of one title clash even without their names.
        assertEquals(10L, items.nameClash(3, other.copy(id = 11), BuiltInNames.None)?.id)
        assertEquals(
            null,
            items.nameClash(3, CostItem(11, 3, "Rent", kind = ItemKind.Cost, createdAt = 0), names)
        )
    }

    @Test
    fun anItemIsPlacedAmongItsSiblings() {
        fun order(items: List<CostItem>, parent: Long) = items.childrenOf(parent).map { it.id }
        assertEquals(listOf(3L, 2L, 4L, 8L), order(tree.placed(3, 1, beforeId = 2), 1))
        assertEquals(listOf(2L, 4L, 8L, 3L), order(tree.placed(3, 1), 1)) // at the end
        assertEquals(listOf(2L, 3L, 4L, 8L), order(tree.placed(3, 1, beforeId = 3), 1))
        // Into another level: at the end, or before a sibling there.
        val moved = tree.placed(4, 3)
        assertEquals(listOf(5L, 7L, 4L), order(moved, 3))
        assertEquals(listOf(2L, 3L, 8L), order(moved, 1))
        assertEquals(listOf(4L, 5L, 7L), order(tree.placed(4, 3, beforeId = 5), 3))
        // Into an empty set; a beforeId of another level counts as none.
        assertEquals(listOf(7L), order(tree.placed(7, 4), 4))
        assertEquals(listOf(5L, 7L, 4L), order(tree.placed(4, 3, beforeId = 9), 3))
        assertEquals(tree.size, tree.placed(3, 1, beforeId = 2).size)
    }
}
