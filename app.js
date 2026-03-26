// ============ STATE ============
const STORAGE_KEY = 'hiking-trainer-v1';
let state = {
  screen: 'program', weekIdx: 0, dayIdx: 0,
  completed: {},   // {"w0d0b0e0": true, ...}
  openBlocks: {},
  profile: 'hike',
  venues: {},      // per-day overrides
  hikeDate: '',    // ISO date string for countdown
  notes: {},       // {"w0d0": "text", ...}
  history: {},     // {"w0d0": {date: "2026-03-15", startMs: 123, endMs: 456}, ...}
  weekOffset: 0,   // number of weeks shifted (for skip)
  readiness: {},   // {"phase1_0": true, ...}
  dayMetrics: {},  // {"w0d0": {rpe:"7", pulse:"148", pack:"10"}, ...}
  cal: {
    start: '',
    times: ['18:00', '18:00', '10:00'],
    duration: 90
  },
  expedition: {
    gear: {},
    hikes: [],
    taper: {}
  }
};

const uiState = {
  expandedPhases: {},
  expandedPhaseWeeks: {},
  pendingExerciseFocus: ''
};

const PERSIST_KEYS = ['completed','weekIdx','profile','venues','hikeDate','notes','history','weekOffset','readiness','dayMetrics','cal','expedition'];

const CALENDAR_DEFAULTS = {
  start: '',
  times: ['18:00', '18:00', '10:00'],
  duration: 90
};

const EXPEDITION_GEAR = [
  { id: 'boots', label: 'Ботинки разношены и проверены на длинной прогулке' },
  { id: 'pack', label: 'Рюкзак подогнан и уже был в тренировках с весом' },
  { id: 'shell', label: 'Мембранная куртка и защита от дождя готовы' },
  { id: 'layers', label: 'Тёплый слой и сменная база собраны' },
  { id: 'water', label: 'Фляги / гидратор / схема воды продуманы' },
  { id: 'headlamp', label: 'Фонарь, батарейки и зарядка подготовлены' },
  { id: 'aid', label: 'Аптечка и личные лекарства собраны' },
  { id: 'food', label: 'Питание и перекусы на маршрут продуманы' }
];

const TAPER_CHECKLIST = [
  { id: 'sleep', label: 'Держу сон и восстановление в приоритете' },
  { id: 'volume', label: 'Не добираю лишний объём сверх плана' },
  { id: 'gear_recheck', label: 'Перепроверил снаряжение и раскладку' },
  { id: 'route', label: 'Проверил маршрут, логистику и прогноз' }
];

function loadState() {
  try {
    const s = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (s) {
      PERSIST_KEYS.forEach(k => { if (s[k] != null) state[k] = s[k]; });
      state.cal = {
        start: state.cal?.start || '',
        times: Array.isArray(state.cal?.times) && state.cal.times.length === 3 ? state.cal.times : [...CALENDAR_DEFAULTS.times],
        duration: Number(state.cal?.duration) > 0 ? Number(state.cal.duration) : CALENDAR_DEFAULTS.duration
      };
      state.expedition = {
        gear: state.expedition?.gear && typeof state.expedition.gear === 'object' ? state.expedition.gear : {},
        hikes: Array.isArray(state.expedition?.hikes) ? state.expedition.hikes : [],
        taper: state.expedition?.taper && typeof state.expedition.taper === 'object' ? state.expedition.taper : {}
      };
    }
  } catch(e) {}
}

function saveState() {
  const o = {};
  PERSIST_KEYS.forEach(k => o[k] = state[k]);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(o));
}

function exId(w, d, bIdx, eIdx) { return `w${w}d${d}b${bIdx}e${eIdx}`; }
function dayId(w, d) { return `w${w}d${d}`; }
function readinessId(phaseIdx, itemIdx) { return `phase${phaseIdx}_${itemIdx}`; }
function getDayMetrics(w, d) { return state.dayMetrics[dayId(w, d)] || { rpe: '', pulse: '', pack: '' }; }

function isExDone(id) { return !!state.completed[id]; }

function ensureDayHistory(w, d) {
  const key = dayId(w, d);
  if (!state.history[key]) state.history[key] = {};
  return state.history[key];
}

function syncDayHistory(w, d, now = Date.now()) {
  const h = ensureDayHistory(w, d);
  const dc = getDayExCount(w, d);
  h.date = new Date(now).toISOString().slice(0,10);
  h.lastActionMs = now;
  if (dc.done > 0 && !h.startMs) h.startMs = now;
  if (dc.done > 0) h.endMs = now;
  if (dc.done === dc.total && dc.total > 0) h.completedAt = now;
  else delete h.completedAt;
}

function toggleEx(id) {
  if (state.completed[id]) delete state.completed[id];
  else state.completed[id] = true;
  const m = id.match(/w(\d+)d(\d+)/);
  if (m) {
    const now = Date.now();
    syncDayHistory(Number(m[1]), Number(m[2]), now);
  }
  saveState();
}

function getDayExCount(wIdx, dIdx) {
  const day = getDayData(wIdx, dIdx);
  let total = 0, done = 0;
  day.blocks.forEach((bl, bi) => {
    bl.exercises.forEach((_, ei) => {
      total++;
      if (isExDone(exId(wIdx, dIdx, bi, ei))) done++;
    });
  });
  return { total, done };
}

function getWeekProgress(wIdx) {
  let total = 0, done = 0;
  PROGRAM[wIdx].forEach((_, di) => {
    const c = getDayExCount(wIdx, di);
    total += c.total; done += c.done;
  });
  return { total, done };
}

function getTotalProgress() {
  let total = 0, done = 0;
  PROGRAM.forEach((_, wi) => {
    const c = getWeekProgress(wi);
    total += c.total; done += c.done;
  });
  return { total, done };
}

function getPhase(wIdx) { return Math.floor(wIdx / 4); }

function getCurrentWeekIndex() {
  const nt = getNextTraining();
  return nt ? nt.w : Math.min(Math.max(state.weekOffset || 0, 0), PROGRAM.length - 1);
}

function getCurrentPhaseIndex() {
  return getPhase(getCurrentWeekIndex());
}

function getCurrentWeekWithinPhase() {
  const currentWeek = getCurrentWeekIndex();
  return currentWeek - getCurrentPhaseIndex() * 4;
}

function getWeekMeta(wIdx) {
  const wp = getWeekProgress(wIdx);
  const daySummaries = PROGRAM[wIdx].map((_, di) => {
    const day = getDayData(wIdx, di);
    const dc = getDayExCount(wIdx, di);
    const workBlocks = day.blocks.filter(b => b.type !== 'warmup' && b.type !== 'cooldown');
    const workExerciseCount = workBlocks.reduce((sum, block) => sum + block.exercises.length, 0);
    return {
      day,
      progress: dc,
      workBlockCount: workBlocks.length,
      workExerciseCount
    };
  });
  return {
    progress: wp,
    daySummaries,
    workBlockCount: daySummaries.reduce((sum, item) => sum + item.workBlockCount, 0),
    workExerciseCount: daySummaries.reduce((sum, item) => sum + item.workExerciseCount, 0)
  };
}

function getWeekFocusSummary(wIdx) {
  return PROGRAM[wIdx].map((_, di) => getDayData(wIdx, di).focus).join(' • ');
}

function getDayVenueIcon(venue) {
  return { gym:'🏋️', home:'🏠', outdoor:'🌲', run:'🏃' }[venue] || '⛰️';
}

function getBlockTypeConfig(type) {
  if (type === 'warmup') return { label: 'Разминка', icon: '△' };
  if (type === 'cooldown') return { label: 'Заминка', icon: '▽' };
  if (type === 'sets') return { label: 'Сет', icon: '◼︎' };
  return { label: 'Круг', icon: '◎' };
}

function toggleHomePhase(phaseIdx) {
  uiState.expandedPhases[phaseIdx] = !uiState.expandedPhases[phaseIdx];
  render();
}

function toggleHomePhaseWeeks(phaseIdx) {
  uiState.expandedPhaseWeeks[phaseIdx] = !uiState.expandedPhaseWeeks[phaseIdx];
  render();
}

function getVisiblePhaseWeeks(phaseIdx) {
  const start = phaseIdx * 4;
  const end = start + 4;
  if (uiState.expandedPhaseWeeks[phaseIdx]) {
    return Array.from({ length: 4 }, (_, i) => start + i);
  }

  if (phaseIdx !== getCurrentPhaseIndex()) {
    return [start, start + 1];
  }

  const currentWithinPhase = getCurrentWeekWithinPhase();
  const first = Math.max(start, start + currentWithinPhase);
  const second = Math.min(end - 1, first + 1);
  return first === second ? [first] : [first, second];
}

function getFirstIncompleteExercise(wIdx, dIdx) {
  const day = getDayData(wIdx, dIdx);
  for (let bi = 0; bi < day.blocks.length; bi++) {
    for (let ei = 0; ei < day.blocks[bi].exercises.length; ei++) {
      const id = exId(wIdx, dIdx, bi, ei);
      if (!isExDone(id)) return { id, blockId: `${wIdx}-${dIdx}-${bi}` };
    }
  }
  return null;
}

function focusExerciseElement(elementId) {
  setTimeout(() => {
    const el = document.getElementById(elementId);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('target');
    setTimeout(() => el.classList.remove('target'), 1400);
  }, 40);
}

function goToNextExercise(wIdx, dIdx) {
  const next = getFirstIncompleteExercise(wIdx, dIdx);
  if (!next) return;
  state.openBlocks[next.blockId] = true;
  uiState.pendingExerciseFocus = `exercise-${next.id}`;
  render();
}

// Next incomplete training
function getNextTraining() {
  const startWeek = Math.min(Math.max(state.weekOffset || 0, 0), PROGRAM.length - 1);
  for (let w = startWeek; w < 16; w++) {
    for (let d = 0; d < 3; d++) {
      const dc = getDayExCount(w, d);
      if (dc.done < dc.total) return { w, d };
    }
  }
  return null;
}

// Streak: count consecutive completed days (backwards from latest completed)
function getStreak() {
  const completedDays = [];
  for (let w = 0; w < 16; w++) {
    for (let d = 0; d < 3; d++) {
      const dc = getDayExCount(w, d);
      completedDays.push(dc.done === dc.total && dc.total > 0);
    }
  }
  let lastCompleted = -1;
  for (let i = completedDays.length - 1; i >= 0; i--) {
    if (completedDays[i]) { lastCompleted = i; break; }
  }
  if (lastCompleted === -1) return 0;
  let streak = 0;
  for (let i = lastCompleted; i >= 0; i--) {
    if (!completedDays[i]) break;
    streak++;
  }
  return streak;
}

// Hike countdown
function getHikeCountdown() {
  if (!state.hikeDate) return null;
  const diff = Math.ceil((new Date(state.hikeDate + 'T00:00:00') - new Date()) / 86400000);
  return diff;
}

function setHikeDate(v) {
  state.hikeDate = v;
  saveState();
  render();
}

function getReadinessProgress(phaseIdx) {
  const tests = READINESS_TESTS[phaseIdx] || [];
  const done = tests.filter((_, idx) => !!state.readiness[readinessId(phaseIdx, idx)]).length;
  return { done, total: tests.length };
}

function setReadinessCheck(phaseIdx, itemIdx, checked) {
  const key = readinessId(phaseIdx, itemIdx);
  if (checked) state.readiness[key] = true;
  else delete state.readiness[key];
  saveState();
  render();
}

function setCalendarStart(value) {
  state.cal.start = value;
  saveState();
}

function setCalendarTime(idx, value) {
  state.cal.times[idx] = value || CALENDAR_DEFAULTS.times[idx];
  saveState();
}

function setCalendarDuration(value) {
  const parsed = Number(value);
  state.cal.duration = parsed > 0 ? parsed : CALENDAR_DEFAULTS.duration;
  saveState();
}

function saveDayMetric(w, d, field, value) {
  const key = dayId(w, d);
  const next = { ...getDayMetrics(w, d), [field]: value.trim() };
  if (!next.rpe && !next.pulse && !next.pack) delete state.dayMetrics[key];
  else state.dayMetrics[key] = next;
  saveState();
  if (state.screen === 'day' && state.weekIdx === w && state.dayIdx === d) render();
}

