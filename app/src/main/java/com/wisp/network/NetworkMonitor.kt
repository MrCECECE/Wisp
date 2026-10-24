package com.wisp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

enum class NetworkType { WIFI, MOBILE, NONE }

object NetworkMonitor {
    fun getType(context: Context): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetworkType.NONE
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkType.NONE
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return NetworkType.WIFI
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return NetworkType.MOBILE
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            for (net in cm.allNetworks) {
                if (net == active) continue
                val c = cm.getNetworkCapabilities(net) ?: continue
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return NetworkType.WIFI
            }
            for (net in cm.allNetworks) {
                if (net == active) continue
                val c = cm.getNetworkCapabilities(net) ?: continue
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return NetworkType.MOBILE
            }
        }
        return NetworkType.NONE
    }
}