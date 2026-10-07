// TS7 DiPlay port, modified 2026-10-07; GPL3. See SOURCE_MANIFEST.json / PATCHES.md.
package com.shilapi.xcertplay.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/** API27 adaptation of DiPlay's reservation/cancellation and AP interface policy.
 * No forced5GHz, no hidden AP setter, no station disconnect, no guessed channel.
 */
@android.annotation.SuppressLint("MissingPermission")
class LocalOnlyHotspotManager(
    context: Context,
    private val onDiagnostic: (String) -> Unit = {},
    private val onLost: () -> Unit = {},
) : WirelessHotspotManager {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: throw IOException("WIFI_UNAVAILABLE")
    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val lock = Object()
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var starting = false
    private var closed = false
    private var failure = false

    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper())
        require(timeoutMillis in 1..60_000)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val before = interfaces().flatMap { ipv4(it).map { ip -> ip.hostAddress } }.toSet()
        synchronized(lock) { check(!closed && !starting && reservation == null); starting = true }
        val callback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                synchronized(lock) {
                    if (closed || !starting || failure) { value.close(); return }
                    reservation = value
                    lock.notifyAll()
                }
            }
            override fun onFailed(reason: Int) {
                synchronized(lock) { failure = true; lock.notifyAll() }
            }
            override fun onStopped() {
                val notify = synchronized(lock) {
                    failure = true
                    val active = !closed
                    lock.notifyAll()
                    active
                }
                if (notify) onLost()
            }
        }
        try {
            wifi.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
            val active = synchronized(lock) {
                while (reservation == null) { ensureActive(); waitUntil(deadline) }
                reservation!!
            }
            @Suppress("DEPRECATION")
            val config = active.wifiConfiguration ?: throw IOException("HOTSPOT_CONFIG_UNAVAILABLE")
            val ssid = unquote(config.SSID)?.takeIf { it.isNotBlank() && it.length <= 32 }
                ?: throw IOException("HOTSPOT_CONFIG_UNAVAILABLE")
            val passphrase = unquote(config.preSharedKey)?.takeIf { it.length in 8..63 }
                ?: throw IOException("HOTSPOT_CONFIG_UNAVAILABLE")
            if (!config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK))
                throw IOException("HOTSPOT_SECURITY_UNSUPPORTED")
            val configuredBssid = config.BSSID?.takeUnless { it == "02:00:00:00:00:00" }
            val ap = awaitInterface(before.filterNotNull().toSet(), configuredBssid, deadline)
            val address = ipv4(ap).singleOrNull() ?: throw IOException("HOTSPOT_ADDRESS_AMBIGUOUS")
            val configuredChannel = field(config, "apChannel")?.takeIf { it in 1..196 }
            val configuredBand = when (field(config, "apBand")) { 0 -> "2.4 GHz"; 1 -> "5 GHz"; else -> null }
            val settled = LegacyHotspotRadio.Settled()
            var frequency: Int? = null
            val radioDeadline = minOf(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(5))
            while (System.nanoTime() < radioDeadline && frequency == null) {
                synchronized(lock) { ensureActive() }
                frequency = settled.observe(LegacyHotspotRadio.read(ap.name, configuredBand).frequencyMHz,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))
                if (frequency == null) synchronized(lock) { waitUntil(radioDeadline) }
            }
            val measuredChannel = frequency?.let(::wifiFrequencyMhzToChannel)
            val channel = measuredChannel ?: configuredChannel ?: throw IOException("HOTSPOT_CHANNEL_UNKNOWN")
            val band = when {
                frequency in 2412..2484 || channel in 1..14 -> "2.4 GHz"
                frequency in 5160..5895 || channel in 32..177 -> "5 GHz"
                else -> throw IOException("HOTSPOT_CHANNEL_UNKNOWN")
            }
            val acquired = wifi.createMulticastLock("ts7-diplay-ap").apply { setReferenceCounted(false); acquire() }
            synchronized(lock) {
                if (closed || failure) { acquired.release(); throw IOException("HOTSPOT_CANCELLED") }
                multicast = acquired
                starting = false
            }
            return WirelessHotspotInfo(ssid, passphrase, Iap2WirelessSecurity.WPA_WPA2, channel,
                frequency, HotspotInterfaceBssid.read(ap.name), ap.name, address, band,
                WirelessHotspotBackend.LOCAL_ONLY_HOTSPOT)
        } catch (error: Exception) { close(); throw error }
    }

    private fun awaitInterface(before: Set<String>, configured: String?, deadline: Long): NetworkInterface {
        while (true) {
            synchronized(lock) { ensureActive() }
            val upstream = connectivity?.allNetworks.orEmpty().mapNotNull {
                connectivity?.getLinkProperties(it)?.interfaceName
            }.toSet()
            val all = interfaces()
            val candidates = all.map {
                LocalOnlyHotspotInterfacePolicy.Candidate(it.name,
                    ipv4(it).mapNotNull { ip -> ip.hostAddress }.toSet(), HotspotInterfaceBssid.read(it.name))
            }
            val selected = LocalOnlyHotspotInterfacePolicy.select(candidates, before, upstream, configured)
            if (selected != null) return all.single { it.name == selected.name }
            synchronized(lock) { waitUntil(deadline) }
        }
    }
    private fun interfaces(): List<NetworkInterface> = NetworkInterface.getNetworkInterfaces()?.toList()
        .orEmpty().filter { it.isUp && !it.isLoopback }
    private fun ipv4(network: NetworkInterface): List<Inet4Address> =
        network.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
    private fun field(config: WifiConfiguration, name: String): Int? =
        runCatching { WifiConfiguration::class.java.getField(name).getInt(config) }.getOrNull()
    private fun unquote(value: String?): String? = value?.let {
        if (it.length >= 2 && it.first() == '"' && it.last() == '"') it.substring(1, it.length - 1) else it
    }
    private fun ensureActive() { if (closed || failure) throw IOException("HOTSPOT_CANCELLED") }
    private fun waitUntil(deadline: Long) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw IOException("HOTSPOT_TIMEOUT")
        TimeUnit.NANOSECONDS.timedWait(lock, minOf(remaining, TimeUnit.MILLISECONDS.toNanos(200)))
    }
    override fun close() {
        val resources = synchronized(lock) {
            if (closed) return
            closed = true
            starting = false
            val pair = reservation to multicast
            reservation = null
            multicast = null
            lock.notifyAll()
            pair
        }
        resources.first?.close()
        resources.second?.let { if (it.isHeld) it.release() }
    }
}