function getPhaseProgress(phaseIdx) {
  let total = 0, done = 0, completedDays = 0;
  for (let w = phaseIdx * 4; w < phaseIdx * 4 + 4; w++) {
    const wp = getWeekProgress(w);
    total += wp.total;
    done += wp.done;
    for (let d = 0; d < 3; d++) {
      const dc = getDayExCount(w, d);
      if (dc.done === dc.total && dc.total > 0) completedDays++;
    }
  }
  return { total, done, completedDays, weeks: 4 };
}

function getRecentSessions(limit = 4) {
  return Object.entries(state.history)
    .filter(([, value]) => value && (value.lastActionMs || value.endMs || value.completedAt))
    .sort((a, b) => (b[1].lastActionMs || b[1].endMs || 0) - (a[1].lastActionMs || a[1].endMs || 0))
    .slice(0, limit)
    .map(([key, value]) => {
      const match = key.match(/w(\d+)d(\d+)/);
      const w = Number(match[1]);
      const d = Number(match[2]);
      const duration = value.endMs && value.startMs ? Math.round((value.endMs - value.startMs) / 60000) : 0;
      return {
        key,
        w,
        d,
        date: value.date,
        duration,
        focus: getDayData(w, d).focus,
        metrics: getDayMetrics(w, d)
      };
    });
}

function toMetricNumber(value) {
  if (value == null || value === '') return null;
  const normalized = String(value).replace(',', '.').trim();
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

function getWeekMetricSummary(w) {
  const sessions = [0,1,2].map(d => {
    const hist = state.history[dayId(w, d)];
    const metrics = getDayMetrics(w, d);
    return {
      dayIdx: d,
      duration: hist && hist.endMs && hist.startMs ? Math.round((hist.endMs - hist.startMs) / 60000) : 0,
      rpe: toMetricNumber(metrics.rpe),
      pulse: toMetricNumber(metrics.pulse),
      pack: toMetricNumber(metrics.pack)
    };
  });

  const durations = sessions.map(s => s.duration).filter(Boolean);
  const rpes = sessions.map(s => s.rpe).filter(v => v != null);
  const pulses = sessions.map(s => s.pulse).filter(v => v != null);
  const packs = sessions.map(s => s.pack).filter(v => v != null);

  return {
    totalDuration: durations.reduce((sum, value) => sum + value, 0),
    averageRpe: rpes.length ? Math.round((rpes.reduce((sum, value) => sum + value, 0) / rpes.length) * 10) / 10 : null,
    averagePulse: pulses.length ? Math.round(pulses.reduce((sum, value) => sum + value, 0) / pulses.length) : null,
    maxPack: packs.length ? Math.max(...packs) : null,
    loggedDays: sessions.filter(session => session.duration || session.rpe != null || session.pulse != null || session.pack != null).length
  };
}

function getWeekReview(w) {
  const weekMeta = getWeekMeta(w);
  const daysShort = ['Вт', 'Чт', 'Сб'];
  const completedDays = weekMeta.daySummaries.filter(summary => summary.progress.done === summary.progress.total && summary.progress.total > 0).length;
  const outstanding = weekMeta.daySummaries
    .map((summary, dayIdx) => ({ dayIdx, focus: summary.day.focus, done: summary.progress.done === summary.progress.total && summary.progress.total > 0 }))
    .filter(item => !item.done);
  const metrics = getWeekMetricSummary(w);
  const isCurrentWeek = w === getCurrentWeekIndex();
  let tone = 'neutral';
  let title = 'Неделя в фокусе';
  let summary = `Фокус недели: ${getWeekFocusSummary(w)}.`;
  let action = outstanding.length ? `Начните с ${daysShort[outstanding[0].dayIdx]}: ${outstanding[0].focus}.` : 'Сохраняйте устойчивый ритм без дополнительного объёма.';

  if (completedDays === 3) {
    tone = metrics.averageRpe && metrics.averageRpe >= 8 ? 'focus' : 'good';
    title = metrics.averageRpe && metrics.averageRpe >= 8 ? 'Сильная неделя закрыта' : 'Неделя закрыта';
    summary = `Все 3 тренировочных дня выполнены${metrics.totalDuration ? ` • около ${metrics.totalDuration} минут работы` : ''}.`;
    action = metrics.averageRpe && metrics.averageRpe >= 8
      ? 'Следующую неделю начните спокойно и не добирайте объём сверх плана.'
      : 'Можно переходить дальше по плану и держать текущий ритм.';
  } else if (completedDays === 2) {
    tone = 'focus';
    title = 'Остался финальный день';
    summary = `Закрыто 2 из 3 тренировочных дней${metrics.totalDuration ? ` • уже набрано ~${metrics.totalDuration} минут` : ''}.`;
    action = outstanding.length ? `Лучший следующий шаг — ${daysShort[outstanding[0].dayIdx]}: ${outstanding[0].focus}.` : action;
  } else if (completedDays === 1) {
    tone = 'neutral';
    title = 'Неделя в процессе';
    summary = `Закрыт 1 из 3 тренировочных дней${metrics.totalDuration ? ` • ${metrics.totalDuration} минут уже в работе` : ''}.`;
    action = outstanding.length ? `Дальше по плану: ${daysShort[outstanding[0].dayIdx]} — ${outstanding[0].focus}.` : action;
  } else if (!isCurrentWeek) {
    tone = 'warn';
    title = 'Неделя ещё не закрыта';
    summary = 'В этой неделе пока нет завершённых тренировочных дней.';
    action = outstanding.length ? `Если возвращаетесь в план, начните с ${daysShort[outstanding[0].dayIdx]}: ${outstanding[0].focus}.` : action;
  }

  const highlights = [];
  if (metrics.averageRpe != null) {
    if (metrics.averageRpe >= 8) highlights.push('Нагрузка ощущалась тяжёлой: на следующем занятии держите запас и следите за техникой.');
    else if (metrics.averageRpe <= 5) highlights.push('Нагрузка ощущалась комфортной: можно просто идти дальше по плану без лишнего объёма.');
  }
  if (metrics.maxPack != null) {
    highlights.push(`Практика с рюкзаком уже была: до ${metrics.maxPack} кг на этой неделе.`);
  } else if (completedDays > 0 && getPhase(w) >= 2) {
    highlights.push('В горных фазах полезно фиксировать вес рюкзака после нагрузочных дней.');
  }
  if (outstanding.length > 0 && completedDays > 0) {
    highlights.push(`Не закрыто: ${outstanding.map(item => `${daysShort[item.dayIdx]} — ${item.focus}`).join('; ')}.`);
  }

  const stats = [
    { label: 'Дней', value: `${completedDays}/3` },
    metrics.totalDuration ? { label: 'Минут', value: `~${metrics.totalDuration}` } : null,
    metrics.averageRpe != null ? { label: 'RPE', value: `${metrics.averageRpe}` } : null,
    metrics.maxPack != null ? { label: 'Рюкзак', value: `${metrics.maxPack} кг` } : null
  ].filter(Boolean);

  return { tone, title, summary, action, highlights, stats };
}

function getReadinessFocusLabel(test) {
  if (/бег|км|мин|непрерывн/i.test(test)) return 'аэробная выносливость';
  if (/рюкзак|поход|ходьба|гору|одышк/i.test(test)) return 'походная нагрузка';
  if (/подтягив|отжим|планк/i.test(test)) return 'кор и верх тела';
  return 'сила ног';
}

function getDominantReadinessFocus(tests) {
  if (!tests.length) return 'общая готовность';
  const counts = tests.reduce((acc, test) => {
    const label = getReadinessFocusLabel(test);
    acc[label] = (acc[label] || 0) + 1;
    return acc;
  }, {});
  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0][0];
}

function getReadinessInsight(phaseIdx) {
  const tests = READINESS_TESTS[phaseIdx] || [];
  if (!tests.length) return null;

  const progress = getReadinessProgress(phaseIdx);
  const prepPhaseIdx = Math.max(phaseIdx - 1, 0);
  const prepPhaseProgress = getPhaseProgress(prepPhaseIdx);
  const prepPct = prepPhaseProgress.total ? prepPhaseProgress.done / prepPhaseProgress.total : 0;
  const remaining = tests.filter((_, idx) => !state.readiness[readinessId(phaseIdx, idx)]);
  const dominantFocus = getDominantReadinessFocus(remaining);

  let tone = 'neutral';
  let title = `Готовность к Фазе ${phaseIdx + 1}`;
  let summary = `${progress.done} из ${progress.total} контрольных ориентиров отмечено.`;
  let action = remaining[0] ? `Следующий лучший шаг — ${remaining[0]}.` : 'Все ориентиры уже закрыты.';

  if (progress.done === progress.total) {
    tone = 'good';
    title = 'Можно переходить уверенно';
    summary = `Все ${progress.total} ориентиров для Фазы ${phaseIdx + 1} выполнены.`;
    action = 'Переходите дальше по плану без форсирования дополнительного объёма.';
  } else if (prepPct < 0.35 && progress.done === 0) {
    tone = 'neutral';
    title = 'Сначала доберите базу';
    summary = `В текущей фазе пока выполнено ${prepPhaseProgress.done}/${prepPhaseProgress.total} упражнений, поэтому тесты ещё рано форсировать.`;
    action = 'Сфокусируйтесь на стабильных тренировках по плану и вернитесь к ориентирам чуть позже.';
  } else if (progress.done === 0) {
    tone = 'focus';
    title = 'Пора начать проверку готовности';
    summary = `База текущей фазы уже собирается, но ни один ориентир для Фазы ${phaseIdx + 1} ещё не отмечен.`;
    action = `Начните с проверки на ${dominantFocus}.`;
  } else if (progress.done / progress.total >= 0.75) {
    tone = 'good';
    title = 'Почти готов к следующей фазе';
    summary = `Закрыто ${progress.done} из ${progress.total} ориентиров. Остались последние штрихи перед переходом.`;
    action = remaining.length === 1 ? `Остался один ориентир: ${remaining[0]}.` : `Осталось ${remaining.length} ориентира, главный фокус — ${dominantFocus}.`;
  } else {
    tone = 'focus';
    title = 'Готовность растёт';
    summary = `Закрыто ${progress.done} из ${progress.total} ориентиров. Основной незакрытый контур сейчас — ${dominantFocus}.`;
    action = remaining[0] ? `Следующий ориентир: ${remaining[0]}.` : action;
  }

  const stats = [
    { label: 'Ориентиры', value: `${progress.done}/${progress.total}` },
    { label: 'Фаза', value: `${prepPhaseIdx + 1} → ${phaseIdx + 1}` },
    prepPhaseProgress.total ? { label: 'Прогресс фазы', value: `${Math.round(prepPct * 100)}%` } : null
  ].filter(Boolean);

  return {
    tone,
    title,
    summary,
    action,
    focus: dominantFocus,
    remaining: remaining.slice(0, 2),
    stats
  };
}

function escapeHtml(value) {
  return escapeAttr(value);
}

function getExpeditionGearProgress() {
  const done = EXPEDITION_GEAR.filter(item => !!state.expedition.gear[item.id]).length;
  return { done, total: EXPEDITION_GEAR.length };
}

function getLongHikeSummary() {
  const hikes = [...(state.expedition.hikes || [])]
    .sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')));
  const totalHours = hikes.reduce((sum, hike) => sum + (toMetricNumber(hike.duration) || 0), 0);
  const maxPack = hikes.reduce((max, hike) => Math.max(max, toMetricNumber(hike.pack) || 0), 0);
  return {
    hikes,
    count: hikes.length,
    totalHours: Math.round(totalHours * 10) / 10,
    maxPack: maxPack || null
  };
}

function isTaperWindow() {
  const currentWeek = getCurrentWeekIndex();
  const hikeDays = getHikeCountdown();
  return currentWeek >= 12 || (hikeDays !== null && hikeDays <= 14);
}

function getTaperProgress() {
  const done = TAPER_CHECKLIST.filter(item => !!state.expedition.taper[item.id]).length;
  return { done, total: TAPER_CHECKLIST.length };
}

