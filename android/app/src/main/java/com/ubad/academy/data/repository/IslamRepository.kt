package com.ubad.academy.data.repository

import com.ubad.academy.core.Web
import com.ubad.academy.data.backup.BackupCodec
import com.ubad.academy.data.backup.WebNormalizer
import com.ubad.academy.data.local.db.IslamEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.domain.model.Dhikr
import com.ubad.academy.domain.model.IslamState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Islam tracker document (`state.islam`) with the web's daily auto-reset
 * (`islamDay()`) and 60-day prayer history (`recordPrayers()`).
 */
@Singleton
class IslamRepository @Inject constructor(private val db: UbadDatabase) {
    private val json = BackupCodec.ExportJson
    private val mutex = Mutex()

    private fun decode(s: String?): IslamState =
        s?.let { runCatching { json.decodeFromString(IslamState.serializer(), it) }.getOrNull() } ?: WebNormalizer.defaultIslam()

    /** Always the view for *today* (a new day shows cleared prayers/sunnah, like islamDay()). */
    val state: Flow<IslamState> = db.islam().observe().map { rolled(decode(it)) }

    private fun rolled(s: IslamState): IslamState {
        val td = Web.today()
        if (s.day == td) return s
        return s.copy(
            day = td,
            prayers = IslamState.PRAYER_KEYS.associateWith { 0 },
            rawatib = IslamState.RAWATIB_KEYS.associateWith { 0 },
        )
    }

    /** Atomic read-modify-write on today's state. */
    suspend fun update(block: (IslamState) -> IslamState): IslamState = mutex.withLock {
        val next = block(rolled(decode(db.islam().get())))
        db.islam().put(IslamEntity(json = json.encodeToString(IslamState.serializer(), next)))
        next
    }

    // ── prayers / sunnah ──
    suspend fun togglePrayer(key: String) = update { s ->
        val p = s.prayers.toMutableMap().apply { this[key] = if ((this[key] ?: 0) == 1) 0 else 1 }
        recordPrayers(s.copy(prayers = p))
    }

    private fun recordPrayers(s: IslamState): IslamState {
        val done = IslamState.PRAYER_KEYS.count { (s.prayers[it] ?: 0) > 0 }
        val hist = s.hist.toMutableMap().apply { this[Web.today()] = done }
        val keys = hist.keys.sorted()
        keys.take((keys.size - 60).coerceAtLeast(0)).forEach { hist.remove(it) }
        return s.copy(hist = hist.toSortedMap().let { LinkedHashMap(it) })
    }

    suspend fun toggleRawatib(key: String) = update { s ->
        s.copy(rawatib = s.rawatib.toMutableMap().apply { this[key] = if ((this[key] ?: 0) != 0) 0 else 1 })
    }

    // ── fasting ──
    suspend fun toggleFastToday() = update { s ->
        val td = Web.today()
        s.copy(fasts = if (td in s.fasts) s.fasts - td else (s.fasts + td).takeLast(1000))
    }

    // ── tasbih ──
    /** Returns true when the target was reached (web resets count to 0 and celebrates). */
    suspend fun tasbihTap(): Boolean {
        var reached = false
        update { s ->
            val t = s.tasbih
            var count = t.count + 1
            val total = (t.total + 1).coerceAtMost(1_000_000_000)
            if (count >= t.target) { reached = true; count = 0 }
            s.copy(tasbih = t.copy(count = count, total = total))
        }
        return reached
    }

    suspend fun tasbihSelect(id: String) = update { s -> s.copy(tasbih = s.tasbih.copy(mode = id, count = 0)) }
    suspend fun tasbihTarget(target: Int) = update { s -> s.copy(tasbih = s.tasbih.copy(target = target, count = 0)) }
    suspend fun tasbihReset() = update { s -> s.copy(tasbih = s.tasbih.copy(count = 0)) }

    /** openTasModal save: edit text of an existing item, or add a custom dhikr. */
    suspend fun tasbihSave(existingId: String?, text: String) = update { s ->
        val clean = text.trim().take(120)
        if (clean.isEmpty()) return@update s
        val items = s.tasbih.adhkar
        if (existingId != null) {
            s.copy(tasbih = s.tasbih.copy(adhkar = items.map { if (it.id == existingId) it.copy(text = clean) else it }))
        } else {
            // The web normalizer keeps at most 30 custom adhkar; refuse more instead of losing them on reload.
            if (items.count { !it.builtin } >= 30) return@update s
            val d = Dhikr(id = "custom_" + Web.uid(), text = clean, builtin = false)
            s.copy(tasbih = s.tasbih.copy(adhkar = items + d, mode = d.id))
        }
    }

    suspend fun tasbihDelete(id: String) = update { s ->
        val items = s.tasbih.adhkar.filterNot { it.id == id && !it.builtin }
        val mode = if (s.tasbih.mode == id) items.firstOrNull()?.id ?: "sub" else s.tasbih.mode
        s.copy(tasbih = s.tasbih.copy(adhkar = items, mode = mode))
    }
}
