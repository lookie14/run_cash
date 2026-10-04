package io.github.lookie14.runcash.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.lookie14.runcash.domain.MonthSummary
import io.github.lookie14.runcash.domain.PointRules
import io.github.lookie14.runcash.domain.RuleSchedule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import kotlin.random.Random

/** 서버에 올라온 하루 기록. updatedAtMillis는 사용자 폰이 마지막으로 올린 시각이다. */
data class DailyRecord(val date: LocalDate, val steps: Long, val updatedAtMillis: Long?)

/** 월말 정산 기록. paidAtMillis는 서버 시각이 아직 안 정해졌으면 null이다. */
data class Settlement(val month: YearMonth, val amount: Int, val paidAtMillis: Long?, val paidBy: String?)

/** 관리자 한 명. */
data class AdminMember(val uid: String, val name: String, val joinedAtMillis: Long?)

/** 연결 코드 종류: 사용자 폰 연결용 / 관리자 초대용. */
enum class PairKind(val value: String) { User("user"), Admin("admin") }

data class PairCode(val code: String, val kind: PairKind, val expiresAtMillis: Long)

data class FamilyState(
    val exists: Boolean,
    /** 사용자 폰이 연결되어 있는지. */
    val paired: Boolean,
    val admins: List<AdminMember>,
)

sealed interface ClaimResult {
    data class Success(val familyId: String) : ClaimResult
    data object InvalidCode : ClaimResult
    data object Expired : ClaimResult
    /** 사용자 연결 코드인데 이미 다른 사용자 폰이 연결돼 있다. */
    data object AlreadyPaired : ClaimResult
}

/**
 * Firestore 구조
 *  families/{가족 ID}                 : adminUids[], admins{uid: {name, joinedAt}}, grandmaUid(사용자, 연결 전 null),
 *                                       rules{"적용 시작일": {goal, maxWon}}, createdAt
 *  families/{가족 ID}/daily/{날짜}    : steps, updatedAt   (사용자 폰이 쓰고, 모두 읽는다)
 *  families/{가족 ID}/settlements/{연-월} : amount, steps, goalDays, paidBy, paidAt   (관리자가 쓴다)
 *  pairCodes/{6자리 코드}              : familyId, kind("user"|"admin"), expiresAt
 *
 * 가족 ID는 무작위로 만든다. 관리자가 여러 명이고 누구든 빠질 수 있으므로 특정 계정에 묶지 않는다.
 * 코드에서 사용자 = Role.Grandma, 관리자 = Role.Grandson 이다.
 */
class FamilyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    /** 로그인된 uid. 백그라운드 작업에서는 새 익명 계정을 만들면 안 되므로 이걸 쓴다. */
    fun currentUidOrNull(): String? = auth.currentUser?.uid

    suspend fun uid(): String =
        auth.currentUser?.uid
            ?: auth.signInAnonymously().awaitResult().user?.uid
            ?: error("로그인에 실패했어요")

    private fun family(familyId: String) = db.collection("families").document(familyId)

    private fun DocumentSnapshot.adminUids(): List<String> =
        (get("adminUids") as? List<*>).orEmpty().filterIsInstance<String>()

    private fun DocumentSnapshot.adminMembers(): List<AdminMember> {
        val raw = get("admins") as? Map<*, *> ?: return emptyList()
        val uids = adminUids()
        return raw.mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            if (uid !in uids) return@mapNotNull null
            val fields = value as? Map<*, *>
            AdminMember(
                uid = uid,
                name = (fields?.get("name") as? String).orEmpty().ifBlank { "이름 없음" },
                joinedAtMillis = (fields?.get("joinedAt") as? Timestamp)?.toDate()?.time,
            )
        }.sortedBy { it.joinedAtMillis ?: Long.MAX_VALUE }
    }

    // ---- 관리자 ----

    /** 새 가족을 만들고 나를 첫 관리자로 등록한다. 가족 ID를 돌려준다. */
    suspend fun createFamily(adminName: String): String {
        val uid = uid()
        val ref = db.collection("families").document()
        ref.set(
            mapOf(
                "adminUids" to listOf(uid),
                "admins" to mapOf(uid to mapOf("name" to adminName, "joinedAt" to FieldValue.serverTimestamp())),
                "grandmaUid" to null,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitResult()
        return ref.id
    }

    /** 6자리 코드를 만든다. 이미 있는 코드와 겹치면 규칙이 막으므로 다른 코드로 다시 시도한다. */
    suspend fun createPairCode(familyId: String, kind: PairKind): PairCode {
        uid()
        repeat(MAX_CODE_TRIES) {
            val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            val expiresAt = System.currentTimeMillis() + CODE_VALID_MILLIS
            try {
                db.collection("pairCodes").document(code)
                    .set(
                        mapOf(
                            "familyId" to familyId,
                            "kind" to kind.value,
                            "expiresAt" to Timestamp(Date(expiresAt)),
                        ),
                    )
                    .awaitResult()
                return PairCode(code, kind, expiresAt)
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            }
        }
        error("코드를 만들지 못했어요. 잠시 뒤에 다시 해 주세요")
    }

    /** 관리자 초대 코드로 기존 가족에 관리자로 참여한다. */
    suspend fun joinAsAdmin(code: String, adminName: String): ClaimResult {
        val uid = uid()
        val (familyId, result) = readCode(code, PairKind.Admin)
        if (familyId == null) return result

        return try {
            family(familyId).update(
                mapOf(
                    "adminUids" to FieldValue.arrayUnion(uid),
                    "admins.$uid" to mapOf("name" to adminName, "joinedAt" to FieldValue.serverTimestamp()),
                    "lastJoinCode" to code,
                ),
            ).awaitResult()
            ClaimResult.Success(familyId)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.NOT_FOUND -> ClaimResult.InvalidCode
                else -> throw e
            }
        }
    }

    /** 관리자를 뺀다. 마지막 한 명은 뺄 수 없다(규칙에서도 막는다). */
    suspend fun removeAdmin(familyId: String, uid: String) {
        family(familyId).update(
            mapOf(
                "adminUids" to FieldValue.arrayRemove(uid),
                "admins.$uid" to FieldValue.delete(),
            ),
        ).awaitResult()
    }

    fun observeFamily(familyId: String): Flow<FamilyState> = callbackFlow {
        val registration = family(familyId).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            trySend(
                FamilyState(
                    exists = snap.exists(),
                    paired = snap.getString("grandmaUid") != null,
                    admins = snap.adminMembers(),
                ),
            )
        }
        awaitClose { registration.remove() }
    }

    /**
     * 이 폰이 아직 이 가족의 구성원인지 지켜본다. true = 구성원, false = 빠짐.
     * 관리자 폰은 관리자 목록에, 사용자 폰은 grandmaUid에 내가 있는지 본다.
     * 빠지면 규칙상 읽기 권한이 사라지므로 PERMISSION_DENIED도 빠진 것으로 본다.
     * 오프라인일 때 기기에 저장된 값이 없어서 "문서 없음"으로 보이는 경우는 빠진 것으로 보지 않는다.
     */
    fun observeMembership(familyId: String, asAdmin: Boolean): Flow<Boolean> = callbackFlow {
        val myUid = uid()
        val registration = family(familyId).addSnapshotListener { snap, error ->
            if (error != null) {
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    trySend(false)
                    close()
                } else {
                    close(error)
                }
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            val member = snap.exists() && if (asAdmin) {
                myUid in snap.adminUids()
            } else {
                snap.getString("grandmaUid") == myUid
            }
            trySend(member)
        }
        awaitClose { registration.remove() }
    }

    // ---- 날짜별 걸음 기록 ----

    private fun dailyBetween(familyId: String, start: LocalDate, endInclusive: LocalDate) =
        family(familyId).collection("daily")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), start.toString())
            .whereLessThanOrEqualTo(FieldPath.documentId(), endInclusive.toString())

    private fun dailyOfMonth(familyId: String, month: YearMonth) =
        dailyBetween(familyId, month.atDay(1), month.atEndOfMonth())

    /** 기간 안의 날짜별 기록(걸음 수 + 올라온 시각). 관리자 모니터링용. */
    fun observeDays(familyId: String, start: LocalDate, endInclusive: LocalDate): Flow<Map<LocalDate, DailyRecord>> =
        callbackFlow {
            val registration = dailyBetween(familyId, start, endInclusive).addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val records = snap?.documents.orEmpty().mapNotNull { doc ->
                    val date = runCatching { LocalDate.parse(doc.id) }.getOrNull() ?: return@mapNotNull null
                    DailyRecord(
                        date = date,
                        steps = doc.getLong("steps") ?: 0L,
                        updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time,
                    )
                }
                trySend(records.associateBy { it.date })
            }
            awaitClose { registration.remove() }
        }

    private fun List<DocumentSnapshot>.toStepMap(): Map<LocalDate, Long> =
        mapNotNull { doc ->
            val date = runCatching { LocalDate.parse(doc.id) }.getOrNull() ?: return@mapNotNull null
            date to (doc.getLong("steps") ?: 0L)
        }.toMap()

    /** 그 달의 날짜별 걸음 수. 기록이 없는 날은 맵에 없다. */
    fun observeMonth(familyId: String, month: YearMonth): Flow<Map<LocalDate, Long>> = callbackFlow {
        val registration = dailyOfMonth(familyId, month).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().toStepMap())
        }
        awaitClose { registration.remove() }
    }

    /** 그 달의 날짜별 걸음 수를 한 번 읽는다. (알림 작업용) */
    suspend fun monthSteps(familyId: String, month: YearMonth): Map<LocalDate, Long> =
        dailyOfMonth(familyId, month).get().awaitResult().documents.toStepMap()

    // ---- 목표 규칙 ----

    private fun DocumentSnapshot.toRuleSchedule(): RuleSchedule {
        val raw = get("rules") as? Map<*, *> ?: return RuleSchedule()
        val changes = raw.mapNotNull { (key, value) ->
            val date = (key as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val fields = value as? Map<*, *> ?: return@mapNotNull null
            val goal = (fields["goal"] as? Number)?.toInt() ?: return@mapNotNull null
            val maxWon = (fields["maxWon"] as? Number)?.toInt() ?: return@mapNotNull null
            date to PointRules(dailyGoal = goal, dailyMaxPoints = maxWon)
        }.toMap()
        return RuleSchedule(changes)
    }

    fun observeRules(familyId: String): Flow<RuleSchedule> = callbackFlow {
        val registration = family(familyId).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) trySend(snap.toRuleSchedule())
        }
        awaitClose { registration.remove() }
    }

    suspend fun getRules(familyId: String): RuleSchedule =
        family(familyId).get().awaitResult().toRuleSchedule()

    /** 관리자: from 날짜부터 적용할 규칙을 저장한다. 같은 날짜로 다시 저장하면 덮어쓴다. */
    suspend fun setRulesFrom(familyId: String, from: LocalDate, goal: Int, maxWon: Int) {
        family(familyId)
            .update(FieldPath.of("rules", from.toString()), mapOf("goal" to goal, "maxWon" to maxWon))
            .awaitResult()
    }

    // ---- 월말 정산 ----

    /** 문서 ID는 "2026-10" 형식. */
    private fun settlement(familyId: String, month: YearMonth) =
        family(familyId).collection("settlements").document(month.toString())

    private fun DocumentSnapshot.toSettlement(month: YearMonth): Settlement? =
        if (!exists()) {
            null
        } else {
            Settlement(
                month = month,
                amount = getLong("amount")?.toInt() ?: 0,
                paidAtMillis = getTimestamp("paidAt")?.toDate()?.time,
                paidBy = getString("paidBy"),
            )
        }

    fun observeSettlement(familyId: String, month: YearMonth): Flow<Settlement?> = callbackFlow {
        val registration = settlement(familyId, month).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snap?.toSettlement(month))
        }
        awaitClose { registration.remove() }
    }

    suspend fun getSettlement(familyId: String, month: YearMonth): Settlement? =
        settlement(familyId, month).get().awaitResult().toSettlement(month)

    /** 관리자: "보냈어요". 보낸 금액을 그 시점 값으로 고정해 둔다. */
    suspend fun markPaid(familyId: String, month: YearMonth, summary: MonthSummary, paidBy: String) {
        settlement(familyId, month).set(
            mapOf(
                "amount" to summary.won,
                "steps" to summary.totalSteps,
                "goalDays" to summary.goalDays,
                "paidBy" to paidBy,
                "paidAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitResult()
    }

    // ---- 사용자 폰 ----

    /** 코드를 읽어 가족 ID를 돌려준다. 쓸 수 없는 코드면 (null, 이유). */
    private suspend fun readCode(code: String, kind: PairKind): Pair<String?, ClaimResult> {
        val snap = db.collection("pairCodes").document(code).get().awaitResult()
        if (!snap.exists() || snap.getString("kind") != kind.value) return null to ClaimResult.InvalidCode
        val expiresAt = snap.getTimestamp("expiresAt")?.toDate()?.time ?: return null to ClaimResult.InvalidCode
        if (expiresAt < System.currentTimeMillis()) return null to ClaimResult.Expired
        val familyId = snap.getString("familyId") ?: return null to ClaimResult.InvalidCode
        return familyId to ClaimResult.Success(familyId)
    }

    /** 사용자 연결 코드로 이 폰을 사용자로 연결한다. */
    suspend fun claimPairCode(code: String): ClaimResult {
        val uid = uid()
        val (familyId, result) = readCode(code, PairKind.User)
        if (familyId == null) return result

        return try {
            family(familyId).update(mapOf("grandmaUid" to uid, "claimCode" to code)).awaitResult()
            ClaimResult.Success(familyId)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> ClaimResult.AlreadyPaired
                FirebaseFirestoreException.Code.NOT_FOUND -> ClaimResult.InvalidCode
                else -> throw e
            }
        }
    }

    suspend fun uploadDaily(familyId: String, date: LocalDate, steps: Long) {
        family(familyId).collection("daily").document(date.toString())
            .set(mapOf("steps" to steps, "updatedAt" to FieldValue.serverTimestamp()))
            .awaitResult()
    }

    private companion object {
        const val MAX_CODE_TRIES = 5
        const val CODE_VALID_MILLIS = 10 * 60 * 1000L
    }
}
