package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.Settings
import com.airfreshener.telegram_sms.utils.ServiceUtils.connectivityManager
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

object NetworkUtils {

    private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"
    private const val DNS_OVER_HTTP_ADDRESS = "https://cloudflare-dns.com/dns-query"

    fun checkNetworkStatus(context: Context): Boolean {
        val manager = context.connectivityManager
        var networkStatus = false
        val networks = manager.allNetworks
        if (networks.isNotEmpty()) {
            for (network in networks) {
                val networkCapabilities = manager.getNetworkCapabilities(network) ?: continue // TODO
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    networkStatus = true
                }
            }
        }
        return networkStatus
    }

    fun getUrl(token: String, func: String): String {
        return "$TELEGRAM_API_URL$token/$func"
    }

    fun getOkhttpObj(settings: Settings): OkHttpClient {
        var isDnsOverHttps = settings.isDnsOverHttp
        val proxyItem = PaperUtils.getProxyConfig()
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        var proxy: Proxy? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (proxyItem.enable) {
                val policy = ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                val proxyAddr = InetSocketAddress(proxyItem.host, proxyItem.port)
                proxy = Proxy(Proxy.Type.SOCKS, proxyAddr)
                Authenticator.setDefault(ProxyAuthenticator(proxyItem))
                okhttp.proxy(proxy)
                isDnsOverHttps = true
            }
        }
        if (isDnsOverHttps) {
            val dohHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (proxyItem.enable && proxyItem.dns_over_socks5) {
                    dohHttpClient.proxy(proxy)
                }
            }

            val doh = DnsOverHttps.Builder().client(dohHttpClient.build())
                .url(DNS_OVER_HTTP_ADDRESS.toHttpUrl())
                .bootstrapDnsHosts(
                    getByIp("2606:4700:4700::1001"),
                    getByIp("2606:4700:4700::1111"),
                    getByIp("1.0.0.1"),
                    getByIp("1.1.1.1")
                )
                .includeIPv6(true)
                .build()
            okhttp.dns(doh)
        }
        return okhttp.build()
    }

    private fun getByIp(host: String): InetAddress {
        return try {
            InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }

    private class ProxyAuthenticator(private val proxyItem: ProxyConfigV2) : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestingHost.equals(proxyItem.host, ignoreCase = true)) {
                if (proxyItem.port == requestingPort) {
                    return PasswordAuthentication(
                        proxyItem.username,
                        proxyItem.password.toCharArray()
                    )
                }
            }
            return null
        }
    }
}
