package com.narezany.cascade

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.narezany.cascade.data.AppSettings
import com.narezany.cascade.data.CascadeStore
import com.narezany.cascade.data.ImportOutcome
import com.narezany.cascade.data.Profile
import com.narezany.cascade.data.ShareLinkParser
import com.narezany.cascade.data.Subscription
import com.narezany.cascade.data.SubscriptionManager
import com.narezany.cascade.engine.CascadeRuntime
import com.narezany.cascade.engine.CascadeVpnService
import com.narezany.cascade.engine.XrayConfigBuilder
import com.narezany.cascade.engine.XrayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class CascadeViewModel(application: Application) : AndroidViewModel(application) {
    val profiles: StateFlow<List<Profile>> = CascadeStore.profiles
    val subscriptions: StateFlow<List<Subscription>> = CascadeStore.subscriptions
    val settings: StateFlow<AppSettings> = CascadeStore.settings
    val engine = CascadeRuntime.engine
    val traffic = CascadeRuntime.traffic

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pinging = MutableStateFlow<Set<String>>(emptySet())
    val pinging: StateFlow<Set<String>> = _pinging.asStateFlow()

    init {
        viewModelScope.launch { autoUpdateSubscriptions() }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun importText(raw: String): ImportOutcome {
        val outcome = ShareLinkParser.parseMany(raw)
        if (outcome.added.isNotEmpty()) {
            val added = CascadeStore.addProfiles(outcome.added)
            if (CascadeStore.settingsSnapshot().selectedProfileId.isBlank()) {
                CascadeStore.profiles.value.firstOrNull()?.let { CascadeStore.selectProfile(it.id) }
            }
            notify(if (added > 0) "Добавлено профилей: $added" else "Такие профили уже есть")
        } else {
            notify(outcome.errors.firstOrNull() ?: "Не удалось распознать профиль")
        }
        return outcome
    }

    fun importClipboard(context: Context): ImportOutcome? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) {
            notify("Буфер обмена пуст")
            return null
        }
        return importText(text)
    }

    fun saveProfile(profile: Profile) {
        val exists = CascadeStore.profile(profile.id) != null
        if (exists) {
            CascadeStore.updateProfile(profile)
        } else {
            CascadeStore.addProfiles(listOf(profile))
        }
        if (CascadeStore.settingsSnapshot().selectedProfileId.isBlank()) {
            CascadeStore.selectProfile(profile.id)
        }
        reloadIfActive()
        notify(if (exists) "Сервер обновлён" else "Сервер добавлен")
    }

    fun duplicateProfile(profile: Profile) {
        val copy = profile.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${profile.name} (копия)",
            createdAt = System.currentTimeMillis(),
        )
        CascadeStore.addProfiles(listOf(copy))
        notify("Создана копия")
    }

    fun deleteProfile(profile: Profile) {
        CascadeStore.deleteProfile(profile.id)
        if (CascadeRuntime.engine.value.profileId == profile.id) {
            CascadeVpnService.stop(getApplication())
        }
        notify("Сервер удалён")
    }

    fun selectProfile(id: String) {
        CascadeStore.selectProfile(id)
        reloadIfActive()
    }

    fun setFavorite(profile: Profile, favorite: Boolean) {
        CascadeStore.setFavorite(profile.id, favorite)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings, reload: Boolean = false) {
        CascadeStore.updateSettings(transform)
        if (reload) reloadIfActive()
    }

    fun addSubscription(name: String, url: String) {
        val subscription = CascadeStore.addSubscription(name, url)
        viewModelScope.launch {
            _busy.value = true
            notify("Обновляю подписку…")
            val result = SubscriptionManager.update(subscription)
            _busy.value = false
            if (result.profiles.isEmpty()) {
                notify("Подписка: ${result.error}")
            } else {
                notify("Загружено профилей: ${result.profiles.size}")
                if (CascadeStore.settingsSnapshot().selectedProfileId.isBlank()) {
                    CascadeStore.profiles.value.firstOrNull()?.let { CascadeStore.selectProfile(it.id) }
                }
            }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            _busy.value = true
            val result = SubscriptionManager.update(subscription)
            _busy.value = false
            if (result.profiles.isEmpty()) {
                notify("Подписка: ${result.error}")
            } else {
                notify("Обновлено профилей: ${result.profiles.size}")
            }
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        CascadeStore.deleteSubscription(subscription.id)
        notify("Подписка удалена")
    }

    fun updateAllSubscriptions() {
        viewModelScope.launch {
            _busy.value = true
            var updated = 0
            CascadeStore.subscriptions.value.forEach { subscription ->
                val result = SubscriptionManager.update(subscription)
                if (result.profiles.isNotEmpty()) updated += result.profiles.size
            }
            CascadeStore.updateSettings { it.copy(lastSubCheck = System.currentTimeMillis()) }
            _busy.value = false
            notify(if (updated > 0) "Обновлено профилей: $updated" else "Подписки не изменились")
        }
    }

    fun ping(profile: Profile) {
        if (profile.id in _pinging.value) return
        _pinging.value = _pinging.value + profile.id
        viewModelScope.launch {
            val context = getApplication<Application>()
            val settings = CascadeStore.settingsSnapshot()
            val all = CascadeStore.profiles.value
            val targets = if (profile.isGroup) {
                profile.groupIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
            } else {
                listOf(profile)
            }
            var best = Long.MAX_VALUE
            targets.forEach { target ->
                val config = runCatching {
                    XrayConfigBuilder(settings, all).buildPing(target.id)
                }.getOrNull()
                if (config != null) {
                    val result = XrayEngine.measureDelay(context, config, settings.delayUrl)
                    CascadeStore.setPing(target.id, result)
                    if (result >= 0) best = minOf(best, result)
                }
            }
            _pinging.value = _pinging.value - profile.id
            if (best != Long.MAX_VALUE) {
                CascadeStore.setPing(profile.id, best)
                notify("${profile.name}: $best мс")
            } else {
                CascadeStore.setPing(profile.id, -1)
                notify("${profile.name}: нет ответа")
            }
        }
    }

    fun testAggregation(host: String, port: Int) {
        viewModelScope.launch {
            _busy.value = true
            val result = withContext(Dispatchers.IO) { probeSocks(host, port) }
            _busy.value = false
            notify(result)
        }
    }

    fun exportBackup(): String = CascadeStore.exportJson()

    fun importBackup(raw: String, merge: Boolean) {
        val count = CascadeStore.importJson(raw, merge)
        if (count < 0) {
            notify("Это не резервная копия Cascade")
        } else {
            notify("Импортировано профилей: $count")
        }
    }

    fun clearEverything() {
        CascadeVpnService.stop(getApplication())
        CascadeStore.clearAll()
        notify("Все данные удалены")
    }

    fun reloadIfActive() {
        if (CascadeRuntime.engine.value.active) {
            CascadeVpnService.reload(getApplication())
        }
    }

    fun updateEnginePing(ms: Long) {
        CascadeRuntime.ping(ms)
    }

    private suspend fun autoUpdateSubscriptions() {
        val settings = CascadeStore.settingsSnapshot()
        if (!settings.autoUpdateSubscriptions) return
        val subscriptions = CascadeStore.subscriptions.value
        if (subscriptions.isEmpty()) return
        val interval = settings.subscriptionIntervalHours * 3_600_000L
        if (System.currentTimeMillis() - settings.lastSubCheck < interval) return
        subscriptions.forEach { SubscriptionManager.update(it) }
        CascadeStore.updateSettings { it.copy(lastSubCheck = System.currentTimeMillis()) }
    }

    private fun probeSocks(host: String, port: Int): String {
        if (host.isBlank() || port !in 1..65535) return "Укажите корректный адрес и порт"
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                socket.soTimeout = 3000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val reply = ByteArray(2)
                var read = 0
                while (read < 2) {
                    val count = input.read(reply, read, 2 - read)
                    if (count <= 0) break
                    read += count
                }
                if (read < 2 || reply[0].toInt() != 5) return@use "Порт отвечает, но это не SOCKS5"
                if (reply[1].toInt() == 0) "SOCKS5 доступен: $host:$port" else "SOCKS5 требует авторизацию"
            }
        }.getOrElse { "Нет соединения: ${it.message ?: "ошибка"}" }
    }
}
