package com.airfreshener.telegram_sms.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import com.airfreshener.telegram_sms.config.ProxyConfigV2
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

    private const val TELEGRAM_API_DOMAIN = "api.telegram.org"
    private const val DNS_OVER_HTTP_ADDRSS = "https://cloudflare-dns.com/dns-query"
    @JvmStatic
    fun check_network_status(context: Context): Boolean {
        val manager =
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
        var network_status = false
        val networks = manager.allNetworks
        if (networks.size != 0) {
            for (network in networks) {
                val network_capabilities = manager.getNetworkCapabilities(network)!!
                if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    network_status = true
                }
            }
        }
        return network_status
    }

    @JvmStatic
    fun get_url(token: String, func: String): String {
        return "https://$TELEGRAM_API_DOMAIN/bot$token/$func"
    }

    @JvmStatic
    fun get_okhttp_obj(doh_switch: Boolean, proxy_item: ProxyConfigV2): OkHttpClient {
        var doh_switch = doh_switch
        val okhttp: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        var proxy: Proxy? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (proxy_item.enable) {
                val policy = ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                val proxyAddr = InetSocketAddress(proxy_item.host, proxy_item.port)
                proxy = Proxy(Proxy.Type.SOCKS, proxyAddr)
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication? {
                        if (requestingHost.equals(proxy_item.host, ignoreCase = true)) {
                            if (proxy_item.port == requestingPort) {
                                return PasswordAuthentication(
                                    proxy_item.username,
                                    proxy_item.password.toCharArray()
                                )
                            }
                        }
                        return null
                    }
                })
                okhttp.proxy(proxy)
                doh_switch = true
            }
        }
        if (doh_switch) {
            val doh_http_client: OkHttpClient.Builder = OkHttpClient.Builder().retryOnConnectionFailure(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (proxy_item.enable && proxy_item.dns_over_socks5) {
                    doh_http_client.proxy(proxy)
                }
            }
            okhttp.dns(
                DnsOverHttps.Builder().client(doh_http_client.build())
                    .url(DNS_OVER_HTTP_ADDRSS.toHttpUrl())
                    .bootstrapDnsHosts(
                        get_by_ip("2606:4700:4700::1001"),
                        get_by_ip("2606:4700:4700::1111"),
                        get_by_ip("1.0.0.1"),
                        get_by_ip("1.1.1.1")
                    )
                    .includeIPv6(true)
                    .build()
            )
        }
        return okhttp.build()
    }

    private fun get_by_ip(host: String): InetAddress {
        return try {
            InetAddress.getByName(host)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            throw RuntimeException(e)
        }
    }
}
