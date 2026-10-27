package xyz.dafmix.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import xyz.dafmix.app.data.Api
import xyz.dafmix.app.data.DownloadStore
import xyz.dafmix.app.data.PersistentCookieJar
import xyz.dafmix.app.data.Prefs
import java.io.File
import java.util.concurrent.TimeUnit

/** Uygulama genelinde paylaşılan servisler */
object Svc {
    lateinit var app: Application
    lateinit var prefs: Prefs
    lateinit var cookies: PersistentCookieJar
    lateinit var client: OkHttpClient
    lateinit var api: Api
    lateinit var store: DownloadStore

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    fun init(a: Application) {
        app = a
        prefs = Prefs(a)
        cookies = PersistentCookieJar(a)
        client = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        api = Api(client, prefs)
        store = DownloadStore(File(a.filesDir, "downloads_meta.json"))
        watchNetwork(a)
    }

    private fun watchNetwork(a: Application) {
        val cm = a.getSystemService(ConnectivityManager::class.java)
        fun check(): Boolean {
            val n = cm.activeNetwork ?: return false
            val c = cm.getNetworkCapabilities(n) ?: return false
            return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        _online.value = check()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = true }
            override fun onLost(network: Network) { _online.value = check() }
        })
    }
}
