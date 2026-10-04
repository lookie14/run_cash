import { initializeApp } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js";
import { getAuth, signInAnonymously, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js";
import {
  getFirestore, doc, getDoc, updateDoc, onSnapshot, collection, query, where,
  documentId, arrayUnion, serverTimestamp,
} from "https://www.gstatic.com/firebasejs/12.19.0/firebase-firestore.js";
import { firebaseConfig } from "./config.js";
import {
  iso, addDays, today0, monthKey, prevMonth, makeSchedule, wonForDay, monthSummary, activityStats,
} from "./logic.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const FAMILY_KEY = "runcash.familyId";
const RECENT_DAYS = 63;
const STALE_MS = 24 * 60 * 60 * 1000;

const $ = (id) => document.getElementById(id);
const fmt = (n) => Number(n).toLocaleString("ko-KR");
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const shortSteps = (s) => (s < 1000 ? String(s) : `${(s / 1000).toFixed(1)}천`);
const WEEK = ["일", "월", "화", "수", "목", "금", "토"];

function timeAgo(ms) {
  const m = Math.floor(ms / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  if (m < 60 * 24) return `${Math.floor(m / 60)}시간 전`;
  return `${Math.floor(m / (60 * 24))}일 전`;
}
const dateTime = (ms) => new Date(ms).toLocaleString("ko-KR", { month: "long", day: "numeric", hour: "numeric", minute: "2-digit" });
const monthDay = (ms) => new Date(ms).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });

function showError(e) {
  console.error(e);
  $("error").textContent = `문제가 생겼어요: ${e?.message || e}`;
}

// ---------------------------------------------------------------- 상태
const state = {
  uid: null,
  familyId: null,
  family: null,          // 가족 문서 데이터
  recent: null,          // {iso: {steps, updatedAt}}
  settlement: undefined, // 지난달 정산 (undefined = 아직 모름, null = 없음)
  calMonth: null,        // [year, month]
  calSteps: null,        // 달력 달의 {iso: steps}
  selected: iso(today0()),
};
let unsubs = [];
let calUnsub = null;

function stopListening() {
  unsubs.forEach((u) => u());
  unsubs = [];
  if (calUnsub) calUnsub();
  calUnsub = null;
}

// ---------------------------------------------------------------- 시작
onAuthStateChanged(auth, (user) => {
  if (!user) {
    signInAnonymously(auth).catch(showError);
    return;
  }
  state.uid = user.uid;
  const saved = localStorage.getItem(FAMILY_KEY);
  if (saved) startDashboard(saved);
  else showJoin();
});

function showJoin(message = "") {
  stopListening();
  $("loading").classList.add("hidden");
  $("dash").classList.add("hidden");
  $("join").classList.remove("hidden");
  $("join-msg").textContent = message;
}

$("join-btn").addEventListener("click", async () => {
  const name = $("join-name").value.trim();
  const code = $("join-code").value.replace(/\D/g, "");
  if (!name) { $("join-msg").textContent = "이름을 넣어 주세요."; return; }
  if (code.length !== 6) { $("join-msg").textContent = "숫자 6개를 넣어 주세요."; return; }
  $("join-btn").disabled = true;
  $("join-msg").textContent = "";
  try {
    const snap = await getDoc(doc(db, "pairCodes", code));
    const data = snap.exists() ? snap.data() : null;
    if (!data || data.kind !== "admin" || !data.familyId) throw new Error("코드가 맞지 않아요. 앱의 '관리자 추가' 숫자인지 확인해 주세요.");
    if (!data.expiresAt || data.expiresAt.toMillis() < Date.now()) throw new Error("시간이 지난 코드예요. 앱에서 새 숫자를 받아 주세요.");
    await updateDoc(doc(db, "families", data.familyId), {
      adminUids: arrayUnion(state.uid),
      [`admins.${state.uid}`]: { name: `${name} (웹)`, joinedAt: serverTimestamp() },
      lastJoinCode: code,
    });
    localStorage.setItem(FAMILY_KEY, data.familyId);
    startDashboard(data.familyId);
  } catch (e) {
    $("join-msg").textContent = e.code === "permission-denied"
      ? "참여하지 못했어요. 코드를 다시 확인해 주세요."
      : (e.message || String(e));
  } finally {
    $("join-btn").disabled = false;
  }
});

