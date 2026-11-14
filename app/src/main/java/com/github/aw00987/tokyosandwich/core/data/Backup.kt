package com.github.aw00987.tokyosandwich.core.data

import com.github.aw00987.tokyosandwich.core.YearMonthSerializer
import com.github.aw00987.tokyosandwich.core.data.ledger.BillEntity
import com.github.aw00987.tokyosandwich.core.data.ledger.BillExt
import com.github.aw00987.tokyosandwich.core.data.ledger.BillOrigin
import com.github.aw00987.tokyosandwich.core.data.ledger.BuiltInNames
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.LedgerStore
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind
import com.github.aw00987.tokyosandwich.core.data.ledger.acceptsBills
import com.github.aw00987.tokyosandwich.core.data.ledger.hasNameClash
import com.github.aw00987.tokyosandwich.core.data.ledger.orphanBills
import com.github.aw00987.tokyosandwich.core.data.ledger.subtreeIds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth

// An export of the user's data: the settings file ([AppSettings]: the cost tree, its recurring bills and the settings)
// with two keys added, its format version and the user's bills. Not in it: the providers' bills and synced data
// ([SyncState]), which a sync brings back, and accounts, passwords and sessions.

/** The format of [encodeBackup]; a file of another one is refused. */
const val BACKUP_VERSION = 1

private const val VERSION_KEY = "backupVersion"
private const val BILLS_KEY = "bills"

/** One bill of an export: a [BillEntity] without its database id. */
@Serializable
data class BackupBill(
    val itemId: Long,
    @Serializable(with = YearMonthSerializer::class) val month: YearMonth,
    val amount: Long,
    val note: String = "",
    val origin: BillOrigin,
    val ext: BillExt? = null,
    val createdAt: Long,
)

fun BillEntity.toBackup(): BackupBill =
    BackupBill(itemId, month, amount, note, origin, ext?.orNull(), createdAt)

fun BackupBill.toEntity(): BillEntity =
    BillEntity(
        itemId = itemId,
        month = month,
        amount = amount,
        note = note,
        origin = origin,
        ext = ext,
        createdAt = createdAt
    )

/** A decoded export. */
data class Backup(val settings: AppSettings, val bills: List<BackupBill>)

/** Why an export cannot be imported. Nothing is changed in that case. */
enum class BackupProblem {
    /** Not JSON, or not an export of this app. */
    NotABackup,

    /** An export of another format version. */
    OtherVersion,

    /**
     * The cost tree is missing, or broken: not one root set, not one item per utility, an item outside the tree, two
     * items of one level whose names clash.
     */
    BrokenTree,

    /** A bill or recurring bill that does not belong to a cost the user adds bills to, or a utility's bill. */
    BrokenBills,

    /** Two bills of one cost in the same month. */
    DuplicateMonth,
}

class BackupException(val problem: BackupProblem, cause: Throwable? = null) :
    Exception(problem.name, cause)

private val backupJson =
    Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

private val billsSerializer = ListSerializer(BackupBill.serializer())

/** The export of [settings] and the user's [bills] (a utility's are left out). */
fun encodeBackup(settings: AppSettings, bills: List<BillEntity>): String {
    val fields = backupJson.encodeToJsonElement(AppSettings.serializer(), settings).jsonObject
    val userBills = bills.filter { it.origin != BillOrigin.Provider }.map { it.toBackup() }
    val file = JsonObject(
        buildMap {
            put(VERSION_KEY, JsonPrimitive(BACKUP_VERSION))
            putAll(fields)
            put(BILLS_KEY, backupJson.encodeToJsonElement(billsSerializer, userBills))
        },
    )
    return backupJson.encodeToString(JsonObject.serializer(), file)
}

/** Reads an export; throws [BackupException] when it is not one of this version. Unknown fields are ignored. */
fun decodeBackup(text: String): Backup {
    val file = try {
        backupJson.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw BackupException(BackupProblem.NotABackup, e)
    }
    val version = file[VERSION_KEY]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
        ?: throw BackupException(BackupProblem.NotABackup)
    if (version != BACKUP_VERSION) throw BackupException(BackupProblem.OtherVersion)
    return try {
        Backup(
            settings = backupJson.decodeFromJsonElement(
                AppSettings.serializer(),
                JsonObject(file - VERSION_KEY - BILLS_KEY),
            ),
            bills = file[BILLS_KEY]?.let { backupJson.decodeFromJsonElement(billsSerializer, it) }
                .orEmpty(),
        )
    } catch (e: Exception) {
        throw BackupException(BackupProblem.NotABackup, e)
    }
}

/**
 * What stands in the way of importing [backup], or null when it fits the rules of the cost tree and the bills. [names]
 * are the built-in items' names, for the rule that the names of one level do not clash.
 */
fun validateBackup(backup: Backup, names: BuiltInNames = BuiltInNames.None): BackupProblem? {
    val tree = backup.settings.costs ?: return BackupProblem.BrokenTree
    val items = tree.items
    val ids = items.map { it.id } + tree.recurring.map { it.id }
    if (ids.toSet().size != ids.size || ids.any { it >= tree.nextId }) return BackupProblem.BrokenTree
    val root = items.singleOrNull { it.parentId == null }
    if (root == null || root.kind != ItemKind.Set) return BackupProblem.BrokenTree
    if (ProviderKind.entries.any { kind ->
            items.filter { it.provider == kind }.map { it.kind } != listOf(ItemKind.Cost)
        }) {
        return BackupProblem.BrokenTree
    }
    // Every item hangs below the root, and every parent is a set.
    val byId = items.associateBy { it.id }
    if (items.any { item -> item.parentId != null && byId[item.parentId]?.kind != ItemKind.Set }) {
        return BackupProblem.BrokenTree
    }
    if (items.subtreeIds(root.id).size != items.size) return BackupProblem.BrokenTree
    if (items.hasNameClash(names)) return BackupProblem.BrokenTree

    val takers = items.filter { it.acceptsBills }.mapTo(HashSet()) { it.id }
    if (tree.recurring.any { it.itemId !in takers }) return BackupProblem.BrokenBills
    val bills = backup.bills.map { it.toEntity() }
    if (bills.any { it.origin == BillOrigin.Provider } || orphanBills(tree, bills).isNotEmpty()) {
        return BackupProblem.BrokenBills
    }
    if (bills.groupBy { it.itemId to it.month }
            .any { it.value.size > 1 }) return BackupProblem.DuplicateMonth
    return null
}

/** Exports and imports the user's data. */
class BackupService(
    private val ledger: LedgerStore,
    private val sync: SyncStore,
    private val names: BuiltInNames = BuiltInNames.None,
) {
    /** The user's data as an export file's text. */
    suspend fun export(): String {
        val (settings, bills) = ledger.snapshot()
        return encodeBackup(settings, bills)
    }

    /**
     * Replaces the user's data with the export [text]: the cost tree, the recurring bills, the settings and the bills.
     * The utilities' synced data and bills are deleted (their next sync brings them back); accounts and logins stay.
     * Throws [BackupException], with nothing changed, when the file cannot be imported.
     */
    suspend fun import(text: String) {
        val backup = decodeBackup(text)
        validateBackup(backup, names)?.let { throw BackupException(it) }
        // Once started, the writes finish even if the screen goes: the tree and the bills must not end up apart.
        withContext(NonCancellable) {
            sync.clearSynced()
            ledger.replaceAll(backup.settings, backup.bills.map { it.toEntity() })
        }
    }
}
