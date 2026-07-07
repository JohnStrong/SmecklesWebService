package auth

case class AccessDeniedException(message: String) extends Throwable(message)
