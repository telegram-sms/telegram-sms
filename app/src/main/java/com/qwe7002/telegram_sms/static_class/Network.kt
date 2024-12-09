package com.qwe7002.telegram_sms.static_class

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import com.qwe7002.telegram_sms.config.proxy
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
    private const val TELEGRAM_API_DOMAIN = "api.telegram.org"
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
        return "https://$TELEGRAM_API_DOMAIN/bot$token/$func"
    }

    @JvmStatic
    fun getOkhttpObj(dohSwitch: Boolean, proxyItem: proxy?): OkHttpClient {
        var doh = dohSwitch
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Add proxy configuration
            if (proxyItem!=null && proxyItem.enable) {
                val policy = ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                val proxyAddr = InetSocketAddress(proxyItem.host, proxyItem.port)
                val proxy = Proxy(Proxy.Type.SOCKS, proxyAddr)
                Authenticator.setDefault(object : Authenticator() {
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
                })
                okhttp.proxy(proxy)
                doh = true
            }
        }
        if (doh) {
            val dohHttpClient: OkHttpClient.Builder =OkHttpClient. Builder().retryOnConnectionFailure(true)
            okhttp.dns(
                DnsOverHttps.Builder().client(dohHttpClient.build())
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
            Log.e("get_by_ip: ", "get_by_ip: ", e.fillInStackTrace())
            throw RuntimeException(e)
        }
    }
}