function startDashboard(familyId) {
  stopListening();
  state.familyId = familyId;
  state.family = null;
  state.recent = null;
  state.settlement = undefined;
  const t = today0();
  state.calMonth = [t.getFullYear(), t.getMonth() + 1];
  state.selected = iso(t);

  $("join").classList.add("hidden");
  $("loading").classList.remove("hidden");

  const famRef = doc(db, "families", familyId);

  // 가족 문서: 관리자 목록, 규칙. 내가 관리자에서 빠지면 참여 화면으로 돌아간다.
  unsubs.push(onSnapshot(famRef, (snap) => {
    if (!snap.exists() && snap.metadata.fromCache) return;
    const data = snap.data();
    if (!data || !(data.adminUids || []).includes(state.uid)) {
      localStorage.removeItem(FAMILY_KEY);
      showJoin("관리자 목록에서 빠졌어요. 다시 보려면 초대 숫자를 받아 주세요.");
      return;
    }
    state.family = data;
    render();
  }, (e) => {
    if (e.code === "permission-denied") {
      localStorage.removeItem(FAMILY_KEY);
      showJoin("이 가족을 볼 수 없어요. 다시 보려면 초대 숫자를 받아 주세요.");
    } else showError(e);
  }));

  // 최근 63일 기록
  const start = iso(addDays(t, -RECENT_DAYS));
  const end = iso(t);
  const dailyQ = query(collection(famRef, "daily"), where(documentId(), ">=", start), where(documentId(), "<=", end));
  unsubs.push(onSnapshot(dailyQ, (snap) => {
    const recent = {};
    snap.forEach((d) => {
      const v = d.data();
      recent[d.id] = { steps: Number(v.steps || 0), updatedAt: v.updatedAt ? v.updatedAt.toMillis() : null };
    });
    state.recent = recent;
    render();
  }, showError));

  // 지난달 정산
  const [py, pm] = prevMonth(t.getFullYear(), t.getMonth() + 1);
  unsubs.push(onSnapshot(doc(famRef, "settlements", monthKey(py, pm)), (snap) => {
    state.settlement = snap.exists() ? snap.data() : null;
    render();
  }, showError));

  listenCalendar();
}

function listenCalendar() {
  if (calUnsub) calUnsub();
  const [y, m] = state.calMonth;
  const last = new Date(y, m, 0).getDate();
  const famRef = doc(db, "families", state.familyId);
  const q = query(
    collection(famRef, "daily"),
    where(documentId(), ">=", `${monthKey(y, m)}-01`),
    where(documentId(), "<=", `${monthKey(y, m)}-${String(last).padStart(2, "0")}`),
  );
  state.calSteps = null;
  calUnsub = onSnapshot(q, (snap) => {
    const steps = {};
    snap.forEach((d) => { steps[d.id] = Number(d.data().steps || 0); });
    state.calSteps = steps;
    render();
  }, showError);
}

// 1분마다 "N분 전" 갱신, 날짜가 바뀌면 새로 불러오기
let loadedDay = iso(today0());
setInterval(() => {
  if (iso(today0()) !== loadedDay && state.familyId) {
    loadedDay = iso(today0());
    startDashboard(state.familyId);
  } else render();
}, 60000);

// ---------------------------------------------------------------- 그리기
function render() {
  if (!state.family || !state.recent) return;
  $("loading").classList.add("hidden");
  $("dash").classList.remove("hidden");

  const t = today0();
  const todayIso = iso(t);
  const schedule = makeSchedule(state.family.rules);
  const steps = Object.fromEntries(Object.entries(state.recent).map(([k, v]) => [k, v.steps]));

  renderStatus();
  renderSettlement(schedule, steps, t);
  renderMonth(schedule, steps, t, todayIso);
  const stats = activityStats(schedule, steps, t);
  renderWeek(stats, schedule.rulesOn(todayIso).goal);
  renderTrend(stats);
  renderRules(schedule, todayIso);
  renderCalendar(schedule, t);
  renderAdmins();
}

