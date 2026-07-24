package models

import java.time.LocalDate

case class Expense(
    email: String,
    dayDate: LocalDate,
    category: String,
    description: String,
    amountMinor: Long,
    sourceType: String,
    sourceId: Long,
    createdAt: Long // epoc millis
)
