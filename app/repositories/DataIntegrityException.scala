package repositories

/**
 * Thrown when data read from the database violates expected invariants.
 *
 * This indicates a data corruption or schema drift issue — e.g. a column
 * contains a value that cannot be mapped to its corresponding enum.
 * Results in a 500 Internal Server Error via the JsonErrorHandler.
 *
 * @param message a description of the integrity violation (logged server-side, not exposed to clients)
 */
case class DataIntegrityException(message: String) extends RuntimeException(message)