function renderStatus() {
  const el = $("status");
  const times = Object.values(state.recent).map((r) => r.updatedAt).filter(Boolean);
  const last = times.length ? Math.max(...times) : null;
  const now = Date.now();
  el.className = "card";
  if (!state.family.grandmaUid) {
    el.classList.add("idle");
    el.innerHTML = `<div class="title-ok" style="color:var(--muted)">사용자 폰이 아직 연결되지 않았어요</div>
      <div class="muted small">앱의 관리자 화면에서 사용자 연결 숫자를 만들어 주세요.</div>`;
  } else if (!last) {
    el.classList.add("idle");
    el.innerHTML = `<div class="title-ok" style="color:var(--ink)">아직 올라온 기록이 없어요</div>
      <div class="muted small">사용자 폰에서 앱을 한 번 열면 기록이 올라와요.</div>`;
  } else if (now - last > STALE_MS) {
    el.classList.add("warn");
    el.innerHTML = `<div class="title-warn">기록이 멈춘 것 같아요</div>
      <div>마지막 기록 ${timeAgo(now - last)} (${dateTime(last)})</div>
      <div class="muted small">걷지 않으셨을 수도 있지만, 사용자 폰에서 앱이 열리는지, 인터넷과 걸음 수 권한이 켜져 있는지 확인해 보세요.</div>`;
  } else {
    el.classList.add("ok");
    el.innerHTML = `<div class="title-ok">정상 · 마지막 기록 ${timeAgo(Math.max(0, now - last))}</div>
      <div class="muted small">${dateTime(last)}</div>`;
  }
}

function renderSettlement(schedule, steps, t) {
  const el = $("settlement");
  if (state.settlement === undefined) { el.classList.add("hidden"); return; }
  const [py, pm] = prevMonth(t.getFullYear(), t.getMonth() + 1);
  const s = state.settlement;
  if (s) {
    const who = s.paidBy ? `${esc(s.paidBy)}님` : "관리자";
    const when = s.paidAt ? ` · ${monthDay(s.paidAt.toMillis())}` : "";
    el.innerHTML = `<div class="muted">${pm}월 정산 완료</div>
      <div class="big" style="color:var(--forest)">${fmt(s.amount || 0)}원 보냄</div>
      <div class="muted small">${who} 보냄${when}</div>`;
    el.classList.remove("hidden");
    return;
  }
  const sum = monthSummary(schedule, steps, monthKey(py, pm));
  if (sum.won <= 0) { el.classList.add("hidden"); return; }
  el.innerHTML = `<div class="muted">${pm}월 용돈 정산 · 아직 안 보냄</div>
    <div class="big">${fmt(sum.won)}원</div>
    <div class="muted small">목표 달성 ${sum.goalDays}일 · ${fmt(sum.totalSteps)}걸음 · 송금 후 앱에서 "보냈어요"를 눌러 주세요</div>`;
  el.style.background = "#FFE8E0";
  el.classList.remove("hidden");
}

function renderMonth(schedule, steps, t, todayIso) {
  const sum = monthSummary(schedule, steps, monthKey(t.getFullYear(), t.getMonth() + 1));
  $("month").innerHTML = `<div class="muted">이번 달 모인 용돈</div>
    <div class="big">${fmt(sum.won)}원</div>
    <hr>
    <div class="stats">
      <div class="stat"><div class="label">오늘 걸음</div><div class="value">${fmt(steps[todayIso] || 0)}</div></div>
      <div class="stat"><div class="label">이번 달 걸음</div><div class="value">${fmt(sum.totalSteps)}</div></div>
      <div class="stat"><div class="label">목표 달성</div><div class="value">${sum.goalDays}일</div></div>
      <div class="stat"><div class="label">기록된 날</div><div class="value">${sum.recordedDays}일</div></div>
    </div>`;
}

