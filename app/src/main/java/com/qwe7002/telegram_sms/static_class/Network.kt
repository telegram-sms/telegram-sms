package com.qwe7002.telegram_sms.static_class

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import com.qwe7002.telegram_sms.MMKV.PROXY_ID
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Network {
    private const val logTag = "${TAG}.Network"
    private const val DNS_OVER_HTTP_ADDRSS = "https://1.1.1.1/dns-query"

    @Suppress("DEPRECATION")
    @JvmStatic
    fun checkNetworkStatus(context: Context): Boolean {
        val manager =
            checkNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        var networkStatus = false
        val networks = manager.allNetworks
        for (network in networks) {
            val networkCapabilities = checkNotNull(manager.getNetworkCapabilities(network))
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                networkStatus = true
            }
        }
        return networkStatus
    }

    @JvmStatic
    fun getUrl(token: String, func: String): String {
        val telegramAPIAddress = MMKV.defaultMMKV()
            .getString("api_address", "api.telegram.org") ?: "api.telegram.org"
        return buildUrl(telegramAPIAddress, token, func)
    }

    /**
     * Normalize a user-supplied Bot API host/base into a request origin.
     *
     * - trims surrounding whitespace and any trailing '/'
     * - keeps an explicit http:// or https:// scheme; defaults to https:// when none
     * - falls back to the official cloud server when blank
     *
     * Pure (no MMKV / Android access) so the URL logic can be unit tested.
     */
    @JvmStatic
    fun normalizeApiBase(apiAddress: String): String {
        val trimmed = apiAddress.trim().trimEnd('/')
        if (trimmed.isEmpty()) return "https://api.telegram.org"
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) trimmed else "https://$trimmed"
    }

    /** Build a full Bot API endpoint URL. Pure / unit testable. */
    @JvmStatic
    fun buildUrl(apiAddress: String, token: String, func: String): String =
        "${normalizeApiBase(apiAddress)}/bot$token/$func"

    /**
     * A bot migration step: release the bot from server [base] by calling Bot API
     * method [method] ("logOut" or "close") there.
     */
    data class ApiMigration(val base: String, val method: String)

    /**
     * Decide how to release the bot when the configured server changes from
     * [oldApiAddress] to [newApiAddress]. A bot may only be active on one Bot API server
     * at a time, so it must be released from the server it is leaving before another can
     * claim it. Per the Bot API docs (and go-telegram-bot-api's LogOutConfig/CloseConfig):
     *
     *  - to or from the official cloud server -> "logOut"
     *  - between two local servers            -> "close"
     *
     * [ApiMigration.base] is the normalized server to act on (the one being left).
     * Returns null when the server is effectively unchanged. Pure / unit testable.
     */
    @JvmStatic
    fun migrationForApiChange(oldApiAddress: String, newApiAddress: String): ApiMigration? {
        val oldBase = normalizeApiBase(oldApiAddress)
        val newBase = normalizeApiBase(newApiAddress)
        if (oldBase == newBase) return null
        val cloud = normalizeApiBase("api.telegram.org")
        val method = if (oldBase != cloud && newBase != cloud) "close" else "logOut"
        return ApiMigration(oldBase, method)
    }

    @JvmStatic
    fun getOkhttpObj(dohSwitch: Boolean): OkHttpClient {
        val proxyMMKV = MMKV.mmkvWithID(PROXY_ID)
        val okhttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && proxyMMKV.getBoolean(
                "enable",
                false
            )
        ) {
            val policy = ThreadPolicy.Builder().permitAll().build()
            StrictMode.setThreadPolicy(policy)
            val host = proxyMMKV.getString("host", "")
            val port = proxyMMKV.getInt("port", 0)
            val username = proxyMMKV.getString("username", "")
            val password = proxyMMKV.getString("password", "")
            val proxyAddr = InetSocketAddress(host, port)
            val proxy = Proxy(Proxy.Type.SOCKS, proxyAddr)
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    return if (requestingHost.equals(
                            host,
                            ignoreCase = true
                        ) && port == requestingPort
                    ) {
                        PasswordAuthentication(username, password?.toCharArray())
                    } else {
                        null
                    }
                }
            })
            okhttp.proxy(proxy)
        }

        if (dohSwitch) {
            val dohHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            okhttp.dns(
                DnsOverHttps.Builder().client(dohHttpClient)
                    .url(DNS_OVER_HTTP_ADDRSS.toHttpUrl())
                    .bootstrapDnsHosts(
                        getByIp("2001:4860:4860::8888"),
                        getByIp("2606:4700:4700::1111"),
                        getByIp("8.8.8.8"),
                        getByIp("1.1.1.1")
                    )
                    .includeIPv6(true)
                    .build()
            )
        }

        return okhttp.build()
    }

    private fun getByIp(host: String): InetAddress {
        try {
            return InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            Log.e(logTag, "get_by_ip: ", e.fillInStackTrace())
            throw RuntimeException(e)
        }
    }
}
