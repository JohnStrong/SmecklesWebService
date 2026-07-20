package models

import java.time.LocalDate

case class Budget(
   email: String,
   periodStart: LocalDate,
   periodEnd: LocalDate,
   amountMinor: Long,
   currencyCode: String
)