function renderWeek(stats, goal) {
  const max = Math.max(goal * 1.25, ...stats.bars.map((b) => b.steps * 1.1), 1);
  const bars = stats.bars.map((b) => {
    const cls = b.isToday ? "today" : b.steps >= goal ? "reached" : "";
    return `<div class="slot"><div class="bar ${cls}" style="height:${(b.steps / max) * 100}%"></div></div>`;
  }).join("");
  const labels = stats.bars.map((b) =>
    `<div><div>${shortSteps(b.steps)}</div><div class="day">${b.isToday ? "<b>오늘</b>" : WEEK[b.date.getDay()]}</div></div>`).join("");
  $("week").innerHTML = `<h2>최근 7일</h2>
    <div class="chart">${bars}<div class="goal" style="bottom:${(goal / max) * 100}%"></div></div>
    <div class="chart-labels">${labels}</div>
    <div class="muted small" style="margin-top:6px">점선: 목표 ${fmt(goal)}걸음 · 초록: 목표 달성 · 노랑: 오늘(진행 중)</div>`;
}

function renderTrend(stats) {
  const { recentAvg: r, previousAvg: p } = stats;
  let text, color;
  if (p === 0 && r === 0) { text = "지난 2주 동안 기록이 없어요"; color = "var(--muted)"; }
  else if (p === 0) { text = "그 전 주에는 기록이 없었어요"; color = "var(--muted)"; }
  else {
    const change = Math.trunc(((r - p) * 100) / p);
    if (change >= 5) { text = `그 전 7일보다 ${change}% 늘었어요 ▲`; color = "var(--forest)"; }
    else if (change <= -5) { text = `그 전 7일보다 ${-change}% 줄었어요 ▼`; color = "var(--danger)"; }
    else { text = "그 전 7일과 비슷해요"; color = "var(--ink)"; }
  }
  $("trend").innerHTML = `<h2>추세</h2>
    <div class="stats">
      <div class="stat"><div class="label">연속 목표 달성</div><div class="value">${stats.streak}일째</div></div>
      <div class="stat"><div class="label">최근 7일 평균</div><div class="value">${fmt(r)}걸음</div></div>
    </div>
    <div style="margin-top:10px;font-weight:700;color:${color}">${text}</div>
    <div class="muted small">그 전 7일 평균 ${fmt(p)}걸음 · 오늘은 진행 중이라 평균에서 뺐어요</div>`;
}

function renderRules(schedule, todayIso) {
  const now = schedule.rulesOn(todayIso);
  const next = schedule.rulesOn(iso(addDays(today0(), 1)));
  const changed = now.goal !== next.goal || now.maxWon !== next.maxWon;
  $("rules").innerHTML = `<h2>목표</h2>
    <div>오늘: ${fmt(now.goal)}걸음 · 하루 최대 ${fmt(now.maxWon)}원</div>
    ${changed ? `<div style="color:var(--forest);font-weight:700">내일부터: ${fmt(next.goal)}걸음 · 하루 최대 ${fmt(next.maxWon)}원</div>` : ""}
    <div class="muted small">목표보다 적게 걸으면 걸은 만큼 비례해서 쌓여요.</div>`;
}

