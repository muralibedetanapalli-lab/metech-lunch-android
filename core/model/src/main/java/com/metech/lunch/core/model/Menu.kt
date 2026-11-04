package com.metech.lunch.core.model

import kotlinx.serialization.Serializable

/** Today's menu for one kitchen, plus whether ordering is still open (server decides). */
@Serializable
data class DailyMenu(
    val kitchenId: String,
    val date: String,              // ISO local date in the kitchen's timezone
    val orderingOpen: Boolean,     // false after the cutoff; server-enforced
    val cutoffAt: String,          // ISO date-time of the cutoff
    val items: List<MenuItem>,
)

@Serializable
data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String? = null,
    val pricePaise: Long,          // minor units to avoid floating point money
    val available: Boolean,
    val remainingCapacity: Int? = null,
    val dietaryTags: List<String> = emptyList(),   // client-supplied
    val allergens: List<String> = emptyList(),     // client-supplied
)
