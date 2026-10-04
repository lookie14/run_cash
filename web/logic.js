// 앱(domain 패키지)과 같은 계산 규칙. 사이트와 앱의 금액이 항상 같아야 한다.

export const DEFAULT_RULES = { goal: 5000, maxWon: 1000 };

const pad = (n) => String(n).padStart(2, "0");

/** 브라우저 현지 날짜 기준 "2026-10-15" */
export const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
export const today0 = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; };
/** "2026-10" (month는 1~12) */
export const monthKey = (year, month) => `${year}-${pad(month)}`;
export const prevMonth = (year, month) => (month === 1 ? [year - 1, 12] : [year, month - 1]);

/** 날짜별 규칙. 관리자가 바꾸면 "그 다음 날부터" 적용된다. rules 필드: {"2026-10-16": {goal, maxWon}} */
export function makeSchedule(rulesField) {
  const entries = Object.entries(rulesField || {})
    .filter(([k, v]) => /^\d{4}-\d{2}-\d{2}$/.test(k) && v && typeof v.goal === "number" && typeof v.maxWon === "number")
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  return {
    rulesOn(dateIso) {
      let r = DEFAULT_RULES;
      for (const [k, v] of entries) {
        if (k <= dateIso) r = { goal: v.goal, maxWon: v.maxWon };
        else break;
      }
      return r;
    },
  };
}

/** 목표보다 적으면 걸은 만큼 비례, 목표 이상이면 하루 최대 금액 */
export function wonForDay(schedule, dateIso, steps) {
  const { goal, maxWon } = schedule.rulesOn(dateIso);
  if (steps <= 0) return 0;
  if (steps >= goal) return maxWon;
  return Math.floor((steps * maxWon) / goal);
}

/** steps: {"2026-10-15": 6000, ...} 중 prefix("2026-10")로 시작하는 날짜만 요약 */
export function monthSummary(schedule, steps, prefix) {
  let totalSteps = 0, goalDays = 0, recordedDays = 0, won = 0;
  for (const [d, s] of Object.entries(steps)) {
    if (!d.startsWith(prefix)) continue;
    totalSteps += s;
    if (s >= schedule.rulesOn(d).goal) goalDays++;
    if (s > 0) recordedDays++;
    won += wonForDay(schedule, d, s);
  }
  return { totalSteps, goalDays, recordedDays, won };
}

/** 최근 7일 막대, 연속 달성(오늘 미달이어도 어제까지 연속은 유지), 어제까지 7일 평균 vs 그 전 7일 평균 */
export function activityStats(schedule, steps, today) {
  const on = (d) => steps[iso(d)] || 0;
  const reached = (d) => on(d) >= schedule.rulesOn(iso(d)).goal;

  const bars = [];
  for (let back = 6; back >= 0; back--) {
    const d = addDays(today, -back);
    bars.push({ date: d, steps: on(d), isToday: back === 0 });
  }

  let streak = reached(today) ? 1 : 0;
  let d = addDays(today, -1);
  while (reached(d)) { streak++; d = addDays(d, -1); }

  let recent = 0, previous = 0;
  for (let i = 1; i <= 7; i++) recent += on(addDays(today, -i));
  for (let i = 8; i <= 14; i++) previous += on(addDays(today, -i));

  return { bars, streak, recentAvg: Math.floor(recent / 7), previousAvg: Math.floor(previous / 7) };
}
