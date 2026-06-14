package auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider

import java.net.URI
import javax.inject.{Inject, Singleton}

/**
 * Factory for creating [[com.auth0.jwk.JwkProvider]] instances.
 *
 * Abstracted behind a trait so that tests can substitute a mock provider
 * (returning locally-generated keys) without making network calls to Google.
 */
trait JwkProviderFactory {
  def create(): JwkProvider
}

/**
 * Production implementation that fetches public signing keys from Google's JWKS endpoint.
 *
 * Firebase Auth ID tokens are signed with rotating RSA keys published at this endpoint.
 * The underlying [[UrlJwkProvider]] caches keys and refreshes automatically when
 * a token presents a key ID (`kid`) not found in the cache.
 */
@Singleton
class GoogleJwkProviderFactory @Inject()() extends JwkProviderFactory {
  override def create(): JwkProvider = new UrlJwkProvider(
    URI("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com").toURL
  )
}