function getExpeditionInsight() {
  const gear = getExpeditionGearProgress();
  const hikes = getLongHikeSummary();
  const taper = getTaperProgress();
  const taperActive = isTaperWindow();

  let tone = 'neutral';
  let title = 'Походный контур в процессе';
  let summary = `Снаряжение ${gear.done}/${gear.total} • длинные выходы ${hikes.count} • taper ${taper.done}/${taper.total}.`;
  let action = 'Поддерживайте не только тренировки, но и походную подготовку вокруг них.';

  if (gear.done === gear.total && hikes.count >= 2 && (!taperActive || taper.done >= 2)) {
    tone = 'good';
    title = 'Походный контур выглядит собранным';
    summary = `Снаряжение закрыто, длинные выходы уже есть${hikes.maxPack ? `, рюкзак доходил до ${hikes.maxPack} кг` : ''}.`;
    action = taperActive ? 'Держите taper мягким и не добавляйте новые тяжёлые эксперименты.' : 'Остаётся держать устойчивый ритм тренировок и периодически перепроверять контур.';
  } else if (hikes.count === 0) {
    tone = 'focus';
    title = 'Добавьте реальный длинный выход';
    summary = `Тренировочный план идёт, но в журнале пока нет длинных выходов под походную задачу.`;
    action = 'Запланируйте хотя бы один длинный выход с рюкзаком и зафиксируйте его здесь.';
  } else if (gear.done < Math.ceil(gear.total / 2)) {
    tone = 'focus';
    title = 'Снаряжение ещё не собрано в систему';
    summary = `Закрыто только ${gear.done} из ${gear.total} ключевых пунктов снаряжения.`;
    action = 'Лучший следующий шаг — добрать базовый комплект и проверить его на тренировочном выходе.';
  } else if (taperActive && taper.done < 2) {
    tone = 'warn';
    title = 'Входим в taper-окно';
    summary = `До похода уже близко, а taper-контур отмечен только на ${taper.done}/${taper.total}.`;
    action = 'Сейчас приоритет не в доборе объёма, а в восстановлении, логистике и перепроверке маршрута.';
  }

  const highlights = [];
  if (hikes.count > 0) highlights.push(`Длинных выходов в журнале: ${hikes.count}${hikes.totalHours ? ` • суммарно ~${hikes.totalHours} ч` : ''}.`);
  if (hikes.maxPack) highlights.push(`Максимальный зафиксированный вес рюкзака: ${hikes.maxPack} кг.`);
  if (!hikes.maxPack && hikes.count > 0) highlights.push('Для длинных выходов полезно фиксировать вес рюкзака, чтобы видеть приближение к походной нагрузке.');
  if (gear.done < gear.total) highlights.push(`Осталось добрать ${gear.total - gear.done} пунктов снаряжения.`);
  if (taperActive && taper.done < taper.total) highlights.push(`В taper-окне осталось ${taper.total - taper.done} подготовительных пункта.`);

  return {
    tone,
    title,
    summary,
    action,
    highlights,
    stats: [
      { label: 'Снаряжение', value: `${gear.done}/${gear.total}` },
      { label: 'Выходы', value: `${hikes.count}` },
      hikes.maxPack ? { label: 'Рюкзак', value: `${hikes.maxPack} кг` } : { label: 'Taper', value: `${taper.done}/${taper.total}` }
    ]
  };
}

function toggleGearItem(id, checked) {
  if (checked) state.expedition.gear[id] = true;
  else delete state.expedition.gear[id];
  saveState();
  render();
}

function toggleTaperItem(id, checked) {
  if (checked) state.expedition.taper[id] = true;
  else delete state.expedition.taper[id];
  saveState();
  render();
}

function addLongHike() {
  const date = document.getElementById('hikeLogDate')?.value || '';
  const duration = document.getElementById('hikeLogDuration')?.value || '';
  const pack = document.getElementById('hikeLogPack')?.value || '';
  const note = (document.getElementById('hikeLogNote')?.value || '').trim();
  if (!date || !duration) {
    alert('Укажите дату и длительность выхода.');
    return;
  }
  const durationNum = toMetricNumber(duration);
  if (!durationNum || durationNum <= 0) {
    alert('Длительность выхода должна быть больше нуля.');
    return;
  }
  state.expedition.hikes = [
    {
      date,
      duration: durationNum,
      pack: toMetricNumber(pack) || '',
      note
    },
    ...(state.expedition.hikes || [])
  ]
    .sort((a, b) => String(b.date || '').localeCompare(String(a.date || '')))
    .slice(0, 12);
  saveState();
  render();
}

function removeLongHike(index) {
  state.expedition.hikes = (state.expedition.hikes || []).filter((_, idx) => idx !== index);
  saveState();
  render();
}

// Notes
function saveDayNotes(w, d) {
  const ta = document.getElementById('dayNotes');
  if (!ta) return;
  const key = `w${w}d${d}`;
  if (ta.value.trim()) state.notes[key] = ta.value.trim();
  else delete state.notes[key];
  saveState();
}

// Week skip
function skipWeek() {
  const currentWeek = getCurrentWeekIndex();
  if (currentWeek >= PROGRAM.length - 1) {
    alert('Это последняя неделя плана — дальше сдвигать уже некуда.');
    return;
  }
  if (confirm(`Пропустить текущую активную неделю (${currentWeek + 1})? Следующей станет неделя ${currentWeek + 2}.`)) {
    state.weekOffset = (state.weekOffset || 0) + 1;
    saveState();
    render();
  }
}

// Readiness test per phase

// ============ TIMER ============
let timerInterval = null;
let timerEnd = 0;
let timerTotal = 0;

function startTimer(seconds, label) {
  stopTimer();
  timerTotal = seconds;
  timerEnd = Date.now() + seconds * 1000;
  const overlay = document.getElementById('timer');
  const textEl = document.getElementById('timerText');
  const labelEl = document.getElementById('timerLabel');
  const fillEl = document.getElementById('timerProgressFill');
  overlay.classList.add('active');
  labelEl.textContent = label || 'Отдых';

  timerInterval = setInterval(() => {
    const remaining = Math.max(0, Math.ceil((timerEnd - Date.now()) / 1000));
    const mins = Math.floor(remaining / 60);
    const secs = remaining % 60;
    textEl.textContent = `${mins}:${secs.toString().padStart(2, '0')}`;
    fillEl.style.width = `${(remaining / timerTotal) * 100}%`;
    if (remaining <= 0) {
      stopTimer();
      beep();
    }
  }, 200);
}

function stopTimer() {
  if (timerInterval) clearInterval(timerInterval);
  timerInterval = null;
  document.getElementById('timer').classList.remove('active');
}

function beep() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    [0, 0.2, 0.4].forEach(t => {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.connect(g); g.connect(ctx.destination);
      o.frequency.value = 880;
      g.gain.value = 0.3;
      o.start(ctx.currentTime + t);
      o.stop(ctx.currentTime + t + 0.15);
    });
  } catch(e) {}
}

// ============ EXERCISE IMAGES (free-exercise-db) ============
const IMG_BASE = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/';

