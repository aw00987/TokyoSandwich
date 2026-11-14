package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.BillExt
import com.github.aw00987.tokyosandwich.core.data.ledger.BillOrigin
import com.github.aw00987.tokyosandwich.core.data.ledger.BuiltInNames
import com.github.aw00987.tokyosandwich.core.data.ledger.BuiltInTitle
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.CostTree
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ROOT_ID
import com.github.aw00987.tokyosandwich.core.data.ledger.RecurringBill
import com.github.aw00987.tokyosandwich.core.data.ledger.defaultTree
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.YearMonth

class BackupTest {
    // The default tree plus a cost (5) with a recurring bill (6).
    private val tree = defaultTree().let { base ->
        base.copy(
            nextId = 7,
            items = base.items + CostItem(
                5,
                ROOT_ID,
                "rent",
                "note\nline",
                ItemKind.Cost,
                "home",
                createdAt = 5
            ),
            recurring = listOf(RecurringBill(6, 5, 80_000, "monthly", YearMonth.of(2026, 1))),
        )
    }
    private val settings = AppSettings(tree, ProvidersSettings(water = ProviderSettings("W-1")))

    private fun bill(
        item: Long,
        month: Int,
        origin: BillOrigin = BillOrigin.Manual,
        ext: BillExt? = null
    ) =
        BillEntity(
            id = 0, itemId = item, month = YearMonth.of(2026, month), amount = 1_000, note = "n",
            origin = origin, ext = ext, createdAt = 42
        )

    private val bills = listOf(
        bill(5, 1, BillOrigin.Recurring, BillExt(recurringId = 6)),
        bill(5, 12),
    )

    private fun problem(backup: Backup): BackupProblem? = validateBackup(backup)

    private fun decodeProblem(text: String): BackupProblem? = try {
        decodeBackup(text)
        null
    } catch (e: BackupException) {
        e.problem
    }

    @Test
    fun exportRoundTripsWithoutTheProvidersBills() {
        val text = encodeBackup(
            settings,
            bills + bill(2, 3, BillOrigin.Provider, BillExt(usage = 12.5, days = 30))
        )
        val backup = decodeBackup(text)
        assertEquals(settings, backup.settings)
        assertEquals(bills.map { it.toBackup() }, backup.bills)
        assertEquals(bills, backup.bills.map { it.toEntity() })
        assertNull(problem(backup))
        assertTrue(text.contains("\"backupVersion\": $BACKUP_VERSION"))
        // "Provider" is also a kind of home card; no bill has that origin.
        assertFalse(text.contains("\"origin\": \"Provider\""))
    }

    @Test
    fun theHomeLayoutIsExportedAndAFileWithoutOneHasTheDefault() {
        val arranged = settings.copy(
            home = HomeLayout(
                listOf(
                    HomeCard(HomeCardKind.Pie, itemId = ROOT_ID),
                    HomeCard(HomeCardKind.Cost, itemId = 5),
                    HomeCard(HomeCardKind.Provider, provider = ProviderKind.Gas),
                ),
            ),
        )
        val backup = decodeBackup(encodeBackup(arranged, bills))
        assertEquals(arranged.home, backup.settings.home)
        assertNull(problem(backup))
        // A card that fits no item does not make the file a broken one; it is dropped after the import.
        val stray =
            arranged.copy(home = HomeLayout(listOf(HomeCard(HomeCardKind.Cost, itemId = 99))))
        assertNull(problem(decodeBackup(encodeBackup(stray, bills))))
        val file = Json.parseToJsonElement(encodeBackup(settings, bills)).jsonObject
        assertTrue("home" in file)
        val withoutHome = JsonObject(file - "home").toString()
        assertEquals(HomeLayout(), decodeBackup(withoutHome).settings.home)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = encodeBackup(settings, bills).replaceFirst("{", """{"later": {"x": 1},""")
        assertEquals(settings, decodeBackup(text).settings)
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertEquals(BackupProblem.NotABackup, decodeProblem("not json"))
        assertEquals(BackupProblem.NotABackup, decodeProblem("[]"))
        assertEquals(BackupProblem.NotABackup, decodeProblem("""{"costs": null}"""))
        assertEquals(
            BackupProblem.NotABackup,
            decodeProblem("""{"backupVersion": 1, "bills": 5}""")
        )
        assertEquals(BackupProblem.OtherVersion, decodeProblem("""{"backupVersion": 2}"""))
    }

