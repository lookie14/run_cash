package io.github.lookie14.runcash.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

enum class Role { Grandma, Grandson }

/**
 * 이 폰의 역할과 연결된 가족 ID.
 * 할머니 폰은 페어링이 끝나면 familyId(= 손주의 uid)를 저장한다. 손주 폰은 자기 uid가 곧 familyId다.
 */
data class Session(val role: Role? = null, val familyId: String? = null)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("runcash_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session> = _session

    private fun load(): Session {
        val role = prefs.getString(KEY_ROLE, null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        return Session(role, prefs.getString(KEY_FAMILY, null))
    }

    @Synchronized
    private fun save(s: Session) {
        val editor = prefs.edit().putString(KEY_ROLE, s.role?.name).putString(KEY_FAMILY, s.familyId)
        // 연결된 가족이 바뀌면 업로드 기록도 처음부터 다시 맞춘다.
        if (s.familyId != _session.value.familyId) editor.remove(KEY_LAST_SYNCED)
        editor.apply()
        _session.value = s
    }

    fun setRole(role: Role) = save(Session(role, null))

    fun setFamily(familyId: String) = save(_session.value.copy(familyId = familyId))

    /** 연결이 끊겼을 때. 역할은 그대로 두고 숫자 입력부터 다시 한다. */
    fun clearFamily() = save(_session.value.copy(familyId = null))

    /** 역할 선택부터 다시 시작한다. */
    fun reset() = save(Session())

    /** 지난 날짜 걸음 수를 이 날짜까지 서버에 다 올렸다. */
    var lastSyncedDate: LocalDate?
        get() = prefs.getString(KEY_LAST_SYNCED, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNCED, value?.toString()).apply()
        }

    private companion object {
        const val KEY_ROLE = "role"
        const val KEY_FAMILY = "familyId"
        const val KEY_LAST_SYNCED = "lastSyncedDate"
    }
}
