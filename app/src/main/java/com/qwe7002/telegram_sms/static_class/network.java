package com.qwe7002.telegram_sms.static_class;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.StrictMode;

import com.qwe7002.telegram_sms.config.proxy;

import org.jetbrains.annotations.NotNull;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;

public class
network {
    private static final String TELEGRAM_API_DOMAIN = "api.telegram.org";
    private static final String DNS_OVER_HTTP_ADDRSS = "https://1.1.1.1/dns-query";

    public static boolean checkNetworkStatus(@NotNull Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert manager != null;
        boolean network_status = false;
        Network[] networks = manager.getAllNetworks();
        if (networks.length != 0) {
            for (Network network : networks) {
                NetworkCapabilities network_capabilities = manager.getNetworkCapabilities(network);
                assert network_capabilities != null;
                if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    network_status = true;
                }
            }
        }
        return network_status;
    }

    @NotNull
    public static String getUrl(String token, String func) {
        return "https://" + TELEGRAM_API_DOMAIN + "/bot" + token + "/" + func;
    }

    @NotNull
    public static OkHttpClient getOkhttpObj(boolean doh_switch, proxy proxy_item) {
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        Proxy proxy = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (proxy_item.enable) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
                InetSocketAddress proxyAddr = new InetSocketAddress(proxy_item.host, proxy_item.port);
                proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestingHost().equalsIgnoreCase(proxy_item.host)) {
                            if (proxy_item.port == getRequestingPort()) {
                                return new PasswordAuthentication(proxy_item.username, proxy_item.password.toCharArray());
                            }
                        }
                        return null;
                    }
                });
                okhttp.proxy(proxy);
                doh_switch = true;
            }
        }
        if (doh_switch) {
            OkHttpClient.Builder doh_http_client = new OkHttpClient.Builder().retryOnConnectionFailure(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (proxy_item.enable && proxy_item.dns_over_socks5) {
                    doh_http_client.proxy(proxy);
                }
            }
            okhttp.dns(new DnsOverHttps.Builder().client(doh_http_client.build())
                    .url(HttpUrl.get(DNS_OVER_HTTP_ADDRSS))
                    .bootstrapDnsHosts(get_by_ip("2606:4700:4700::1001"), get_by_ip("2606:4700:4700::1111"), get_by_ip("1.0.0.1"), get_by_ip("1.1.1.1"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress get_by_ip(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
