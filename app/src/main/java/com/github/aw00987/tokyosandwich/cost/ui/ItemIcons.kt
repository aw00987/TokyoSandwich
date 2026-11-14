package com.github.aw00987.tokyosandwich.cost.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.github.aw00987.tokyosandwich.R
import com.github.aw00987.tokyosandwich.core.data.ledger.CostItem
import com.github.aw00987.tokyosandwich.core.data.ledger.ItemKind
import com.github.aw00987.tokyosandwich.core.data.ledger.ProviderKind

/**
 * The icons a cost or a set can be given instead of its default one, in the order the picker shows them. [key] is what
 * the ledger saves (`cost_item.icon`), so it must never change; an icon can be dropped (its items fall back to their
 * default), but its key must not be reused for another one.
 */
enum class ItemIcon(
    val key: String,
    @param:DrawableRes val drawable: Int,
    @param:StringRes val label: Int
) {
    Home("home", R.drawable.ic_item_home, R.string.item_icon_home),
    Apartment("apartment", R.drawable.ic_item_apartment, R.string.item_icon_apartment),
    Restaurant("restaurant", R.drawable.ic_item_restaurant, R.string.item_icon_restaurant),
    LunchDining("lunch_dining", R.drawable.ic_item_lunch_dining, R.string.item_icon_lunch_dining),
    LocalCafe("local_cafe", R.drawable.ic_item_local_cafe, R.string.item_icon_local_cafe),
    LocalGroceryStore(
        "local_grocery_store",
        R.drawable.ic_item_local_grocery_store,
        R.string.item_icon_local_grocery_store
    ),
    ShoppingBag("shopping_bag", R.drawable.ic_item_shopping_bag, R.string.item_icon_shopping_bag),
    Train("train", R.drawable.ic_item_train, R.string.item_icon_train),
    DirectionsBus(
        "directions_bus",
        R.drawable.ic_item_directions_bus,
        R.string.item_icon_directions_bus
    ),
    DirectionsCar(
        "directions_car",
        R.drawable.ic_item_directions_car,
        R.string.item_icon_directions_car
    ),
    LocalGasStation(
        "local_gas_station",
        R.drawable.ic_item_local_gas_station,
        R.string.item_icon_local_gas_station
    ),
    LocalParking(
        "local_parking",
        R.drawable.ic_item_local_parking,
        R.string.item_icon_local_parking
    ),
    Flight("flight", R.drawable.ic_item_flight, R.string.item_icon_flight),
    PhoneAndroid(
        "phone_android",
        R.drawable.ic_item_phone_android,
        R.string.item_icon_phone_android
    ),
    Wifi("wifi", R.drawable.ic_item_wifi, R.string.item_icon_wifi),
    Tv("tv", R.drawable.ic_item_tv, R.string.item_icon_tv),
    Subscriptions(
        "subscriptions",
        R.drawable.ic_item_subscriptions,
        R.string.item_icon_subscriptions
    ),
    WaterDrop("water_drop", R.drawable.ic_water_drop_filled, R.string.item_icon_water_drop),
    Bolt("bolt", R.drawable.ic_bolt, R.string.item_icon_bolt),
    Flame("flame", R.drawable.ic_flame, R.string.item_icon_flame),
    LocalHospital(
        "local_hospital",
        R.drawable.ic_item_local_hospital,
        R.string.item_icon_local_hospital
    ),
    Medication("medication", R.drawable.ic_item_medication, R.string.item_icon_medication),
    HealthAndSafety(
        "health_and_safety",
        R.drawable.ic_item_health_and_safety,
        R.string.item_icon_health_and_safety
    ),
    School("school", R.drawable.ic_item_school, R.string.item_icon_school),
    ChildCare("child_care", R.drawable.ic_item_child_care, R.string.item_icon_child_care),
    Pets("pets", R.drawable.ic_item_pets, R.string.item_icon_pets),
    FitnessCenter(
        "fitness_center",
        R.drawable.ic_item_fitness_center,
        R.string.item_icon_fitness_center
    ),
    Checkroom("checkroom", R.drawable.ic_item_checkroom, R.string.item_icon_checkroom),
    LocalLaundryService(
        "local_laundry_service",
        R.drawable.ic_item_local_laundry_service,
        R.string.item_icon_local_laundry_service
    ),
    ContentCut("content_cut", R.drawable.ic_item_content_cut, R.string.item_icon_content_cut),
    Spa("spa", R.drawable.ic_item_spa, R.string.item_icon_spa),
    SportsEsports(
        "sports_esports",
        R.drawable.ic_item_sports_esports,
        R.string.item_icon_sports_esports
    ),
    Movie("movie", R.drawable.ic_item_movie, R.string.item_icon_movie),
    MenuBook("menu_book", R.drawable.ic_item_menu_book, R.string.item_icon_menu_book),
    MusicNote("music_note", R.drawable.ic_item_music_note, R.string.item_icon_music_note),
    CardGiftcard(
        "card_giftcard",
        R.drawable.ic_item_card_giftcard,
        R.string.item_icon_card_giftcard
    ),
    Celebration("celebration", R.drawable.ic_item_celebration, R.string.item_icon_celebration),
    Savings("savings", R.drawable.ic_item_savings, R.string.item_icon_savings),
    CreditCard("credit_card", R.drawable.ic_item_credit_card, R.string.item_icon_credit_card),
    AccountBalance(
        "account_balance",
        R.drawable.ic_item_account_balance,
        R.string.item_icon_account_balance
    ),
    Build("build", R.drawable.ic_item_build, R.string.item_icon_build),
    CleaningServices(
        "cleaning_services",
        R.drawable.ic_item_cleaning_services,
        R.string.item_icon_cleaning_services
    ),
    VolunteerActivism(
        "volunteer_activism",
        R.drawable.ic_item_volunteer_activism,
        R.string.item_icon_volunteer_activism
    ),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The icon saved as [key]; null for no icon or one the app no longer has. */
        fun of(key: String?): ItemIcon? = key?.let(byKey::get)
    }
}

/** The icon an item has when the user picked none: a folder for a set, a bill for a cost; the utilities have their own. */
@DrawableRes
fun defaultItemIcon(kind: ItemKind, provider: ProviderKind?): Int = when (provider) {
    ProviderKind.Water -> R.drawable.ic_water_drop_filled
    ProviderKind.Electricity -> R.drawable.ic_bolt
    ProviderKind.Gas -> R.drawable.ic_flame
    null -> if (kind == ItemKind.Set) R.drawable.ic_folder else R.drawable.ic_cost
}

/** The item's icon: the one the user picked, or else its default. */
@DrawableRes
fun itemIcon(item: CostItem): Int =
    ItemIcon.of(item.icon)?.drawable ?: defaultItemIcon(item.kind, item.provider)