// Mapping: exercise keyword patterns → free-exercise-db folder ID
const PHOTO_MAP = [
  [/^болгарские приседания в смите$/i, 'Smith_Single-Leg_Split_Squat'],
  [/^приседания с рюкзаком$/i, 'Bodyweight_Squat'],
  [/присед.*штанг|приседания со штанг|приседания в смит/i, 'Barbell_Squat'],
  [/присед.*гантел|кубков|фронтальн.*присед|goblet/i, 'Goblet_Squat'],
  [/^приседания$/i, 'Bodyweight_Squat'],
  [/^становая на одной ноге(?: с гантелью)?$/i, 'Romanian_Deadlift'],
  [/присед.*полусфер|присед.*собств|присед.*босу|полуприсед/i, 'Bodyweight_Squat'],
  [/^overhead приседания со штангой$/i, 'Front_Barbell_Squat'],
  [/overhead.*присед|оверхэд/i, 'Front_Barbell_Squat'],
  [/болгарск/i, 'Smith_Single-Leg_Split_Squat'],
  [/^выпады$/i, 'Dumbbell_Lunges'],
  [/выпад.*проход|выпад.*гантел|выпад.*вперёд|выпады.*назад|выпад.*рюкзак/i, 'Dumbbell_Lunges'],
  [/выпад.*штанг/i, 'Barbell_Walking_Lunge'],
  [/латеральн.*выпад/i, 'Dumbbell_Lunges'],
  [/румынск.*тяг|румынская/i, 'Romanian_Deadlift'],
  [/станов.*тяг.*штанг|становая/i, 'Barbell_Deadlift'],
  [/good\s?morning|гуд\s?мор/i, 'Good_Morning'],
  [/жим ног|платформ/i, 'Leg_Press'],
  [/разгибание голен|разгибание ног/i, 'Leg_Extensions'],
  [/сгибание ног.*фитбол/i, 'Ball_Leg_Curl'],
  [/сгибание голен|сгибание ног/i, 'Seated_Leg_Curl'],
  [/отведение ног/i, 'Thigh_Abductor'],
  [/сведение ног/i, 'Thigh_Adductor'],
  [/зашагиван|step.*up/i, 'Dumbbell_Step_Ups'],
  [/икроножн|на носки|подъём.*на носк|calf/i, 'Standing_Calf_Raises'],
  [/^жим штанги лёжа$|^жим штанги на горизонтальной(?: скамье)?$/i, 'Barbell_Bench_Press_-_Medium_Grip'],
  [/жим штанг.*леж|жим.*горизонт.*штанг|жим.*узк.*хват/i, 'Barbell_Bench_Press_-_Medium_Grip'],
  [/жим.*наклон.*штанг|жим штанг.*наклон/i, 'Barbell_Incline_Bench_Press_-_Medium_Grip'],
  [/жим.*смит|жим в смит/i, 'Smith_Machine_Bench_Press'],
  [/^жим гантелей на наклонной(?: скамье)?$/i, 'Incline_Dumbbell_Press'],
  [/жим гантел|жим.*горизонт.*гантел/i, 'Dumbbell_Bench_Press'],
  [/жим.*наклон.*гантел|жим гантел.*наклон/i, 'Incline_Dumbbell_Press'],
  [/жим.*хаммер/i, 'Leverage_Chest_Press'],
  [/сведение рук.*кроссовер|кроссовер/i, 'Cable_Crossover'],
  [/^обратная бабочка$/i, 'Reverse_Flyes'],
  [/сведение рук.*тренаж|бабочк|сведение рук/i, 'Butterfly'],
  [/подтягиван.*гравитрон|подтягиван.*амортизат|подтягивания.*резин/i, 'Band_Assisted_Pull-Up'],
  [/подтягиван/i, 'Pullups'],
  [/вертикальн.*тяг|тяга.*верхн.*блок|тяга.*широк.*хват|lat.*pull/i, 'Wide-Grip_Lat_Pulldown'],
  [/^горизонтальная тяга$/i, 'Seated_Cable_Rows'],
  [/горизонтальн.*тяг.*сидя|тяга.*сидя|seated.*row/i, 'Seated_Cable_Rows'],
  [/тяга.*наклон|тяга.*штанг.*обратн|тяга.*гантел.*наклон|тяга.*гантел.*лёж/i, 'Bent_Over_Barbell_Row'],
  [/тяга.*подбородк|тяга.*рейдер/i, 'Upright_Cable_Row'],
  [/отжимания на брусь|брусья|dip/i, 'Dips_-_Triceps_Version'],
  [/обратные отжимания|отжимания.*скамь/i, 'Bench_Dips'],
  [/отжимани/i, 'Pushups'],
  [/жим плеч|армейск.*жим|жим.*сидя.*штанг|жим.*над голов/i, 'Barbell_Shoulder_Press'],
  [/отведени.*рук.*сторон|отведени.*гантел.*сторон/i, 'Side_Lateral_Raise'],
  [/^отведение рук с гантелями в наклоне$/i, 'Reverse_Flyes'],
  [/отведени.*рук.*назад|обратн.*бабочк|reverse.*fly|задн.*дельт/i, 'Reverse_Flyes'],
  [/сгибание рук.*бицепс|бицепс.*штанг|сгибание.*штанг/i, 'Barbell_Curl'],
  [/молот|hammer/i, 'Hammer_Curls'],
  [/сгибание рук.*гантел|бицепс.*гантел/i, 'Concentration_Curls'],
  [/сгибание рук.*блок|бицепс.*блок|бицепс.*кроссовер|бицепс.*trx/i, 'Cable_Hammer_Curls_-_Rope_Attachment'],
  [/французск.*жим|разгибание рук.*головы|skull/i, 'EZ-Bar_Skullcrusher'],
  [/разгибание рук.*блок|трицепс.*блок|tricep.*push/i, 'Triceps_Pushdown_-_Rope_Attachment'],
  [/разгибание.*гантел.*трицепс|разгибание руки.*гантел/i, 'Dumbbell_One-Arm_Triceps_Extension'],
  [/^планка боковая$/i, 'Side_Bridge_-_Hip_Abduction'],
  [/боков.*планк|side.*plank/i, 'Side_Bridge_-_Hip_Abduction'],
  [/динамическ.*планк/i, 'Plank'],
  [/планк.*протяжк|планк.*полотенц/i, 'Ab_Roller'],
  [/планк.*рюкзак/i, 'Plank'],
  [/планк/i, 'Plank'],
  [/скручиван.*фитбол/i, 'Exercise_Ball_Crunch'],
  [/скручиван.*диагон|косы.*скручиван|боков.*скручиван/i, 'Oblique_Crunches_-_On_The_Floor'],
  [/^пресс\s*[—-]\s*обратные скручивания$/i, 'Reverse_Crunch'],
  [/скручиван|ситап|sit.*up|складк/i, 'Crunches'],
  [/обратн.*скручиван/i, 'Reverse_Crunch'],
  [/подъём.*ног.*вис|подъём.*ног.*упор/i, 'Hanging_Leg_Raise'],
  [/ролик.*прес|ab.*roll/i, 'Ab_Roller'],
  [/^пресс\s*[—-]\s*ролик на коленях$/i, 'Ab_Roller'],
  [/русский.*твист|ротаци.*корпус|вращени.*корпус|plate.*twist/i, 'Russian_Twist'],
  [/альпинист|скалолаз|mountain.*climb/i, 'Mountain_Climbers'],
  [/бёрпи|берпи|burpee/i, 'Burpee'],
  [/фермерск.*проходк/i, 'Farmers_Walk'],
  [/гиперэкстензи.*фитбол/i, 'Exercise_Ball_Back_Extension'],
  [/гиперэкстензи|hyperext/i, 'Hyperextensions_Back_Extensions'],
  [/^ходьба$/i, 'Walking_Treadmill'],
  [/^трусца(?: вниз)?$/i, 'Jogging_Treadmill'],
  [/^бег\s*\/\s*ходьба$|^лёгкий бег\s*\/\s*ходьба$/i, 'Jogging_Treadmill'],
  [/^быстрый бег$|^непрерывный бег$|^бег в темпе(?: с горками)?$|^бег в крутой подъём$|^бег в подъём$|^бег с перепадами$|^фартлек$/i, 'Jogging_Treadmill'],
  [/^беговая СБУ$/i, 'Jogging_Treadmill'],
  [/дорожк.*уклон|дорожк/i, 'Walking_Treadmill'],
  [/эллипс/i, 'Elliptical_Trainer'],
  [/гребл|rowing/i, 'Rowing_Stationary'],
  [/аэробайк|байк|велосипед/i, 'Bicycling_Stationary'],
  [/канат.*мах|махи канат|battling/i, 'Battling_Ropes'],
  [/запрыгиван|выпрыгиван|box.*jump/i, 'Box_Jump_Multiple_Response'],
  [/прыжк.*скакалк/i, 'Rope_Jumping'],
  [/турецк.*подъём|turkish/i, 'Kettlebell_Turkish_Get-Up_Lunge_style'],
  [/^взятие гири на грудь$/i, 'One-Arm_Kettlebell_Clean'],
  [/взятие.*гир|гир.*на грудь|гир.*на плечо|kettlebell.*clean/i, 'One-Arm_Kettlebell_Clean'],
  [/рывок.*гантел/i, 'One-Arm_Kettlebell_Snatch'],
  [/стульчик|wall.*sit/i, 'Wall_Squat'],
  [/полусфер|баланс|стойка.*одной|закрыт.*глаз/i, 'Balance_Board'],
  [/растяжк|растяж|stretch/i, 'All_Fours_Quad_Stretch'],
  [/скатыван.*фитбол|roll.*out.*фитбол/i, 'Exercise_Ball_Pull-In'],
  [/валик|раскатк|roll|it-band/i, 'Anterior_Tibialis-SMR'],
  [/разминк|суставн/i, 'Arm_Circles'],
  [/рюкзак|ходьб.*рюкзак|прогулк/i, 'Walking_Treadmill'],
  [/пробежк/i, 'Jogging_Treadmill'],
  [/проходк.*штанг/i, 'Barbell_Walking_Lunge'],
  [/devil.*press|трастер/i, 'Clean_and_Press'],
  [/подкат.*фитбол/i, 'Ball_Leg_Curl'],
  [/лодочк|супермен/i, 'Superman'],
  [/ягодичн.*мост|glute.*bridge/i, 'Barbell_Glute_Bridge'],
  [/часик/i, 'Plate_Twist'],
  [/вращени.*бодибар|вращени.*штанг/i, 'Seated_Barbell_Twist'],
  // Venue substitution exercises
  [/бег на месте|^лёгкий бег$|^бег$/i, 'Jogging_Treadmill'],
  [/jumping.*jack/i, 'Rope_Jumping'],
  [/ходьба.*лестниц/i, 'Walking_Treadmill'],
  [/ходьба.*гору|поход.*гору/i, 'Walking_Treadmill'],
  [/приседания с рюкзаком|присед.*рюкзаком/i, 'Bodyweight_Squat'],
  [/австралийск.*подтягиван/i, 'Pullups'],
  [/подтягивания на турник/i, 'Pullups'],
  [/подтягивания обратн.*хват/i, 'Chin-Up'],
  [/пика.*push|pike.*push|отжимания.*пика/i, 'Pushups'],
  [/алмазн.*отжимани/i, 'Pushups'],
  [/тяга.*эспандер.*поясу/i, 'Seated_Cable_Rows'],
  [/подъёмы рук.*эспандер/i, 'Side_Lateral_Raise'],
  [/сгибание рук.*эспандер/i, 'Barbell_Curl'],
  [/разведение рук.*эспандер/i, 'Reverse_Flyes'],
  [/махи.*рюкзак/i, 'One-Arm_Kettlebell_Clean'],
  [/обратные отжимания.*стул/i, 'Bench_Dips'],
  [/подъёмы ног лёжа/i, 'Flat_Bench_Lying_Leg_Raise'],
  [/чередование лестниц/i, 'Walking_Treadmill'],
  [/зашагиван.*степ/i, 'Dumbbell_Step_Ups'],
  [/^эксцентрические приседания$/i, 'Bodyweight_Squat'],
];

function getExImgId(name) {
  const n = name.toLowerCase();
  for (const [rx, id] of PHOTO_MAP) {
    if (rx.test(n)) return id;
  }
  return null;
}