function renderCalendar(schedule, t) {
  const el = $("calendar");
  const [y, m] = state.calMonth;
  const todayIso = iso(t);
  const canNext = y < t.getFullYear() || (y === t.getFullYear() && m < t.getMonth() + 1);
  const head = `<h2>달력</h2><div class="cal-head">
      <button id="cal-prev" aria-label="이전 달">◀</button>
      <span class="m">${y}년 ${m}월</span>
      <button id="cal-next" aria-label="다음 달" ${canNext ? "" : "disabled"}>▶</button>
    </div>`;

  if (!state.calSteps) {
    el.innerHTML = head + `<p class="muted">불러오는 중...</p>`;
  } else {
    const steps = state.calSteps;
    const first = new Date(y, m - 1, 1);
    const days = new Date(y, m, 0).getDate();
    let won = 0, goalDays = 0, cells = "";
    for (let i = 0; i < first.getDay(); i++) cells += "<div></div>";
    for (let day = 1; day <= days; day++) {
      const d = iso(new Date(y, m - 1, day));
      const future = d > todayIso;
      const s = future ? 0 : (steps[d] || 0);
      const reached = !future && s >= schedule.rulesOn(d).goal;
      if (!future) { won += wonForDay(schedule, d, s); if (reached) goalDays++; }
      const cls = ["cell", reached && "reached", d === todayIso && "today", d === state.selected && "sel", future && "future"].filter(Boolean).join(" ");
      cells += `<button class="${cls}" data-date="${d}" ${future ? "disabled" : ""}>
        <span class="n">${day}</span>
        <span class="stamp ${reached ? "" : "empty"}">${reached ? "✓" : ""}</span>
        <span class="s">${future ? "" : shortSteps(s)}</span></button>`;
    }
    const weekdays = WEEK.map((w, i) => `<div class="wd ${i === 0 ? "sun" : ""}">${w}</div>`).join("");

    let detail = `<div class="detail muted">날짜를 눌러 보세요</div>`;
    if (state.selected && state.selected.startsWith(monthKey(y, m)) && state.selected <= todayIso) {
      const d = state.selected;
      const s = steps[d] || 0;
      const goal = schedule.rulesOn(d).goal;
      const [, mm, dd] = d.split("-").map(Number);
      detail = `<div class="detail">
        <div class="muted">${mm}월 ${dd}일 ${WEEK[new Date(y, mm - 1, dd).getDay()]}요일</div>
        <div class="big">${fmt(s)}걸음</div>
        <div style="font-weight:700;color:${s >= goal ? "var(--forest)" : "var(--ink)"}">
          ${s >= goal ? "목표를 채웠어요!" : `목표까지 ${fmt(Math.max(0, goal - s))}걸음`}</div>
        <div style="font-size:22px;font-weight:700;color:var(--forest)">+${fmt(wonForDay(schedule, d, s))}원</div>
        ${d === todayIso ? `<div class="muted small">오늘 값은 진행 중이에요</div>` : ""}
      </div>`;
    }
    el.innerHTML = head +
      `<div class="muted" style="margin-top:6px">이 달 ${fmt(won)}원 · 목표 달성 ${goalDays}일</div>
       <div class="grid">${weekdays}${cells}</div>${detail}`;
    el.querySelectorAll(".cell[data-date]").forEach((b) =>
      b.addEventListener("click", () => { state.selected = b.dataset.date; render(); }));
  }

  $("cal-prev").onclick = () => {
    state.calMonth = prevMonth(y, m);
    state.selected = null;
    listenCalendar();
    render();
  };
  $("cal-next").onclick = () => {
    if (!canNext) return;
    state.calMonth = m === 12 ? [y + 1, 1] : [y, m + 1];
    const now = today0();
    state.selected = state.calMonth[0] === now.getFullYear() && state.calMonth[1] === now.getMonth() + 1 ? iso(now) : null;
    listenCalendar();
    render();
  };
}

function renderAdmins() {
  const admins = state.family.admins || {};
  const uids = state.family.adminUids || [];
  const list = uids
    .map((uid) => ({ uid, name: admins[uid]?.name || "이름 없음", joined: admins[uid]?.joinedAt?.toMillis?.() || null }))
    .sort((a, b) => (a.joined || Infinity) - (b.joined || Infinity));
  $("admins").innerHTML = `<h2>관리자 ${list.length}명</h2>` + list.map((a) =>
    `<div class="row"><div><b>${esc(a.name)}</b>${a.uid === state.uid ? " (나)" : ""}</div>
      <div class="muted small">${a.joined ? `${monthDay(a.joined)} 추가` : ""}</div></div>`).join("");
}
