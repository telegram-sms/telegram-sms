package com.qwe7002.telegram_sms.static_class;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

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
        for (Network network : networks) {
            NetworkCapabilities network_capabilities = manager.getNetworkCapabilities(network);
            assert network_capabilities != null;
            if (network_capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                network_status = true;
            }
        }
        return network_status;
    }

    @NotNull
    public static String getUrl(String token, String func) {
        return "https://" + TELEGRAM_API_DOMAIN + "/bot" + token + "/" + func;
    }

    @NotNull
    public static OkHttpClient getOkhttpObj(boolean dohSwitch, proxy proxyItem) {
        OkHttpClient.Builder okhttp = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (proxyItem.enable) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                StrictMode.setThreadPolicy(policy);
                InetSocketAddress proxyAddr = new InetSocketAddress(proxyItem.host, proxyItem.port);
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestingHost().equalsIgnoreCase(proxyItem.host)) {
                            if (proxyItem.port == getRequestingPort()) {
                                return new PasswordAuthentication(proxyItem.username, proxyItem.password.toCharArray());
                            }
                        }
                        return null;
                    }
                });
                okhttp.proxy(proxy);
                dohSwitch = true;
            }
        }
        if (dohSwitch) {
            OkHttpClient.Builder doh_http_client = new OkHttpClient.Builder().retryOnConnectionFailure(true);
            okhttp.dns(new DnsOverHttps.Builder().client(doh_http_client.build())
                    .url(HttpUrl.get(DNS_OVER_HTTP_ADDRSS))
                    .bootstrapDnsHosts(getByIp("2606:4700:4700::1001"), getByIp("2606:4700:4700::1111"), getByIp("1.0.0.1"), getByIp("1.1.1.1"))
                    .includeIPv6(true)
                    .build());
        }
        return okhttp.build();
    }

    private static InetAddress getByIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            Log.e("get_by_ip: ", "get_by_ip: ", e.fillInStackTrace());
            throw new RuntimeException(e);
        }
    }
}
