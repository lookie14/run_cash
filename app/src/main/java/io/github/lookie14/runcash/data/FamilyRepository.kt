package io.github.lookie14.runcash.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import kotlin.random.Random

data class PairCode(val code: String, val expiresAtMillis: Long)

data class FamilyState(val exists: Boolean, val paired: Boolean)

sealed interface ClaimResult {
    data class Success(val familyId: String) : ClaimResult
    data object InvalidCode : ClaimResult
    data object Expired : ClaimResult
    /** 이미 다른 폰과 연결된 가족이다. */
    data object AlreadyPaired : ClaimResult
}

/**
 * Firestore 구조
 *  families/{손주 uid}                : grandsonUid, grandmaUid(연결 전 null), createdAt
 *  families/{손주 uid}/daily/{날짜}   : steps, updatedAt  (걸음 수 원본. 포인트는 계산으로 구한다)
 *  pairCodes/{6자리 코드}              : familyId, expiresAt
 */
class FamilyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun uid(): String =
        auth.currentUser?.uid
            ?: auth.signInAnonymously().awaitResult().user?.uid
            ?: error("로그인에 실패했어요")

    private fun family(familyId: String) = db.collection("families").document(familyId)

    // ---- 손주 폰 ----

    /** 내 가족 문서가 없으면 만든다. familyId는 내 uid다. */
    suspend fun ensureFamily(): String {
        val uid = uid()
        val ref = family(uid)
        if (!ref.get().awaitResult().exists()) {
            ref.set(
                mapOf(
                    "grandsonUid" to uid,
                    "grandmaUid" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).awaitResult()
        }
        return uid
    }

    /** 6자리 연결 코드를 만든다. 이미 있는 코드와 겹치면 규칙이 막으므로 다른 코드로 다시 시도한다. */
    suspend fun createPairCode(): PairCode {
        val familyId = ensureFamily()
        repeat(MAX_CODE_TRIES) {
            val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
            val expiresAt = System.currentTimeMillis() + CODE_VALID_MILLIS
            try {
                db.collection("pairCodes").document(code)
                    .set(mapOf("familyId" to familyId, "expiresAt" to Timestamp(Date(expiresAt))))
                    .awaitResult()
                return PairCode(code, expiresAt)
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            }
        }
        error("연결 코드를 만들지 못했어요. 잠시 뒤에 다시 해 주세요")
    }

    /** 연결을 끊는다. 할머니 폰은 다시 코드를 입력해야 한다. */
    suspend fun unpair(familyId: String) {
        family(familyId).update("grandmaUid", null).awaitResult()
    }

    fun observeFamily(familyId: String): Flow<FamilyState> = callbackFlow {
        val registration = family(familyId).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(FamilyState(exists = snap?.exists() == true, paired = snap?.getString("grandmaUid") != null))
        }
        awaitClose { registration.remove() }
    }

    /** 그 달의 날짜별 걸음 수. 기록이 없는 날은 맵에 없다. */
    fun observeMonth(familyId: String, month: YearMonth): Flow<Map<LocalDate, Long>> = callbackFlow {
        val registration = family(familyId).collection("daily")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), month.atDay(1).toString())
            .whereLessThanOrEqualTo(FieldPath.documentId(), month.atEndOfMonth().toString())
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val map = snap?.documents.orEmpty().mapNotNull { doc ->
                    val date = runCatching { LocalDate.parse(doc.id) }.getOrNull() ?: return@mapNotNull null
                    date to (doc.getLong("steps") ?: 0L)
                }.toMap()
                trySend(map)
            }
        awaitClose { registration.remove() }
    }

    // ---- 할머니 폰 ----

    suspend fun claimPairCode(code: String): ClaimResult {
        val uid = uid()
        val codeSnap = db.collection("pairCodes").document(code).get().awaitResult()
        if (!codeSnap.exists()) return ClaimResult.InvalidCode
        val expiresAt = codeSnap.getTimestamp("expiresAt")?.toDate()?.time ?: return ClaimResult.InvalidCode
        if (expiresAt < System.currentTimeMillis()) return ClaimResult.Expired
        val familyId = codeSnap.getString("familyId") ?: return ClaimResult.InvalidCode

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

    /**
     * 할머니 폰이 아직 이 가족에 연결되어 있는지 지켜본다. true = 연결됨, false = 끊김.
     * 손주가 연결을 끊으면 규칙상 읽기 권한이 사라지므로 PERMISSION_DENIED도 끊김으로 본다.
     * 오프라인일 때 기기에 저장된 값이 없어서 "문서 없음"으로 보이는 경우는 끊김으로 보지 않는다.
     */
    fun observeGrandmaLink(familyId: String): Flow<Boolean> = callbackFlow {
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
            trySend(snap.exists() && snap.getString("grandmaUid") == myUid)
        }
        awaitClose { registration.remove() }
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
