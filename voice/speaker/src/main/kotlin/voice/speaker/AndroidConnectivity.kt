package voice.speaker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import voice.api.Connectivity

// Current network as the router and the voice downloader see it: unmetered (usually Wi-Fi), metered, or none.
public class AndroidConnectivity(context: Context) : () -> Connectivity {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun invoke(): Connectivity {
        val network = manager?.activeNetwork ?: return Connectivity.Offline
        val caps = manager.getNetworkCapabilities(network) ?: return Connectivity.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Connectivity.Offline
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) Connectivity.Unmetered else Connectivity.Metered
    }
}
