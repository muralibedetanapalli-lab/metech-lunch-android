package com.metech.lunch.core.model

import kotlinx.serialization.Serializable

/** A serviceable office configured by the owner. */
@Serializable
data class Office(
    val id: String,
    val kitchenId: String,
    val name: String,
    val address: String,
    val dropPoints: List<DropPoint> = emptyList(),
)

@Serializable
data class DropPoint(
    val id: String,
    val officeId: String,
    val name: String,
    val instructions: String? = null,
)
