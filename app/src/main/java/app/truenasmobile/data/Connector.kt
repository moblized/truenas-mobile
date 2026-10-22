package app.truenasmobile.data

import app.truenasmobile.api.LoginResult
import app.truenasmobile.api.TrueNasApi
import app.truenasmobile.net.CertInfo
import app.truenasmobile.net.DisconnectedException
import app.truenasmobile.net.EndpointNotFoundException
import app.truenasmobile.net.Http
import app.truenasmobile.net.RpcClient
import app.truenasmobile.net.RpcException
import app.truenasmobile.net.UntrustedCertificateException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/** Opens a connection to a saved server and signs in. Shared by the app, the setup screen and the alert checker. */
object Connector {

    sealed interface Outcome {
        data class Ok(val api: TrueNasApi) : Outcome
        /** Password accepted, waiting for the two-factor code. The connection stays open. */
        data class NeedsOtp(val api: TrueNasApi) : Outcome
        data class Untrusted(val info: CertInfo) : Outcome
        data class Failed(val message: String, val authProblem: Boolean = false) : Outcome
    }

    const val INSECURE_KEY_MESSAGE =
        "TrueNAS revokes API keys that are sent without HTTPS. Use an https:// address, or sign in with a username and password."

    suspend fun open(profile: ServerProfile, secret: String, token: String? = null): Outcome {
        val endpoint = profile.endpoint ?: return Outcome.Failed("That server address doesn't look right.")
        if (!endpoint.secure && profile.auth == AuthMethod.API_KEY) {
            return Outcome.Failed(INSECURE_KEY_MESSAGE, authProblem = true)
        }
        val http = Http.build(profile.certPin)
        val client = RpcClient(endpoint.wsUrl, http.client, http.hostnameVerifier)
        try {
            client.connect()
            val api = TrueNasApi(client)
            if (token != null && api.loginToken(token)) return Outcome.Ok(api)
            val result = when (profile.auth) {
                AuthMethod.API_KEY -> api.loginApiKey(profile.username, secret)
                AuthMethod.PASSWORD -> api.loginPassword(profile.username, secret)
            }
            return when (result) {
                LoginResult.Success -> Outcome.Ok(api)
                LoginResult.OtpRequired -> Outcome.NeedsOtp(api)
                is LoginResult.Failed -> {
                    client.close()
                    Outcome.Failed(result.message, authProblem = true)
                }
            }
        } catch (e: CancellationException) {
            client.close()
            throw e
        } catch (e: UntrustedCertificateException) {
            client.close()
            return Outcome.Untrusted(e.info)
        } catch (e: Exception) {
            client.close()
            return Outcome.Failed(friendly(e, endpoint.webUrl))
        }
    }

    fun friendly(e: Throwable, where: String? = null): String {
        val at = where?.let { " at $it" } ?: ""
        var c: Throwable? = e
        while (c != null) {
            when (c) {
                is RpcException -> return c.message ?: "The server reported an error"
                is EndpointNotFoundException -> return c.message!!
                is UnknownHostException ->
                    return "Can't find the server$at. Check the address, and that you're on your home network or VPN."
                is ConnectException, is NoRouteToHostException ->
                    return "The server$at isn't answering. Is it on, and is the port right?"
                is SocketTimeoutException -> return "The server$at took too long to answer."
                is SSLException -> return "Secure connection failed: ${c.message ?: "TLS error"}. If the server only serves http, enter the address with http://"
                is DisconnectedException -> return c.message ?: "Connection lost"
            }
            c = c.cause
        }
        return (e as? IOException)?.message ?: e.message ?: e.javaClass.simpleName
    }
}