    private fun withTree(change: (CostTree) -> CostTree?) =
        Backup(settings.copy(costs = change(tree)), bills.map { it.toBackup() })

    @Test
    fun aBrokenTreeIsRefused() {
        assertEquals(BackupProblem.BrokenTree, problem(withTree { null }))
        // An id the counter has given out already, or two items with one id.
        assertEquals(BackupProblem.BrokenTree, problem(withTree { it.copy(nextId = 6) }))
        assertEquals(
            BackupProblem.BrokenTree,
            problem(withTree { it.copy(items = it.items + it.items.last()) })
        )
        // No root, two roots, a cost as the root.
        assertEquals(
            BackupProblem.BrokenTree,
            problem(withTree { t -> t.copy(items = t.items.drop(1)) })
        )
        assertEquals(BackupProblem.BrokenTree, problem(withTree { t ->
            t.copy(items = t.items.map { if (it.id == 5L) it.copy(parentId = null) else it })
        }))
        // A utility missing; an item below a cost; an item in a loop, cut off from the root.
        assertEquals(
            BackupProblem.BrokenTree,
            problem(withTree { t -> t.copy(items = t.items.filter { it.id != 3L }) })
        )
        assertEquals(BackupProblem.BrokenTree, problem(withTree { t ->
            t.copy(items = t.items.map { if (it.id == 5L) it.copy(parentId = 2) else it })
        }))
        assertEquals(BackupProblem.BrokenTree, problem(withTree { t ->
            t.copy(
                nextId = 9,
                items = t.items + CostItem(7, 8, "a", kind = ItemKind.Set, createdAt = 7) +
                        CostItem(8, 7, "b", kind = ItemKind.Set, createdAt = 8),
            )
        }))
    }

    @Test
    fun twoItemsOfOneLevelWithOneNameAreRefused() {
        val names = BuiltInNames { if (it == BuiltInTitle.Water) setOf("Water") else emptySet() }
        fun withItem(title: String) = withTree { t ->
            t.copy(
                nextId = 9,
                items = t.items + CostItem(8, ROOT_ID, title, kind = ItemKind.Cost, createdAt = 8)
            )
        }
        assertEquals(null, problem(withItem("Gym")))
        assertEquals(BackupProblem.BrokenTree, validateBackup(withItem("ＷＡＴＥＲ"), names))
    }

    @Test
    fun billsThatDoNotFitAreRefused() {
        fun withBills(vararg extra: BillEntity) =
            Backup(settings, (bills + extra).map { it.toBackup() })
        assertEquals(BackupProblem.BrokenBills, problem(withBills(bill(99, 3))))
        assertEquals(BackupProblem.BrokenBills, problem(withBills(bill(2, 3, BillOrigin.Provider))))
        assertEquals(
            BackupProblem.BrokenBills,
            problem(withBills(bill(5, 3, BillOrigin.Recurring)))
        )
        assertEquals(BackupProblem.BrokenBills, problem(withTree { t ->
            t.copy(recurring = t.recurring.map { it.copy(itemId = 2) })
        }))
        assertEquals(BackupProblem.DuplicateMonth, problem(withBills(bill(5, 12))))
    }

    private fun assertRefused(expected: BackupProblem, text: String) {
        try {
            decodeBackup(text)
            fail("decoded")
        } catch (e: BackupException) {
            assertEquals(expected, e.problem)
        }
    }

    @Test
    fun aBillWithoutAnItemIsNotABackup() {
        assertRefused(
            BackupProblem.NotABackup,
            """{"backupVersion": 1, "bills": [{"month": "2026-01"}]}"""
        )
    }
}
