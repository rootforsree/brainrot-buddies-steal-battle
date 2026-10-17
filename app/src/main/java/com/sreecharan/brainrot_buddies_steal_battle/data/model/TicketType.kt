package com.sreecharan.brainrot_buddies_steal_battle.data.model

/**
 * Ticket kinds. Keys match TicketEntity.type ("lucky"|"mega"), frozen by
 * contract §5. There is no Super Ticket in v1.
 */
enum class TicketType(
    val key: String,
) {
    LUCKY("lucky"),
    MEGA("mega"),
}
