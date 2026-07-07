package auth

case class EmailAllowList(emails: Set[String]):
  def contains(email: String): Boolean =
    emails.contains(email.trim.toLowerCase)

object EmailAllowList:
  def apply(raw: String): EmailAllowList = EmailAllowList(
    emails = raw.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
  )
