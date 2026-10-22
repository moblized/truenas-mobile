package app.truenasmobile.net

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object Certs {
    /** SHA-256 of the certificate, formatted like browsers show it: "AB:CD:...". */
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

    fun same(a: String?, b: String?): Boolean =
        a != null && b != null && a.replace(":", "").equals(b.replace(":", ""), ignoreCase = true)
}

/** What the user needs to see to decide whether to trust a server's certificate. */
data class CertInfo(
    val fingerprint: String,
    val subject: String,
    val issuer: String,
    val notAfterMillis: Long,
    val selfSigned: Boolean,
    /** True when a different certificate was approved before (the server's certificate changed). */
    val changed: Boolean,
    val reason: String,
) {
    companion object {
        fun of(cert: X509Certificate, changed: Boolean, reason: String) = CertInfo(
            fingerprint = Certs.sha256(cert),
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            notAfterMillis = cert.notAfter.time,
            selfSigned = cert.subjectX500Principal == cert.issuerX500Principal,
            changed = changed,
            reason = reason,
        )
    }
}

class UntrustedCertificateException(val info: CertInfo, cause: Throwable? = null) :
    CertificateException("The server's certificate isn't trusted yet", cause)

/**
 * Accepts certificates the phone already trusts (e.g. Let's Encrypt behind a domain), or the one
 * certificate the user approved by fingerprint. TrueNAS ships with a self-signed certificate, so
 * the first connection usually lands in the "approve this fingerprint?" path.
 */
class PinningTrustManager(private val pin: String?) : X509TrustManager {
    private val system: X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        system.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Server sent no certificate")
        if (pin != null && Certs.same(Certs.sha256(leaf), pin)) return
        try {
            system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            throw UntrustedCertificateException(
                CertInfo.of(leaf, changed = pin != null, reason = e.message ?: "Not issued by a trusted authority"),
                e,
            )
        }
    }

    /** Overload Android's X509TrustManagerExtensions looks up by reflection. */
    @Suppress("unused")
    fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, host: String): List<X509Certificate> {
        checkServerTrusted(chain, authType)
        return chain.toList()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}

/**
 * Normal hostname checks, except for the certificate the user approved by fingerprint
 * (self-signed certificates rarely list the LAN IP you connect with).
 */
class PinningHostnameVerifier(private val pin: String?) : HostnameVerifier {
    private val default = HttpsURLConnection.getDefaultHostnameVerifier()

    /** Certificate of the last connection that failed the hostname check, for the trust prompt. */
    @Volatile
    var lastRejected: X509Certificate? = null
        private set

    override fun verify(hostname: String, session: SSLSession): Boolean {
        val leaf = try {
            session.peerCertificates.firstOrNull() as? X509Certificate
        } catch (_: Exception) {
            null
        }
        if (pin != null && leaf != null && Certs.same(Certs.sha256(leaf), pin)) return true
        val ok = default.verify(hostname, session)
        if (!ok) lastRejected = leaf
        return ok
    }
}

object Http {
    class Bundle(val client: OkHttpClient, val hostnameVerifier: PinningHostnameVerifier)

    fun build(pin: String?): Bundle {
        val tm = PinningTrustManager(pin)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        val hv = PinningHostnameVerifier(pin)
        val client = OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, tm)
            .hostnameVerifier(hv)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return Bundle(client, hv)
    }
}