function escapeAttr(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

const LOCAL_PICTOGRAM_MAP = [
  [/валик|раскатк|it-band/i, { kind: 'recovery', label: 'ROLL' }],
  [/суставн|разминка/i, { kind: 'mobility', label: 'MOB' }],
  [/полуприсед.*полусфер|приседания на полусфере|стойка на полусфере|закрытыми глазами/i, { kind: 'balance', label: 'BAL' }],
  [/^подъём:/i, { kind: 'climb', label: 'UP' }],
  [/^равнина:/i, { kind: 'flat', label: 'FLAT' }],
  [/интервалы:|каждые 10 мин|каждые 15 мин|каждые 20 мин|стоп:|общее время|темп подъёма/i, { kind: 'interval', label: 'INT' }],
  [/тяга гантелей лёжа на наклонной|тяга гантелей на наклонной/i, { kind: 'strength', label: 'PULL' }],
  [/пуловер|лыжн.*эргометр|волбол/i, { kind: 'strength', label: 'SKILL' }],
  [/дорожка.*горный профиль|чередование лестниц/i, { kind: 'climb', label: 'UP' }],
  [/беговая СБУ|бег\s*\/\s*ходьба|лёгкий бег\s*\/\s*ходьба|^трусца(?: вниз)?$|^быстрый бег$|^непрерывный бег$|^бег в темпе(?: с горками)?$|^бег в крутой подъём$|^бег в подъём$|^бег с перепадами$|^фартлек$|^ходьба$|бег.*рюкзак|бег-поход/i, { kind: 'run', label: 'RUN' }]
];

function getLocalPictogramSpec(name) {
  for (const [rx, spec] of LOCAL_PICTOGRAM_MAP) {
    if (rx.test(name)) return spec;
  }
  return null;
}

function getLocalPictogramSvg(kind) {
  switch (kind) {
    case 'run':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="31" cy="10" r="4"></circle>
          <path d="M20 18l7-4 5 4"></path>
          <path d="M18 24l8-2 5 5"></path>
          <path d="M14 37l8-8 6 2"></path>
          <path d="M26 19l-2 9"></path>
          <path d="M28 31l8 6"></path>
        </svg>
      `;
    case 'climb':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <path d="M8 35l10-12 7 8 7-11 8 15"></path>
          <path d="M28 11h9v9"></path>
          <path d="M37 11l-9 9"></path>
        </svg>
      `;
    case 'flat':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <path d="M7 33h34"></path>
          <path d="M11 25l6-6 5 4 6-7 9 9"></path>
          <circle cx="12" cy="33" r="2"></circle>
          <circle cx="36" cy="33" r="2"></circle>
        </svg>
      `;
    case 'interval':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="24" r="14"></circle>
          <path d="M24 16v9l6 4"></path>
          <path d="M34 10l4 4"></path>
          <path d="M14 10l-4 4"></path>
        </svg>
      `;
    case 'mobility':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="10" r="4"></circle>
          <path d="M24 15v11"></path>
          <path d="M24 20l-10 5"></path>
          <path d="M24 20l10 5"></path>
          <path d="M24 26l-8 10"></path>
          <path d="M24 26l8 10"></path>
        </svg>
      `;
    case 'balance':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <path d="M14 33c3-6 17-6 20 0"></path>
          <path d="M24 12v14"></path>
          <path d="M24 16l-6 4"></path>
          <path d="M24 16l6 4"></path>
          <path d="M24 26l-5 8"></path>
          <path d="M24 26l5 8"></path>
        </svg>
      `;
    case 'recovery':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <rect x="9" y="26" width="20" height="8" rx="4"></rect>
          <path d="M29 30h10"></path>
          <path d="M35 24v12"></path>
          <path d="M14 20l6-5 5 3"></path>
        </svg>
      `;
    case 'strength':
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <path d="M10 20v8"></path>
          <path d="M15 17v14"></path>
          <path d="M33 17v14"></path>
          <path d="M38 20v8"></path>
          <path d="M15 24h18"></path>
        </svg>
      `;
    default:
      return `
        <svg class="ex-placeholder-svg" viewBox="0 0 48 48" aria-hidden="true">
          <circle cx="24" cy="24" r="14"></circle>
          <path d="M24 17v14"></path>
          <path d="M17 24h14"></path>
        </svg>
      `;
  }
}

function getExercisePictogramHtml(name) {
  const spec = getLocalPictogramSpec(name);
  if (!spec) return null;
  const safeName = escapeAttr(name);
  return `
    <div class="ex-placeholder ex-placeholder-meta ex-placeholder-${spec.kind}" role="img" aria-label="${safeName}">
      ${getLocalPictogramSvg(spec.kind)}
      <div class="ex-placeholder-label">${spec.label}</div>
    </div>
  `;
}

function getExerciseFallbackHtml(name) {
  const pictogram = getExercisePictogramHtml(name);
  if (pictogram) return pictogram;
  const label = String(name || '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map(word => word[0]?.toUpperCase() || '')
    .join('') || 'TR';
  const safeName = escapeAttr(name);
  return `
    <div class="ex-placeholder" role="img" aria-label="${safeName}">
      <div class="ex-placeholder-icon">🥾</div>
      <div class="ex-placeholder-label">${label}</div>
    </div>
  `;
}

function handleExerciseImgError(img, name) {
  const wrap = img.closest('.ex-img');
  if (!wrap) return;
  wrap.innerHTML = getExerciseFallbackHtml(name);
}

function getExImgHtml(name) {
  const pictogram = getExercisePictogramHtml(name);
  if (pictogram) return pictogram;
  const id = getExImgId(name);
  if (!id) return getExerciseFallbackHtml(name);
  const url0 = IMG_BASE + id + '/0.jpg';
  const url1 = IMG_BASE + id + '/1.jpg';
  const safeName = name.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
  return `<img src="${url0}" alt="" loading="lazy" data-img0="${url0}" data-img1="${url1}" data-frame="0" onerror="handleExerciseImgError(this,'${safeName}')" onclick="openLightbox(this,'${safeName}')">`;
}

// Lightbox
let lbState = { img0: '', img1: '', view: 'both' };

function openLightbox(img, title) {
  lbState.img0 = img.dataset.img0;
  lbState.img1 = img.dataset.img1;
  lbState.view = 'both';
  document.getElementById('lbTitle').textContent = title;
  renderLbImgs();
  // Reset toggle buttons
  document.querySelectorAll('#lbToggle button').forEach((b,i) => b.classList.toggle('active', i===0));
  document.getElementById('lightbox').classList.add('active');
  document.body.style.overflow = 'hidden';
}

function closeLightbox(e) {
  if (e && e.target !== e.currentTarget && !e.target.classList.contains('lightbox-close')) return;
  document.getElementById('lightbox').classList.remove('active');
  document.body.style.overflow = '';
}

function showLbView(view) {
  lbState.view = view;
  renderLbImgs();
  document.querySelectorAll('#lbToggle button').forEach(b => {
    b.classList.toggle('active', b.textContent === (view==='both'?'Оба':view==='start'?'Старт':'Финиш'));
  });
}

function renderLbImgs() {
  const c = document.getElementById('lbImgs');
  if (lbState.view === 'both') {
    c.innerHTML = `<img src="${lbState.img0}" alt="Начальная позиция"><img src="${lbState.img1}" alt="Конечная позиция">`;
  } else if (lbState.view === 'start') {
    c.innerHTML = `<img src="${lbState.img0}" alt="Начальная позиция" style="max-width:85%">`;
  } else {
    c.innerHTML = `<img src="${lbState.img1}" alt="Конечная позиция" style="max-width:85%">`;
  }
}

// ============ VENUE SUBSTITUTIONS ============

function getDayData(w, d) {
  const venue = getDayVenue(w, d);
  if (venue === 'run' && RUN_PROGRAM[w] && RUN_PROGRAM[w][d]) {
    const rp = RUN_PROGRAM[w][d];
    return {
      day: PROGRAM[w][d].day,
      focus: rp.focus,
      blocks: rp.blocks,
      isOverride: true
    };
  }
  return { ...PROGRAM[w][d], isOverride: false };
}

// ============ RENDER ============
const app = document.getElementById('app');

function navigate(screen, weekIdx, dayIdx) {
  state.screen = screen;
  if (weekIdx !== undefined) state.weekIdx = weekIdx;
  if (dayIdx !== undefined) state.dayIdx = dayIdx;
  state.openBlocks = {};
  saveState();
  render();
  window.scrollTo(0, 0);
}

function render() {
  if (state.screen === 'program') renderProgram();
  else if (state.screen === 'week') renderWeek();
  else if (state.screen === 'day') renderDay();
}

function renderWeekPreviewCard(wIdx, opts = {}) {
  const phase = PHASES[getPhase(wIdx)];
  const meta = getWeekMeta(wIdx);
  const wp = meta.progress;
  const wpct = wp.total ? Math.round(wp.done / wp.total * 100) : 0;
  const isDeload = (wIdx % 4 === 3);
  const statusLabel = wp.done === wp.total ? 'Готово' : `${wp.done}/${wp.total}`;
  return `
    <button class="card tap-card fade-in" type="button" onclick="navigate('week',${wIdx})" style="${opts.emphasis ? `border-color:${phase.color};box-shadow:0 16px 28px rgba(37,87,65,.14)` : ''}">
      <div class="card-body">
        <div class="card-top">
          <div>
            <div class="card-title">Неделя ${wIdx + 1}${isDeload ? ' • разгрузка' : ''}</div>
            <div class="card-meta">${getWeekFocusSummary(wIdx)}</div>
          </div>
          <div class="card-badge" style="background:${phase.color};color:#fff">${statusLabel}</div>
        </div>
        <div class="card-foot">
          <div class="week-note">${meta.workBlockCount} блока • ${meta.workExerciseCount} упр.</div>
          <div class="week-note">${phase.name}</div>
        </div>
        <div class="card-progress"><div class="card-progress-fill" style="width:${wpct}%;background:${phase.color}"></div></div>
      </div>
    </button>
  `;
}

function renderProgram() {
  const tp = getTotalProgress();
  const pct = tp.total ? Math.round(tp.done / tp.total * 100) : 0;
  const nt = getNextTraining();
  const currentWeekIdx = getCurrentWeekIndex();
  const currentPhaseIdx = getCurrentPhaseIndex();
  const currentPhase = PHASES[currentPhaseIdx];
  const currentWeekMeta = getWeekMeta(currentWeekIdx);
  const visibleWeeks = getVisiblePhaseWeeks(currentPhaseIdx);
  const nextPhaseIdx = currentPhaseIdx < PHASES.length - 1 ? currentPhaseIdx + 1 : null;
  const hikeDays = getHikeCountdown();
  const streak = getStreak();
  const daysShort = ['Вт','Чт','Сб'];
  const recentSessions = getRecentSessions(4);
  const currentPhaseProgress = getPhaseProgress(currentPhaseIdx);
  const nextReadinessInsight = nextPhaseIdx !== null ? getReadinessInsight(nextPhaseIdx) : null;
  const currentWeekReview = getWeekReview(currentWeekIdx);
  const expeditionInsight = getExpeditionInsight();
  const gearProgress = getExpeditionGearProgress();
  const longHikeSummary = getLongHikeSummary();
  const taperActive = isTaperWindow();
  const taperProgress = getTaperProgress();

  let html = `
    <div class="screen">
      <div class="header">
        <div class="header-copy">
          <h1>Горный тренер</h1>
          <div class="header-sub">Подготовка к походу • 16 недель • маршрут силы, выносливости и горной специфики</div>
          <div class="header-meta">
            <div class="pill pill-soft">${pct}% плана выполнено</div>
            <div class="pill pill-soft">Фаза ${currentPhaseIdx + 1}: ${currentPhase.name}</div>
            ${streak >= 2 ? `<div class="streak-badge">🔥 ${streak} подряд</div>` : ''}
          </div>
        </div>
        <div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:${pct}%"></div></div>
      </div>
      <div class="section">
  `;

  if (nt) {
    const nextDay = getDayData(nt.w, nt.d);
    const nextProgress = getDayExCount(nt.w, nt.d);
    html += `
      <button class="surface tap-card hero-card fade-in" type="button" onclick="navigate('day',${nt.w},${nt.d})">
        <div class="card-body">
          <div class="hero-label">Следующая тренировка</div>
          <div class="hero-title">Неделя ${nt.w + 1}, ${daysShort[nt.d]} — ${nextDay.focus}</div>
          <div class="hero-sub">${VENUE_LABELS[getDayVenue(nt.w, nt.d)]} • ${nextProgress.done}/${nextProgress.total} выполнено • откройте и продолжайте прямо с нужного блока</div>
          <div class="hero-grid">
            <div class="hero-mini">
              <div class="hero-mini-label">Маршрут</div>
              <div class="hero-mini-value">${currentPhase.name}</div>
            </div>
            <div class="hero-mini">
              <div class="hero-mini-label">Формат</div>
              <div class="hero-mini-value">${VENUE_LABELS[getDayVenue(nt.w, nt.d)]}</div>
            </div>
          </div>
          <div class="hero-footer">
            <div class="hero-badge pill-soft">${nextProgress.total} упражнений в дне</div>
            <div class="hero-link">Открыть тренировку →</div>
          </div>
        </div>
      </button>
    `;
  } else {
    html += `
      <div class="surface hero-card fade-in">
        <div class="card-body">
          <div class="hero-label">План завершён</div>
          <div class="hero-title">Все 16 недель отмечены</div>
          <div class="hero-sub">Можно сохранить план в календарь, скорректировать профиль тренировок или начать цикл заново.</div>
        </div>
      </div>
    `;
  }

  html += `
      </div>
      <div class="section">
        <div class="overview-grid">
          <div class="surface mini-stat fade-in">
            <div class="mini-stat-label">Текущая неделя</div>
            <div class="mini-stat-value">Неделя ${currentWeekIdx + 1}</div>
            <div class="mini-stat-note">${currentWeekMeta.workBlockCount} блока • ${currentWeekMeta.workExerciseCount} упр.</div>
          </div>
          <div class="surface mini-stat fade-in">
            <div class="mini-stat-label">Текущая фаза</div>
            <div class="mini-stat-value">Фаза ${currentPhaseIdx + 1}</div>
            <div class="mini-stat-note">${currentPhase.name}</div>
          </div>
          <div class="surface mini-stat fade-in">
            <div class="mini-stat-label">Готовность</div>
            <div class="mini-stat-value">${tp.done}/${tp.total}</div>
            <div class="mini-stat-note">Всего выполнено в плане</div>
          </div>
        </div>
      </div>
      <div class="section">
        <div class="analytics-grid">
          <div class="surface analytics-card coach-card coach-${currentWeekReview.tone} fade-in">
            <div class="analytics-head">
              <div>
                <div class="analytics-title">Фокус на сейчас</div>
                <div class="analytics-note">${currentWeekReview.title}</div>
              </div>
              <div class="coach-status">${currentWeekReview.stats[0].value}</div>
            </div>
            <div class="coach-summary">${currentWeekReview.summary}</div>
            <div class="coach-kpis">
              ${currentWeekReview.stats.map(stat => `
                <div class="coach-kpi">
                  <div class="coach-kpi-label">${stat.label}</div>
                  <div class="coach-kpi-value">${stat.value}</div>
                </div>
              `).join('')}
            </div>
            <div class="coach-action">${currentWeekReview.action}</div>
            ${currentWeekReview.highlights.length ? `
              <div class="coach-list">
                ${currentWeekReview.highlights.map(item => `<div class="coach-list-item">${item}</div>`).join('')}
              </div>
            ` : ''}
          </div>
          <div class="surface analytics-card fade-in">
            <div class="analytics-head">
              <div>
                <div class="analytics-title">Пульс текущей фазы</div>
                <div class="analytics-note">Видно, как распределяется выполнение по неделям внутри активной фазы.</div>
              </div>
              <div class="card-badge">${currentPhaseProgress.completedDays}/12 дней</div>
            </div>
            <div class="load-bars">
              ${Array.from({ length: 4 }, (_, idx) => currentPhaseIdx * 4 + idx).map(weekIdx => {
                const wp = getWeekProgress(weekIdx);
                const wpct = wp.total ? Math.round(wp.done / wp.total * 100) : 0;
                return `
                  <div class="load-bar">
                    <div class="load-bar-label">Нед. ${weekIdx + 1}</div>
                    <div class="load-track"><div class="load-fill" style="height:${Math.max(wpct, 12)}%"></div></div>
                    <div class="load-value">${wpct}%</div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>
          ${nextReadinessInsight ? `
            <div class="surface analytics-card coach-card coach-${nextReadinessInsight.tone} fade-in">
              <div class="analytics-head">
                <div>
                  <div class="analytics-title">Готовность к следующей фазе</div>
                  <div class="analytics-note">${nextReadinessInsight.title}</div>
                </div>
                <div class="coach-status">${nextReadinessInsight.stats[0].value}</div>
              </div>
              <div class="coach-summary">${nextReadinessInsight.summary}</div>
              <div class="coach-kpis">
                ${nextReadinessInsight.stats.map(stat => `
                  <div class="coach-kpi">
                    <div class="coach-kpi-label">${stat.label}</div>
                    <div class="coach-kpi-value">${stat.value}</div>
                  </div>
                `).join('')}
              </div>
              <div class="coach-action">${nextReadinessInsight.action}</div>
              ${nextReadinessInsight.remaining.length ? `
                <div class="coach-list">
                  ${nextReadinessInsight.remaining.map(item => `<div class="coach-list-item">${item}</div>`).join('')}
                </div>
              ` : ''}
            </div>
          ` : ''}
          <div class="surface analytics-card fade-in">
            <div class="analytics-head">
              <div>
                <div class="analytics-title">Последние сессии</div>
                <div class="analytics-note">Короткая история с длительностью и ключевыми метриками дня.</div>
              </div>
            </div>
            <div class="session-list">
              ${recentSessions.length ? recentSessions.map(session => `
                <div class="session-item">
                  <div class="session-copy">
                    <div class="session-title">Неделя ${session.w + 1}, ${daysShort[session.d]} — ${session.focus}</div>
                    <div class="session-meta">${session.date || '—'} • RPE ${session.metrics.rpe || '—'} • пульс ${session.metrics.pulse || '—'} • рюкзак ${session.metrics.pack || '—'}</div>
                  </div>
                  <div class="session-side">${session.duration > 0 ? `~${session.duration} мин` : 'без времени'}</div>
                </div>
              `).join('') : `<div class="analytics-note">История появится после первых отмеченных тренировок.</div>`}
            </div>
          </div>
        </div>
      </div>
      <div class="section">
        <div class="surface analytics-card coach-card coach-${expeditionInsight.tone} fade-in">
          <div class="analytics-head">
            <div>
              <div class="analytics-title">Походный контур</div>
              <div class="analytics-note">${expeditionInsight.title}</div>
            </div>
            <div class="coach-status">${expeditionInsight.stats[0].value}</div>
          </div>
          <div class="coach-summary">${expeditionInsight.summary}</div>
          <div class="coach-kpis">
            ${expeditionInsight.stats.map(stat => `
              <div class="coach-kpi">
                <div class="coach-kpi-label">${stat.label}</div>
                <div class="coach-kpi-value">${stat.value}</div>
              </div>
            `).join('')}
          </div>
          <div class="coach-action">${expeditionInsight.action}</div>
          ${expeditionInsight.highlights.length ? `
            <div class="coach-list">
              ${expeditionInsight.highlights.map(item => `<div class="coach-list-item">${item}</div>`).join('')}
            </div>
          ` : ''}
          <details class="service-details expedition-details" ${longHikeSummary.count === 0 ? 'open' : ''}>
            <summary>Снаряжение, длинные выходы и taper</summary>
            <div class="service-body expedition-body">
              <div class="service-phase expedition-panel">
                <div class="service-phase-head">
                  <h4>Чеклист снаряжения</h4>
                  <div class="card-badge" style="background:rgba(36,64,49,.08);color:var(--text)">${gearProgress.done}/${gearProgress.total}</div>
                </div>
                <div class="readiness-list">
                  ${EXPEDITION_GEAR.map(item => `
                    <label class="readiness-item">
                      <input type="checkbox" ${state.expedition.gear[item.id] ? 'checked' : ''} onchange="toggleGearItem('${item.id}',this.checked)">
                      <span>${item.label}</span>
                    </label>
                  `).join('')}
                </div>
              </div>
              <div class="service-phase expedition-panel">
                <div class="service-phase-head">
                  <h4>Журнал длинных выходов</h4>
                  <div class="card-badge" style="background:rgba(36,64,49,.08);color:var(--text)">${longHikeSummary.count} записей</div>
                </div>
                <div class="expedition-log-form">
                  <div class="expedition-field">
                    <label for="hikeLogDate">Дата</label>
                    <input id="hikeLogDate" type="date" value="${new Date().toISOString().slice(0,10)}">
                  </div>
                  <div class="expedition-field">
                    <label for="hikeLogDuration">Часы</label>
                    <input id="hikeLogDuration" type="number" min="0.5" step="0.5" placeholder="2.5">
                  </div>
                  <div class="expedition-field">
                    <label for="hikeLogPack">Рюкзак</label>
                    <input id="hikeLogPack" type="number" min="0" step="0.5" placeholder="8">
                  </div>
                </div>
                <div class="expedition-field">
                  <label for="hikeLogNote">Краткая заметка</label>
                  <input id="hikeLogNote" type="text" placeholder="рельеф, самочувствие, обувь, вода">
                </div>
                <button class="cal-btn expedition-add-btn" type="button" onclick="addLongHike()">Добавить длинный выход</button>
                <div class="session-list">
                  ${longHikeSummary.hikes.length ? longHikeSummary.hikes.slice(0, 4).map((hike, index) => `
                    <div class="session-item expedition-log-item">
                      <div class="session-copy">
                        <div class="session-title">${escapeHtml(hike.date)} • ${hike.duration} ч${hike.pack ? ` • ${hike.pack} кг` : ''}</div>
                        <div class="session-meta">${hike.note ? escapeHtml(hike.note) : 'Без заметки'}</div>
                      </div>
                      <button class="expedition-remove-btn" type="button" onclick="removeLongHike(${index})">Удалить</button>
                    </div>
                  `).join('') : `<div class="analytics-note">Пока нет ни одного длинного выхода. Добавьте хотя бы один тестовый поход с рюкзаком.</div>`}
                </div>
              </div>
              <div class="service-phase expedition-panel">
                <div class="service-phase-head">
                  <h4>Taper перед походом</h4>
                  <div class="card-badge" style="background:rgba(36,64,49,.08);color:var(--text)">${taperProgress.done}/${taperProgress.total}</div>
                </div>
                ${taperActive ? `
                  <div class="readiness-list">
                    ${TAPER_CHECKLIST.map(item => `
                      <label class="readiness-item">
                        <input type="checkbox" ${state.expedition.taper[item.id] ? 'checked' : ''} onchange="toggleTaperItem('${item.id}',this.checked)">
                        <span>${item.label}</span>
                      </label>
                    `).join('')}
                  </div>
                  <div class="countdown-detail">${taperProgress.done === taperProgress.total ? 'Taper-контур закрыт: остаётся держать восстановление и не добавлять лишнюю нагрузку.' : 'Сейчас важнее восстановление, логистика и контроль деталей, чем добор объёма.'}</div>
                ` : `
                  <div class="analytics-note">Taper-чеклист станет основным ближе к последним 2 неделям плана или когда до похода останется до 14 дней.</div>
                `}
              </div>
            </div>
          </details>
        </div>
      </div>
      <div class="section">
        <div class="countdown-card fade-in">
          <div class="countdown-top">
            <div>
              <div class="countdown-label">Обратный отсчёт до похода</div>
              <div class="countdown-detail">Дата нужна только для контекста и мотивации</div>
            </div>
            <div class="countdown-date"><input type="date" value="${state.hikeDate||''}" onchange="setHikeDate(this.value)"></div>
          </div>
          ${hikeDays !== null ? `
            <div class="countdown-num">${hikeDays > 0 ? hikeDays : (hikeDays === 0 ? 'Сегодня' : Math.abs(hikeDays))}</div>
            <div class="countdown-detail">
              ${hikeDays > 0 ? `${hikeDays} ${hikeDays===1?'день':hikeDays<5?'дня':'дней'} до старта` : hikeDays === 0 ? 'Сегодня поход' : `${Math.abs(hikeDays)} ${Math.abs(hikeDays)===1?'день':'дней'} после даты похода`}
              ${nt && hikeDays > 0 ? ` • ориентир: ещё ~${Math.min(Math.floor(hikeDays/7)*3 + Math.min(hikeDays%7,3), 48 - tp.done)} тренировок` : ''}
            </div>
          ` : `<div class="countdown-detail" style="margin-top:10px">Укажите дату, чтобы видеть, сколько времени осталось на подготовку.</div>`}
        </div>
      </div>
      <div class="profile-wrap">
        <div class="profile-label">Формат тренировок</div>
        <div class="profile-cards">
          ${PROFILES.map(p => `
            <button class="profile-card ${state.profile===p.id?'active':''}" type="button" onclick="setProfile('${p.id}')">
              <div class="pf-icon">${p.icon}</div>
              <div class="pf-info">
                <div class="pf-name">${p.name}</div>
                <div class="pf-desc">${p.desc}</div>
              </div>
              <div class="pf-check">${state.profile===p.id?'✓':''}</div>
            </button>
          `).join('')}
        </div>
      </div>
      <div class="phase-section">
        <div class="surface phase-shell fade-in">
          <div class="phase-header">
            <div class="phase-info">
              <div class="phase-dot" style="background:${currentPhase.color}"></div>
              <div>
                <div class="phase-name">Сейчас: Фаза ${currentPhaseIdx + 1} — ${currentPhase.name}</div>
                <div class="phase-desc">${currentPhase.desc}</div>
              </div>
            </div>
            <div class="card-badge" style="background:${currentPhase.color};color:#fff">${currentWeekIdx + 1} неделя</div>
          </div>
          <div class="week-preview-list">
            ${visibleWeeks.map(w => renderWeekPreviewCard(w, { emphasis: w === currentWeekIdx })).join('')}
          </div>
          <div class="phase-actions">
            <button class="phase-toggle ${uiState.expandedPhaseWeeks[currentPhaseIdx] ? '' : 'primary'}" type="button" onclick="toggleHomePhaseWeeks(${currentPhaseIdx})">
              ${uiState.expandedPhaseWeeks[currentPhaseIdx] ? 'Показать только ближайшие недели' : 'Показать всю фазу'}
            </button>
            <button class="phase-toggle" type="button" onclick="skipWeek()">
              Пропустить текущую неделю
            </button>
          </div>
        </div>
      </div>
  `;

  if (nextPhaseIdx !== null) {
    const nextPhase = PHASES[nextPhaseIdx];
    const nextExpanded = !!uiState.expandedPhases[nextPhaseIdx];
    html += `
      <div class="phase-section">
        <div class="surface phase-shell fade-in">
          <div class="phase-header">
            <div class="phase-info">
              <div class="phase-dot" style="background:${nextPhase.color}"></div>
              <div>
                <div class="phase-name">Дальше: Фаза ${nextPhaseIdx + 1} — ${nextPhase.name}</div>
                <div class="phase-desc">${nextPhase.desc}</div>
              </div>
            </div>
          </div>
          ${nextExpanded ? `
            <div class="week-preview-list">
              ${Array.from({ length: 4 }, (_, i) => nextPhaseIdx * 4 + i).map(w => renderWeekPreviewCard(w)).join('')}
            </div>
          ` : ''}
          <div class="phase-actions">
            <button class="phase-toggle" type="button" onclick="toggleHomePhase(${nextPhaseIdx})">
              ${nextExpanded ? 'Свернуть следующую фазу' : 'Развернуть следующую фазу'}
            </button>
          </div>
        </div>
      </div>
    `;
  }

  html += `
      <div class="section">
        <div class="surface utility-card fade-in">
          <div class="section-head">
            <div>
              <div class="section-title">Сервисные действия</div>
              <div class="section-sub">Вспомогательные функции вынесены вниз, чтобы не мешать ежедневной навигации.</div>
            </div>
          </div>
          <div class="utility-grid">
            <div class="cal-wrap">
              <div class="cal-date-row">
                <label for="calStart">Старт плана</label>
                <input type="date" id="calStart" value="${state.cal.start || new Date().toISOString().slice(0,10)}" onchange="setCalendarStart(this.value)">
              </div>
              <div class="cal-settings">
                <div class="cal-setting">
                  <label for="calTueTime">Вт</label>
                  <input type="time" id="calTueTime" value="${state.cal.times[0]}" onchange="setCalendarTime(0,this.value)">
                </div>
                <div class="cal-setting">
                  <label for="calThuTime">Чт</label>
                  <input type="time" id="calThuTime" value="${state.cal.times[1]}" onchange="setCalendarTime(1,this.value)">
                </div>
                <div class="cal-setting">
                  <label for="calSatTime">Сб</label>
                  <input type="time" id="calSatTime" value="${state.cal.times[2]}" onchange="setCalendarTime(2,this.value)">
                </div>
                <div class="cal-setting">
                  <label for="calDuration">Мин</label>
                  <input type="number" id="calDuration" min="30" step="15" value="${state.cal.duration}" onchange="setCalendarDuration(this.value)">
                </div>
              </div>
              <button class="cal-btn" type="button" onclick="exportCalendar()">📅 Добавить 48 тренировок в календарь</button>
            </div>
            <div class="reset-wrap">
              <button class="reset-btn" type="button" onclick="resetProgress()">Сбросить прогресс</button>
            </div>
          </div>
          <details class="service-details">
            <summary>Контрольные ориентиры по фазам</summary>
            <div class="service-body">
              ${READINESS_TESTS.map((tests, idx) => tests ? `
                <div class="service-phase">
                  <div class="service-phase-head">
                    <h4>Перед Фазой ${idx + 1}</h4>
                    <div class="card-badge" style="background:rgba(36,64,49,.08);color:var(--text)">${getReadinessProgress(idx).done}/${getReadinessProgress(idx).total}</div>
                  </div>
                  <div class="readiness-list">
                    ${tests.map((test, testIdx) => `
                      <label class="readiness-item">
                        <input type="checkbox" ${state.readiness[readinessId(idx, testIdx)] ? 'checked' : ''} onchange="setReadinessCheck(${idx},${testIdx},this.checked)">
                        <span>${test}</span>
                      </label>
                    `).join('')}
                  </div>
                  <div class="countdown-detail">${getReadinessProgress(idx).done === getReadinessProgress(idx).total ? 'Все ориентиры выполнены.' : 'Отмечайте пункты по мере готовности.'}</div>
                </div>
              ` : '').join('')}
            </div>
          </details>
        </div>
      </div>
    </div>
  `;

  app.innerHTML = html;
}

function renderWeek() {
  const w = state.weekIdx;
  const phase = PHASES[getPhase(w)];
  const weekMeta = getWeekMeta(w);
  const wp = weekMeta.progress;
  const wpct = wp.total ? Math.round(wp.done / wp.total * 100) : 0;
  const nextTraining = getNextTraining();
  const daysFull = ['Вторник', 'Четверг', 'Суббота'];
  const totalDuration = getWeekMetricSummary(w).totalDuration;
  const weekReview = getWeekReview(w);

  let html = `
    <div class="screen">
      <div class="header header-compact">
        <div class="header-bar">
          <button class="back-btn" type="button" onclick="navigate('program')">&larr;</button>
          <div class="header-copy">
            <h1>Неделя ${w + 1}</h1>
            <div class="header-sub">${phase.name} • ${wp.done}/${wp.total} упражнений выполнено</div>
            <div class="header-meta">
              <div class="pill pill-soft">${weekMeta.workBlockCount} блока</div>
              <div class="pill pill-soft">${weekMeta.workExerciseCount} рабочих упражнений</div>
            </div>
          </div>
        </div>
        <div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:${wpct}%"></div></div>
      </div>
      <div class="week-nav">
        <button class="week-nav-btn" type="button" ${w===0?'disabled':''} onclick="navigate('week',${w-1})">&larr; Нед. ${w}</button>
        <div class="week-nav-title">Неделя ${w + 1}/16</div>
        <button class="week-nav-btn" type="button" ${w===15?'disabled':''} onclick="navigate('week',${w+1})">Нед. ${w + 2} &rarr;</button>
      </div>
      <div class="week-summary">
        <div class="surface week-summary-card fade-in">
          <div class="week-summary-top">
            <div class="week-summary-copy">
              <div class="week-summary-title">${phase.name}</div>
              <div class="week-summary-desc">${phase.desc}${totalDuration ? ` • ~${totalDuration} мин активности на этой неделе` : ''}</div>
            </div>
            <div class="card-badge" style="background:${phase.color};color:#fff">${wpct}%</div>
          </div>
          ${w === getCurrentWeekIndex() && w < PROGRAM.length - 1 ? `
            <div class="phase-actions" style="margin-top:12px">
              <button class="phase-toggle" type="button" onclick="skipWeek()">Пропустить эту неделю</button>
            </div>
          ` : ''}
          <div class="week-strip">
            ${weekMeta.daySummaries.map((summary, di) => {
              const done = summary.progress.done === summary.progress.total;
              const current = nextTraining && nextTraining.w === w && nextTraining.d === di;
              const statusLabel = done ? 'Готово' : current ? 'Дальше' : 'По плану';
              return `
                <div class="week-strip-item ${done ? 'done' : ''} ${current ? 'current' : ''}">
                  <div class="week-strip-day">${daysFull[di]}</div>
                  <div class="week-strip-focus">${summary.day.focus}</div>
                  <div class="week-strip-status">${statusLabel}</div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
      <div class="section">
        <div class="surface analytics-card coach-card coach-${weekReview.tone} fade-in">
          <div class="analytics-head">
            <div>
              <div class="analytics-title">Обзор недели</div>
              <div class="analytics-note">${weekReview.title}</div>
            </div>
            <div class="coach-status">${weekReview.stats[0].value}</div>
          </div>
          <div class="coach-summary">${weekReview.summary}</div>
          <div class="coach-kpis">
            ${weekReview.stats.map(stat => `
              <div class="coach-kpi">
                <div class="coach-kpi-label">${stat.label}</div>
                <div class="coach-kpi-value">${stat.value}</div>
              </div>
            `).join('')}
          </div>
          <div class="coach-action">${weekReview.action}</div>
          ${weekReview.highlights.length ? `
            <div class="coach-list">
              ${weekReview.highlights.map(item => `<div class="coach-list-item">${item}</div>`).join('')}
            </div>
          ` : ''}
        </div>
      </div>
      <div class="day-cards">
  `;

  weekMeta.daySummaries.forEach((summary, di) => {
    const dpct = summary.progress.total ? Math.round(summary.progress.done / summary.progress.total * 100) : 0;
    const done = summary.progress.done === summary.progress.total;
    const current = nextTraining && nextTraining.w === w && nextTraining.d === di;
    const venue = getDayVenue(w, di);
    html += `
      <button class="day-card tap-card fade-in ${done ? 'done' : ''} ${current ? 'current' : ''}" type="button" onclick="navigate('day',${w},${di})">
        <div class="day-card-top">
          <div>
            <div class="day-card-title">${daysFull[di]}</div>
            <div class="day-card-focus">${summary.day.focus}</div>
          </div>
          <div class="day-card-status ${done ? 'done' : current ? 'current' : ''}">
            ${done ? 'Готово' : current ? 'Следующая' : `${summary.progress.done}/${summary.progress.total}`}
          </div>
        </div>
        <div class="day-card-meta">
          <div class="day-meta-chip">${getDayVenueIcon(venue)} ${VENUE_LABELS[venue]}</div>
          <div class="day-meta-chip">${summary.workBlockCount} блока</div>
          <div class="day-meta-chip">${summary.workExerciseCount} упр.</div>
        </div>
        <div class="day-card-exercises">${summary.day.blocks.filter(b => b.type !== 'warmup' && b.type !== 'cooldown').map(b => b.name).join(' • ')}</div>
        <div class="card-progress"><div class="card-progress-fill" style="width:${dpct}%;background:${phase.color}"></div></div>
      </button>
    `;
  });

  html += `</div>`;

  const weekHists = [0,1,2].map(di => state.history[`w${w}d${di}`]).filter(Boolean);
  if (weekHists.length > 0) {
    html += `
      <div class="history-wrap">
        <div class="surface history-card">
          <div class="history-title">История недели</div>
          <div class="history-list">
            ${weekHists.map(h => {
              const dur = h.endMs && h.startMs ? Math.round((h.endMs - h.startMs) / 60000) : 0;
              return `<div class="history-item"><span>${h.date || '—'}</span><span>${dur > 0 ? `~${dur} мин` : 'Без длительности'}</span></div>`;
            }).join('')}
          </div>
        </div>
      </div>
    `;
  }

  html += `</div>`;
  app.innerHTML = html;
}

function renderDay() {
  const w = state.weekIdx;
  const d = state.dayIdx;
  const day = getDayData(w, d);
  const venue = getDayVenue(w, d);
  const phase = PHASES[getPhase(w)];
  const dc = getDayExCount(w, d);
  const dpct = dc.total ? Math.round(dc.done / dc.total * 100) : 0;
  const allDone = dpct === 100;
  const daysFull = ['Вторник', 'Четверг', 'Суббота'];
  const workBlocks = day.blocks.filter(block => block.type !== 'warmup' && block.type !== 'cooldown');
  const nextExercise = getFirstIncompleteExercise(w, d);
  const metrics = getDayMetrics(w, d);

  let html = `
    <div class="screen">
      <div class="header header-compact">
        <div class="header-bar">
          <button class="back-btn" type="button" onclick="navigate('week',${w})">&larr;</button>
          <div class="header-copy">
            <h1>Неделя ${w + 1}, ${daysFull[d]}</h1>
            <div class="header-sub">${dc.done}/${dc.total} упражнений выполнено • ${VENUE_LABELS[venue]}</div>
          </div>
        </div>
        <div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:${dpct}%"></div></div>
      </div>
      <div class="day-header">
        <div class="surface day-summary fade-in">
          <div class="day-summary-copy">
            <div>
              <div class="day-title">${day.focus}</div>
              <div class="day-focus-tag" style="background:${phase.color};color:#fff">Фаза ${getPhase(w) + 1}: ${phase.name}</div>
            </div>
            <div class="card-badge" style="background:${phase.color};color:#fff">${dpct}%</div>
          </div>
          <div class="day-summary-grid">
            <div class="day-summary-item">
              <div class="day-summary-label">Формат</div>
              <div class="day-summary-value">${getDayVenueIcon(venue)} ${VENUE_LABELS[venue]}</div>
            </div>
            <div class="day-summary-item">
              <div class="day-summary-label">Рабочие блоки</div>
              <div class="day-summary-value">${workBlocks.length} блока</div>
            </div>
            <div class="day-summary-item">
              <div class="day-summary-label">Объём</div>
              <div class="day-summary-value">${dc.total} упражнений</div>
            </div>
            <div class="day-summary-item">
              <div class="day-summary-label">Следующее</div>
              <div class="day-summary-value">${nextExercise ? 'Продолжить тренировку' : 'День завершён'}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="venue-wrap">
        <div class="surface-muted">
          <div class="venue-label">Место тренировки</div>
          <div class="venue-tabs">
            <button class="venue-tab ${venue==='gym'?'active':''}" type="button" onclick="setDayVenue(${w},${d},'gym')"><span class="venue-icon">🏋️</span>Зал</button>
            <button class="venue-tab ${venue==='home'?'active':''}" type="button" onclick="setDayVenue(${w},${d},'home')"><span class="venue-icon">🏠</span>Дома</button>
            <button class="venue-tab ${venue==='outdoor'?'active':''}" type="button" onclick="setDayVenue(${w},${d},'outdoor')"><span class="venue-icon">🌲</span>Улица</button>
            <button class="venue-tab ${venue==='run'?'active':''}" type="button" onclick="setDayVenue(${w},${d},'run')"><span class="venue-icon">🏃</span>Бег</button>
          </div>
          ${VENUE_EQUIP[venue] ? `<div class="venue-equip">${VENUE_EQUIP[venue]}</div>` : ''}
          ${isDayOverridden(w,d) ? `<div class="venue-override">Переопределено для этого дня. Профиль: ${PROFILES.find(p=>p.id===state.profile).name}</div>` : ''}
        </div>
      </div>
      <div class="workout-blocks">
  `;

  day.blocks.forEach((block, bi) => {
    const blockId = `${w}-${d}-${bi}`;
    const isOpen = state.openBlocks[blockId] !== false;
    const config = getBlockTypeConfig(block.type);
    const roundsInfo = block.rounds > 1 ? `${block.rounds} ${block.type==='circuit'?'кругов':'подходов'}` : '1 проход';
    const restInfo = block.rest > 0 ? `Отдых ${block.rest >= 60 ? Math.floor(block.rest/60) + ' мин' : block.rest + ' сек'}` : 'Без отдыха';

    html += `
      <div class="block fade-in">
        <button class="block-header" type="button" onclick="toggleBlock('${blockId}')">
          <div class="block-title-wrap">
            <div class="block-label">${config.icon}</div>
            <div>
              <div class="block-name">${block.name}</div>
              <div class="block-meta">
                <div class="block-chip">${config.label}</div>
                <div class="block-chip">${roundsInfo}</div>
                <div class="block-chip">${restInfo}</div>
              </div>
            </div>
          </div>
          <div class="block-chevron ${isOpen?'open':''}">▼</div>
        </button>
        <div class="block-content ${isOpen?'open':''}">
    `;

    block.exercises.forEach((ex, ei) => {
      const effectiveVenue = day.isOverride ? 'gym' : (venue === 'run' ? 'outdoor' : venue);
      const vex = day.isOverride ? ex : getExForVenue(ex, effectiveVenue);
      const eid = exId(w, d, bi, ei);
      const done = isExDone(eid);
      const timeMatch = vex.params.match(/^(\d+)\s*(мин|сек)/);
      const exTimerSec = timeMatch ? (timeMatch[2] === 'мин' ? parseInt(timeMatch[1]) * 60 : parseInt(timeMatch[1])) : 0;
      html += `
        <div class="exercise ${done ? 'done' : ''}" id="exercise-${eid}">
          <button class="ex-check ${done ? 'checked' : ''}" type="button" onclick="toggleExercise('${eid}')" aria-label="${done ? 'Снять выполнение' : 'Отметить упражнение выполненным'}"></button>
          <div class="ex-img">${getExImgHtml(vex.name)}</div>
          <div class="ex-body">
            <div class="ex-name">${vex.name}</div>
            ${vex.params ? `<div class="ex-params">${vex.params}</div>` : ''}
            ${vex.notes ? `<div class="ex-notes">${vex.notes}</div>` : ''}
            <div class="ex-actions">
              ${exTimerSec > 0 ? `<button class="ex-rest-btn primary" type="button" onclick="startTimer(${exTimerSec},'${vex.name.replace(/'/g,"\\'")}')">▶ ${timeMatch[1]} ${timeMatch[2]}</button>` : ''}
              ${block.rest > 0 ? `<button class="ex-rest-btn" type="button" onclick="startTimer(${block.rest},'${config.label}: отдых')">⏲ ${block.rest >= 60 ? Math.floor(block.rest/60)+' мин' : block.rest+' сек'}</button>` : ''}
            </div>
          </div>
        </div>
      `;
    });

    html += `</div></div>`;
  });

  html += `
      </div>
      <div class="metrics-wrap">
        <div class="surface metrics-card">
          <div class="notes-label">Метрики дня</div>
          <div class="metrics-grid">
            <div class="metric-field">
              <label for="metricRpe">RPE</label>
              <input id="metricRpe" type="number" min="1" max="10" value="${metrics.rpe}" placeholder="1-10" onchange="saveDayMetric(${w},${d},'rpe',this.value)">
            </div>
            <div class="metric-field">
              <label for="metricPulse">Пульс</label>
              <input id="metricPulse" type="number" min="0" value="${metrics.pulse}" placeholder="средний / max" onchange="saveDayMetric(${w},${d},'pulse',this.value)">
            </div>
            <div class="metric-field">
              <label for="metricPack">Рюкзак, кг</label>
              <input id="metricPack" type="number" min="0" step="0.5" value="${metrics.pack}" placeholder="вес" onchange="saveDayMetric(${w},${d},'pack',this.value)">
            </div>
          </div>
        </div>
      </div>
      <div class="complete-all-wrap">
        <button class="complete-all-btn ${allDone?'undo':'do'}" type="button" onclick="toggleAllDay(${w},${d})">
          ${allDone ? 'Снять отметки со дня' : 'Отметить день выполненным'}
        </button>
      </div>
  `;

  const noteKey = `w${w}d${d}`;
  html += `
      <div class="notes-wrap">
        <div class="surface notes-card">
          <div class="notes-label">Заметки по тренировке</div>
          <textarea class="notes-area" id="dayNotes" placeholder="Вес, пульс, ощущения, что стоит поправить..." onblur="saveDayNotes(${w},${d})">${state.notes[noteKey]||''}</textarea>
        </div>
      </div>
  `;

  const hist = state.history[noteKey];
  if (hist && hist.date) {
    const dur = hist.endMs && hist.startMs ? Math.round((hist.endMs - hist.startMs) / 60000) : 0;
    html += `
      <div class="history-wrap">
        <div class="surface history-card">
          <div class="history-title">История дня</div>
          <div class="history-list">
            <div class="history-item"><span>Дата</span><span>${hist.date}</span></div>
            ${dur > 0 ? `<div class="history-item"><span>Длительность</span><span>~${dur} мин</span></div>` : ''}
            ${metrics.rpe ? `<div class="history-item"><span>RPE</span><span>${metrics.rpe}/10</span></div>` : ''}
            ${metrics.pulse ? `<div class="history-item"><span>Пульс</span><span>${metrics.pulse}</span></div>` : ''}
            ${metrics.pack ? `<div class="history-item"><span>Рюкзак</span><span>${metrics.pack} кг</span></div>` : ''}
          </div>
        </div>
      </div>
    `;
  }

  html += `
      <div class="day-footer-nav">
        <div class="week-nav" style="padding:0">
          <button class="week-nav-btn" type="button" ${d===0?'disabled':''} onclick="navigate('day',${w},${d-1})">&larr; ${d>0?PROGRAM[w][d-1].day:''}</button>
          <div class="week-nav-title">${day.day}</div>
          <button class="week-nav-btn" type="button" ${d===2?'disabled':''} onclick="navigate('day',${w},${d+1})">${d<2?PROGRAM[w][d+1].day:''} &rarr;</button>
        </div>
      </div>
      ${nextExercise ? `
        <div class="workout-cta">
          <div class="workout-cta-copy">
            <div class="workout-cta-label">Guided workout</div>
            <div class="workout-cta-title">Продолжить с первого незавершённого упражнения</div>
          </div>
          <button class="workout-cta-btn" type="button" onclick="goToNextExercise(${w},${d})">К упражнению</button>
        </div>
      ` : `
        <div class="workout-cta">
          <div class="workout-cta-copy">
            <div class="workout-cta-label">Тренировка завершена</div>
            <div class="workout-cta-title">Все упражнения этого дня отмечены.</div>
          </div>
          <button class="workout-cta-btn" type="button" onclick="navigate('week',${w})">К неделе</button>
        </div>
      `}
    </div>
  `;

  app.innerHTML = html;
  if (uiState.pendingExerciseFocus) {
    const targetId = uiState.pendingExerciseFocus;
    uiState.pendingExerciseFocus = '';
    focusExerciseElement(targetId);
  }
}

// ============ ACTIONS ============
function toggleBlock(blockId) {
  state.openBlocks[blockId] = state.openBlocks[blockId] === false ? true : false;
  render();
}

function toggleExercise(eid) {
  toggleEx(eid);
  // Minimal re-render: just update this exercise + progress
  render();
}

function toggleAllDay(wIdx, dIdx) {
  const day = getDayData(wIdx, dIdx);
  const dc = getDayExCount(wIdx, dIdx);
  const allDone = dc.done === dc.total;
  const now = Date.now();
  day.blocks.forEach((bl, bi) => {
    bl.exercises.forEach((_, ei) => {
      const id = exId(wIdx, dIdx, bi, ei);
      if (allDone) delete state.completed[id];
      else state.completed[id] = true;
    });
  });
  syncDayHistory(wIdx, dIdx, now);
  saveState();
  render();
}

function resetProgress() {
  if (confirm('Сбросить весь прогресс тренировок?')) {
    state.completed = {};
    state.history = {};
    state.weekOffset = 0;
    state.dayMetrics = {};
    state.expedition = { gear: {}, hikes: [], taper: {} };
    saveState();
    render();
  }
}

// ============ CALENDAR EXPORT ============
function exportCalendar() {
  const startValue = state.cal.start || document.getElementById('calStart')?.value || new Date().toISOString().slice(0,10);
  if (!startValue) { alert('Выберите дату начала тренировок'); return; }

  const startDate = new Date(startValue + 'T00:00:00');
  // Find the nearest Tuesday (day 2) on or after the selected date
  const dow = startDate.getDay(); // 0=Sun
  const daysToTue = (2 - dow + 7) % 7;
  const firstTue = new Date(startDate);
  firstTue.setDate(firstTue.getDate() + daysToTue);

  const DAY_OFFSETS = [0, 2, 4]; // Tue=0, Thu=+2, Sat=+4
  const HOURS = Array.isArray(state.cal.times) && state.cal.times.length === 3 ? state.cal.times : CALENDAR_DEFAULTS.times;
  const DURATION = Number(state.cal.duration) > 0 ? Number(state.cal.duration) : CALENDAR_DEFAULTS.duration;

  function icsDate(d, time) {
    const [h, m] = time.split(':').map(Number);
    const dt = new Date(d);
    dt.setHours(h, m, 0, 0);
    return dt.toISOString().replace(/[-:]/g,'').replace(/\.\d{3}/,'');
  }

  function icsDateEnd(d, time, dur) {
    const [h, m] = time.split(':').map(Number);
    const dt = new Date(d);
    dt.setHours(h, m + dur, 0, 0);
    return dt.toISOString().replace(/[-:]/g,'').replace(/\.\d{3}/,'');
  }

  let events = '';
  const uid = () => Math.random().toString(36).slice(2) + Date.now().toString(36);

  for (let w = 0; w < 16; w++) {
    for (let di = 0; di < 3; di++) {
      const day = getDayData(w, di);
      const eventDate = new Date(firstTue);
      eventDate.setDate(firstTue.getDate() + w * 7 + DAY_OFFSETS[di]);

      const venue = getDayVenue(w, di);
      const vLabel = VENUE_LABELS[venue] || '';
      const summary = `Тренировка: ${day.focus}`;
      const desc = `Неделя ${w+1}, ${['Вт','Чт','Сб'][di]}\\n${vLabel}\\n\\nБлоки:\\n${day.blocks.map(bl => '- ' + bl.name + ' (' + bl.exercises.length + ' упр.)').join('\\n')}`;

      events += `BEGIN:VEVENT\r\nUID:${uid()}@hiking-trainer\r\nDTSTAMP:${icsDate(new Date(),'00:00')}\r\nDTSTART:${icsDate(eventDate, HOURS[di])}\r\nDTEND:${icsDateEnd(eventDate, HOURS[di], DURATION)}\r\nSUMMARY:${summary}\r\nDESCRIPTION:${desc}\r\nBEGIN:VALARM\r\nTRIGGER:-PT1H\r\nACTION:DISPLAY\r\nDESCRIPTION:Тренировка через 1 час\r\nEND:VALARM\r\nBEGIN:VALARM\r\nTRIGGER:-PT15M\r\nACTION:DISPLAY\r\nDESCRIPTION:Тренировка через 15 минут\r\nEND:VALARM\r\nEND:VEVENT\r\n`;
    }
  }

  const ics = `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Hiking Trainer//RU\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\nX-WR-CALNAME:Горный тренер\r\n${events}END:VCALENDAR`;

  const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'hiking-trainer.ics';
  a.click();
  URL.revokeObjectURL(url);
}

// ============ BACK BUTTON ============
window.addEventListener('popstate', () => {
  if (state.screen === 'day') navigate('week', state.weekIdx);
  else if (state.screen === 'week') navigate('program');
});

// Push state on navigation
const origNavigate = navigate;
navigate = function(screen, weekIdx, dayIdx) {
  if (screen !== state.screen || weekIdx !== state.weekIdx || dayIdx !== state.dayIdx) {
    history.pushState(null, '');
  }
  origNavigate(screen, weekIdx, dayIdx);
};

// ============ INIT ============
loadState();
render();

// Register service worker for PWA
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
