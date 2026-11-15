(function () {
  'use strict';

  var KEY = 'wage-proto-v1';
  var DAYS = ['월', '화', '수', '목', '금', '토', '일'];
  var MIN_WAGE = 10320;
  var MAX_SIZE = 10 * 1024 * 1024;
  var ALL = ['start', 'upload', 'q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'review', 'result', 'form', 'complaint', 'guide', 'counselor'];
  var STEP = { upload: 1, q1: 1, q2: 1, q3: 1, q4: 1, q5: 1, q6: 1, review: 2, result: 3, form: 4, complaint: 4 };
  var TITLE = { upload: 'title.upload', q1: 'title.q', q2: 'title.q', q3: 'title.q', q4: 'title.q', q5: 'title.q', q6: 'title.q', review: 'title.review', result: 'title.result', form: 'title.form', complaint: 'title.complaint', guide: 'title.guide', counselor: 'title.counselor' };
  var NEED_CALC = ['result', 'form', 'complaint', 'counselor'];
  // 영어·베트남어로 보여 주는 화면 (나머지는 한국어)
  var TRANSLATED = ['start', 'result', 'guide'];
  // 추출 키 → 확인 화면 줄 이름
  var ROW_OF = { companyName: 'company', employeeCount: 'size', startDate: 'start', endDate: 'end', workDaysPerWeek: 'days', dailyContractHours: 'time', weeklyContractHours: 'time', wageType: 'wage', baseWage: 'wage', allowances: 'allowance', payMonth: 'month', paidAmount: 'paid', overtimeHours: 'ot', nightHours: 'night', holidayHours: 'holiday' };
  // 오류 field 앞부분 → 확인 화면 줄 · 진정서 입력칸
  var FIELD_ROW = [['workplace', 'size'], ['employment.startDate', 'start'], ['employment.endDate', 'end'], ['schedule', 'time'], ['wage.ordinaryAllowances', 'allowance'], ['wage', 'wage'], ['months[0].month', 'month'], ['months[0].paidAmount', 'paid'], ['months[0].overtime', 'ot'], ['months[0].night', 'night'], ['months[0].holiday', 'holiday']];
  var FIELD_P = { 'complainant.name': 'name', 'complainant.phone': 'phone', 'complainant.address': 'addr', 'respondent.companyName': 'company', 'respondent.ownerName': 'owner', 'respondent.address': 'waddr', 'respondent.phone': 'wphone', job: 'job', extraFacts: 'facts' };
  var L = {};

  function blankFields() {
    return { company: '', size: '', start: '', employed: '', end: '', days: [false, false, false, false, false, false, false], tStart: '', tEnd: '', brk: '', dayHours: '', wageType: '', wage: '', allowance: '', month: '', paid: '', ot: '', night: '', holiday: '' };
  }
  function detectLang() {
    var l = (navigator.language || 'ko').toLowerCase();
    if (l.indexOf('vi') === 0) return 'vi';
    if (l.indexOf('ko') === 0) return 'ko';
    return 'en';
  }
  function fresh() {
    return {
      route: 'start', agreed: false, lang: detectLang(), big: false, path: '',
      docType: 'payslip', up: { status: 'idle', name: '', msg: '' },
      f: blankFields(), src: {}, low: {}, ok: {}, unread: {}, fieldErr: {},
      p: { name: '', phone: '', addr: '', owner: '', waddr: '', wphone: '', job: '', facts: '' }, pErr: {},
      formTried: false, factsText: null, editing: false, tr: true,
      calc: null, explain: null, explainState: '', draft: null
    };
  }
  function load() {
    try {
      var raw = sessionStorage.getItem(KEY);
      if (raw) return Object.assign(fresh(), JSON.parse(raw));
    } catch (e) {}
    return fresh();
  }
  function save() {
    try { sessionStorage.setItem(KEY, JSON.stringify(S)); } catch (e) {}
  }

  var S = load();
  var stack = [];
  var app = document.getElementById('app');
  var inFrame = window.self !== window.top;

  // ---------- 도우미 ----------
  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  // 화면 문구: i18n/<언어>.json, 번역 안 된 화면이나 빠진 문구는 한국어
  function t(k, vars) {
    var lang = TRANSLATED.indexOf(S.route) >= 0 || k.indexOf('err.') === 0 ? S.lang : 'ko';
    var s = (L[lang] && L[lang][k]) || (L.ko && L.ko[k]) || k;
    return s.replace(/\{(\w+)\}/g, function (_, v) { return (vars || {})[v]; });
  }
  function loadLang(lang) {
    if (L[lang]) return Promise.resolve();
    return fetch('i18n/' + lang + '.json').then(function (r) { return r.json(); }).then(function (d) { L[lang] = d; }, function () { L[lang] = {}; });
  }
  function money(n) { return t('money', { n: Number(n).toLocaleString('ko-KR') }); }
  function won(n) { return Number(n).toLocaleString('ko-KR') + '원'; }
  function readable(n) {
    n = Number(n);
    if (!n) return n === 0 ? '0원' : '';
    var man = Math.floor(n / 10000), rest = n % 10000;
    if (!man) return rest.toLocaleString('ko-KR') + '원';
    return man.toLocaleString('ko-KR') + '만' + (rest ? ' ' + rest.toLocaleString('ko-KR') + '원' : ' 원');
  }
  function krDate(d) {
    if (!d) return '';
    var p = d.split('-');
    return Number(p[0]) + '년 ' + Number(p[1]) + '월 ' + Number(p[2]) + '일';
  }
  function dotDate(d) { return d ? d.replace(/-/g, '.') : ''; }
  function hours() {
    var f = S.f;
    if (f.tStart === '' || f.tEnd === '' || f.brk === '') return Number(f.dayHours) || 0;
    return (Number(f.tEnd) - Number(f.tStart)) - Number(f.brk);
  }
  function dayText() {
    var d = DAYS.filter(function (_, i) { return S.f.days[i]; });
    return d.join(' ');
  }
  function hh(v) { return (Number(v) < 10 ? '0' : '') + Number(v) + ':00'; }
  function toast(msg) {
    var t = document.getElementById('toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () { t.hidden = true; }, 3200);
  }

  // 직접 그린 선 아이콘 (24칸, 선 굵기 1.75 통일 — css .ico)
  var ICONS = {
    'arrow-r': '<path d="M5 12h14M13 6l6 6-6 6"/>',
    'arrow-l': '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    camera: '<path d="M4 8h3l2-2.5h6L17 8h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"/><circle cx="12" cy="13.5" r="3.5"/>',
    file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h4"/>',
    payslip: '<path d="M6 3h12v18l-2-1.5-2 1.5-2-1.5-2 1.5-2-1.5L6 21z"/><path d="M9 8h6M9 12h6M9 16h3"/>',
    contract: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M8.5 17c1-1.6 2-1.6 2.6-.4.5 1 1.2 1 2-.2.6-.8 1.2-.6 1.9.6"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6"/>',
    phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18"/>',
    chat: '<path d="M4 5h16v11H10l-5 4v-4H4z"/><path d="M8 9.5h8M8 12.5h5"/>',
    print: '<path d="M7 9V3h10v6M7 18H5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M7 14h10v7H7z"/>',
    copy: '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/>',
    pen: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
    out: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>'
  };
  function icon(name, cls) {
    return '<svg class="ico' + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + ICONS[name] + '</svg>';
  }
  // 노란돌고래 마스코트 (favicon.svg 와 같은 그림)
  function dolphin(cls) {
    return '<svg class="' + cls + '" viewBox="0 0 64 64" aria-hidden="true" focusable="false"><g transform="translate(2 4)" stroke="currentColor" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" fill="#FFC21A">' +
      '<path d="M33 20.5c1-5 4-8.5 8.5-9.5-1.2 3.4-1.2 6.6-.2 10z"/><path d="M11 45c3.5-14 15.5-23.5 30-23 6 .2 10.5 2.6 13.5 5.8l5 1.4c.6 1-.4 2.2-2 2.2h-4c-4 5.5-11.5 9.6-21.5 9.6-8 0-14.6 1.4-18.6 6.5"/>' +
      '<path d="M13.5 46.5c-3.5-1-7.5-.4-10 2.5 3.8.2 7 1.5 9.2 4.5"/><path d="M33.5 40c.3 4-2 7.4-6 8.6 1.6-2.8 2.2-5.6 2-8.3"/></g>' +
      '<circle cx="49.5" cy="29.5" r="2" fill="currentColor"/>' +
      (cls === 'mascot' ? '<path class="wave" d="M4 60c5-3 9-3 14 0s9 3 14 0 9-3 14 0 9 3 14 0" fill="none" stroke-width="3" stroke-linecap="round"/>' : '') + '</svg>';
  }

  // ---------- 서버 ----------
  // 오류는 API.md 오류 표대로: e.status · e.code · e.field 와 화면에 보일 문구
  function api(url, body) {
    var form = body instanceof FormData;
    return fetch(url, {
      method: 'POST',
      headers: form ? {} : { 'Content-Type': 'application/json' },
      body: form ? body : JSON.stringify(body)
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (data) {
        if (r.ok) return data;
        var key = r.status === 404 ? 'err.expired' : { 413: 'err.413', 415: 'err.415', 422: 'err.422', 429: 'err.429', 503: 'err.503' }[r.status] || 'err.default';
        var e = new Error(r.status === 400 ? data.detail || t('err.400') : t(key));
        e.status = r.status; e.code = data.code; e.field = data.field;
        throw e;
      });
    }, function () {
      throw new Error(t('err.network'));
    });
  }
  // 30분이 지나 결과가 지워졌으면(404 CALCULATION_EXPIRED) 입력 확인 화면으로
  function expired(e) {
    if (e.status !== 404) return false;
    S.calc = null; S.draft = null;
    toast(e.message);
    go('review');
    return true;
  }
  // 400 의 field → 그 칸 아래 detail
  function fieldRow(field) {
    var m = FIELD_ROW.filter(function (p) { return field && field.indexOf(p[0]) === 0; })[0];
    return m && m[1];
  }
  function hoursOrNull(v) { return v === '' ? null : Number(v); }
  // 화면 입력값 → POST /api/v1/calculations 요청 (API.md 형식)
  function toRequest() {
    var f = S.f;
    var count = f.days.filter(Boolean).length;
    var h = hours();
    var lastDay = f.employed === 'no' && f.end ? f.end : new Date().toISOString().slice(0, 10);
    var holiday = hoursOrNull(f.holiday);
    return {
      workplace: { size: f.size === 'under' ? 'UNDER_FIVE' : 'FIVE_OR_MORE' },
      employment: { startDate: f.start, endDate: f.employed === 'yes' ? null : f.end },
      schedule: { weeklyContractHours: h * count, dailyContractHours: h, workDaysPerWeek: count },
      wage: {
        type: { hour: 'HOURLY', day: 'DAILY', month: 'MONTHLY' }[f.wageType],
        baseAmount: Number(f.wage),
        ordinaryAllowances: f.allowance ? [{ name: '매달 받는 수당', amount: Number(f.allowance) }] : [],
        otherAllowances: [],
        annualBonus: 0
      },
      months: [{
        month: f.month || lastDay.slice(0, 7),
        paidAmount: Number(f.paid),
        overtimeHours: hoursOrNull(f.ot),
        nightHours: hoursOrNull(f.night),
        holidayHoursWithin8: holiday == null ? null : Math.min(holiday, 8),
        holidayHoursOver8: holiday == null ? null : Math.max(holiday - 8, 0),
        absentWeeks: 0
      }],
      lastThreeMonthsWages: null
    };
  }
  // POST /calculations 응답 → 화면에서 쓰는 값
  function viewResult() {
    var c = S.calc;
    var explain = S.explain || {};
    var pending = S.explainState === 'loading' ? t('explain.loading') : S.explainState === 'fail' ? t('explain.fail') : S.explainState === '503' ? t('err.503') : '';
    var names = S.lang === 'ko' ? {} : L[S.lang] || {};
    return {
      total: c.summary.totalUnpaid,
      due: c.summary.totalDue,
      paid: c.summary.totalPaid,
      hourly: c.hourlyOrdinaryWage ? Number(c.hourlyOrdinaryWage.value).toLocaleString('ko-KR', { maximumFractionDigits: 2 }) + '원' : '—',
      daily: c.dailyAverageWage ? Number(c.dailyAverageWage.value).toLocaleString('ko-KR') + '원' : '—',
      notApplied: (c.notApplied || []).map(function (n) { return n.reason; }),
      items: c.items.map(function (it) {
        var b = (it.legalBasis || [])[0] || {};
        var name = names['item.' + it.code] || it.label;
        return {
          code: it.code,
          name: name,
          label: name + (it.month ? ' (' + (S.lang === 'ko' ? Number(it.month.slice(5)) + '월' : it.month) + ')' : ''),
          amount: it.status === 'CALCULATED' ? it.amount : null,
          status: it.status === 'CALCULATED' ? '' : t('status.' + it.status),
          missing: it.status === 'NO_DATA',
          estimated: !!it.estimated,
          explain: explain[it.code] || pending,
          article: [b.law, b.article].filter(Boolean).join(' '),
          gist: b.summary || '',
          formula: it.formula || ''
        };
      })
    };
  }
  function calcUrl(tail) { return '/api/v1/calculations/' + encodeURIComponent(S.calc.id) + tail; }
  function calculate(btn) {
    btn.disabled = true;
    btn.textContent = t('calc.busy');
    api('/api/v1/calculations', toRequest()).then(function (res) {
      S.calc = res;
      S.draft = null;
      S.factsText = null;
      go('result');
      loadExplanation();
    }, function (e) {
      var row = e.status === 400 && fieldRow(e.field);
      if (row) S.fieldErr[row] = e.message;
      render();
      toast(e.message);
    });
  }
  // 해설은 실패해도 금액은 그대로 보이고 설명 칸만 안내 문구
  function loadExplanation() {
    S.explain = null;
    S.explainState = 'loading';
    api(calcUrl('/explanation'), { lang: S.lang }).then(function (res) {
      var m = {};
      (res.items || []).forEach(function (i) { m[i.code] = i.text; });
      S.explain = m;
      S.explainState = 'done';
    }, function (e) {
      if (expired(e)) return;
      S.explainState = e.status === 503 ? '503' : 'fail';
    }).then(function () {
      if (S.route === 'result') render(); else save();
    });
  }
  // 진정서 초안: 인적사항은 서버가 넣고, 금액은 계산 id 로만 정해진다
  function makeDraft(btn) {
    var f = S.f, p = S.p;
    btn.disabled = true;
    btn.textContent = t('make.busy');
    S.pErr = {};
    api(calcUrl('/complaint-draft'), {
      complainant: { name: p.name, phone: p.phone, address: p.addr },
      respondent: { companyName: f.company || p.company, ownerName: p.owner, address: p.waddr, phone: p.wphone },
      job: p.job,
      employed: f.employed === 'yes',
      extraFacts: p.facts,
      lang: S.lang
    }).then(function (res) {
      document.getElementById('confirm').close();
      S.draft = res;
      S.factsText = null;
      S.editing = false;
      go('complaint');
    }, function (e) {
      document.getElementById('confirm').close();
      if (expired(e)) return;
      var key = e.status === 400 && FIELD_P[e.field];
      if (key) S.pErr[key] = e.message;
      render();
      toast(e.message);
    });
  }

  // ---------- 검사 ----------
  function valid(r) {
    var f = S.f;
    switch (r) {
      case 'q1': return !!f.size;
      case 'q2': return !!f.start;
      case 'q3': return f.employed === 'yes' || (f.employed === 'no' && !!f.end && f.end >= f.start);
      case 'q4': return f.days.some(Boolean) && hours() > 0;
      case 'q5': return !!f.wageType && Number(f.wage) > 0;
      case 'q6': return f.paid !== '';
      case 'review':
        return ['q1', 'q2', 'q3', 'q4', 'q5', 'q6'].every(valid) && Object.keys(S.low).every(function (k) { return !S.low[k] || S.ok[k]; });
      default: return true;
    }
  }
  function lowLeft() {
    return Object.keys(S.low).filter(function (k) { return S.low[k] && !S.ok[k]; }).length;
  }

  // ---------- 이동 ----------
  function go(r, opts) {
    if (ALL.indexOf(r) < 0) r = 'start';
    if (r !== 'start' && !S.agreed) r = 'start';
    if (NEED_CALC.indexOf(r) >= 0 && !S.calc) r = 'review';
    if (r === 'complaint' && !S.draft) r = 'form';
    if (!opts || !opts.back) stack.push(S.route);
    S.route = r;
    save();
    var url = '#' + r;
    try {
      if (inFrame) history.replaceState(null, '', url);
      else if (!opts || !opts.fromPop) history.pushState(null, '', url);
    } catch (e) {}
    render(true);
  }
  function back() {
    var prev = stack.pop();
    if (!prev) prev = 'start';
    go(prev, { back: true });
  }
  window.addEventListener('popstate', function () {
    var r = (location.hash || '#start').slice(1);
    stack.pop();
    go(r, { back: true, fromPop: true });
  });

  // ---------- 공통 틀 ----------
  function topbar() {
    var r = S.route;
    var step = STEP[r] || 0;
    var steps = [1, 2, 3, 4].map(function (i) {
      return '<li' + (step === i ? ' aria-current="step"' : step > i ? ' class="done"' : '') + '><b>' + (step > i ? icon('check') : i) + '</b>' + t('step.' + i) + '</li>';
    }).join('');
    var brand = '<span class="brand">' + dolphin('') + '<span>' + t('brand') + ' <small>' + t('brandSmall') + '</small></span></span>';
    var left = r === 'start'
      ? brand
      : '<button type="button" class="icon-btn" data-act="back" aria-label="' + t('top.back') + '">' + icon('arrow-l') + '</button>' +
        '<span class="top-title">' + esc(TITLE[r] ? t(TITLE[r]) : '') + '</span>' +
        '<span class="brand-desk">' + brand + '</span>';
    return '<header class="top"><div class="top-in">' + left +
      '<ol class="steps-nav" aria-label="' + t('top.steps') + '">' + steps + '</ol>' +
      '<div class="top-tools">' +
      '<button type="button" class="big-btn" data-act="big" aria-pressed="' + S.big + '" aria-label="' + t('top.big') + '">가<span aria-hidden="true">+</span></button>' +
      '<label class="sr" for="lang-top">' + t('top.lang') + '</label>' +
      '<span class="lang-wrap">' + icon('globe') + '<select id="lang-top" class="lang-select" data-act="lang-select">' +
      opt('ko', '한국어') + opt('en', 'English') + opt('vi', 'Tiếng Việt') +
      '</select></span></div></div></header>';
    function opt(v, t) { return '<option value="' + v + '"' + (S.lang === v ? ' selected' : '') + '>' + t + '</option>'; }
  }
  // 번역 안 된 화면에서만 '한국어로만 나와요' 안내
  function langBanner() {
    var note = S.lang !== 'ko' && TRANSLATED.indexOf(S.route) < 0 && (L[S.lang] || {}).langNote;
    return note ? '<p class="banner" lang="' + S.lang + '">' + esc(note) + '</p>' : '';
  }
  function progress(step) {
    var bars = [1, 2, 3, 4].map(function (i) { return '<span' + (i < step ? ' class="on"' : i === step ? ' class="now"' : '') + '></span>'; }).join('');
    return '<div class="progress only-m"><div class="bar" aria-hidden="true">' + bars + '</div><span class="hint">' + t('progress', { n: step, label: t('step.' + step) }) + '</span></div>';
  }
  function cta(inner, extraClass) {
    return '<div class="cta ' + (extraClass || 'col') + '">' + inner + '</div>';
  }
  // 주 버튼: 글자 + 노란 화살표
  function primary(label, attrs, enabled) {
    return '<button type="button" class="btn btn-primary" ' + attrs + (enabled === false ? ' disabled' : '') + '><span>' + label + '</span>' + icon('arrow-r') + '</button>';
  }

  // ---------- 화면: 시작 ----------
  function vStart() {
    var langs = [['ko', '한국어'], ['en', 'English'], ['vi', 'Tiếng Việt']].map(function (l) {
      return '<button type="button" data-act="lang" data-v="' + l[0] + '" aria-pressed="' + (S.lang === l[0]) + '" lang="' + l[0] + '">' + l[1] + '</button>';
    }).join('');
    return '<div class="split wide-side">' +
      '<div class="stack-lg">' +
      '<section class="hero">' + dolphin('mascot') + '<span class="eyebrow">' + t('start.eyebrow') + '</span>' +
      '<h1 tabindex="-1">' + t('start.title') + '</h1>' +
      '<p class="lead">' + t('start.lead') + '</p>' +
      '<ul class="trust"><li>' + icon('user') + t('start.trust1') + '</li><li>' + icon('lock') + t('start.trust2') + '</li><li>' + icon('globe') + t('start.trust3') + '</li></ul></section>' +
      '<ol class="steps-list"><li>' + t('start.step1') + '</li><li>' + t('start.step2') + '</li><li>' + t('start.step3') + '</li></ol>' +
      '</div>' +
      '<div class="side start-side">' +
      '<fieldset class="stack"><legend class="label" style="margin-bottom:8px">언어 · Language · Ngôn ngữ</legend><div class="lang-btns">' + langs + '</div>' +
      '<p class="hint">' + t('start.langHint') + '</p></fieldset>' +
      '<section class="agree-box stack"><h2>' + t('start.noteTitle') + '</h2>' +
      '<p>' + t('start.noteBody') + '</p>' +
      '<label class="check" for="agree"><input id="agree" type="checkbox" data-act="agree"' + (S.agreed ? ' checked' : '') + '><span>' + t('start.agree') + '</span></label></section>' +
      cta(primary(t('start.docBtn'), 'data-act="path" data-v="doc"', S.agreed) +
        '<button type="button" class="btn btn-line" data-act="path" data-v="manual"' + (S.agreed ? '' : ' disabled') + '>' + t('start.manualBtn') + '</button>' +
        (S.agreed ? '' : '<p class="hint">' + t('start.agreeHint') + '</p>'), ' ') +
      '</div></div>';
  }

  // ---------- 화면: 서류 올리기 ----------
  function vUpload() {
    var u = S.up;
    var docs = [['payslip', '급여명세서', '달마다 실제로 받은 금액을 확인해요'], ['contract', '근로계약서', '약속한 시급·월급과 근무시간을 확인해요']].map(function (d) {
      return '<label class="choice"><input type="radio" name="doc" data-act="doctype" value="' + d[0] + '"' + (S.docType === d[0] ? ' checked' : '') + '><span style="flex:1">' + d[1] + '<small>' + d[2] + '</small></span>' + icon(d[0]) + '</label>';
    }).join('');
    var status = '';
    if (u.status === 'reading') {
      status = '<div class="filecard"><div class="filecard-head">' + icon('file') + '<span>' + esc(u.name) + '</span></div><div class="scan" aria-hidden="true"><i></i><i></i><i></i></div><p class="hint" role="status">' + t('up.reading') + '</p></div>';
    } else if (u.status === 'done') {
      status = '<div class="filecard done"><div class="filecard-head">' + icon('check') + '<span>' + esc(u.name) + '</span></div><p role="status">' + t('up.done', { n: u.n }) +
        (u.unread ? ' ' + t('up.unread', { n: u.unread }) : '') + (u.masked ? ' ' + t('up.masked', { n: u.masked }) : '') + '</p></div>';
    } else if (u.status === 'error') {
      status = '<div class="warnbox" role="alert"><strong>' + esc(u.msg) + '</strong></div>';
    } else if (u.status === 'fail') {
      status = '<div class="warnbox stack" role="alert"><strong>' + esc(u.msg) + '</strong><p>사진이 흐리거나 그림자가 졌을 수 있어요. 다시 찍거나 직접 입력해 주세요.</p><button type="button" class="btn btn-line" data-act="path" data-v="manual">직접 입력하기</button></div>';
    }
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(1) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">어떤 서류가 있나요?</h1><p class="lead">사진을 찍거나 파일을 올리면 글자를 읽어 칸을 채워 드려요.</p></div>' +
      '<fieldset class="stack"><legend class="sr">서류 종류</legend><div class="choices">' + docs + '</div><p class="hint">둘 다 있으면 하나씩 올려 주세요.</p></fieldset>' +
      '<div class="capture">' +
      '<div class="frame" aria-hidden="true">' + icon(S.docType) + '</div>' +
      '<div class="btn-row">' +
      '<label class="btn btn-primary" for="cam">' + icon('camera') + '<span>사진 찍기</span></label>' +
      '<label class="btn btn-line" for="file">' + icon('file') + '<span>파일에서 고르기</span></label></div>' +
      '<input id="cam" class="sr" type="file" accept="image/jpeg,image/png" capture="environment" data-act="file">' +
      '<input id="file" class="sr" type="file" accept="application/pdf,image/jpeg,image/png,.hwp" data-act="file">' +
      '<p class="hint">PDF, JPG, PNG · 파일당 10MB까지<br>한글(HWP) 파일은 PDF로 바꿔서 올려 주세요</p></div>' +
      status + '</div>' +
      '<aside class="side no-print"><section class="stack"><h2>잘 읽히는 사진 찍는 법</h2><ol class="tips"><li><b>1</b>평평한 곳에 놓고 위에서 찍기</li><li><b>2</b>네 모서리가 다 보이게</li><li><b>3</b>그림자와 빛 반사 없이</li></ol></section>' +
      '<p class="note">' + icon('lock') + ' 올린 서류는 글자만 읽고 저장하지 않아요. 주민등록번호는 가려서 읽지 않아요.</p>' +
      cta(primary('읽은 내용 확인하기', 'data-go="review"', u.status === 'done' || Object.keys(S.src).some(function (k) { return S.src[k] === 'doc'; })) +
        '<button type="button" class="btn-text" data-act="path" data-v="manual">서류 없이 직접 입력하기</button>', ' ') +
      '</aside></div>';
  }

  // ---------- 화면: 직접 입력 ----------
  var Q_NAMES = ['직원 수', '일 시작한 날', '지금도 일하는지', '요일과 시간', '약속한 급여', '실제로 받은 돈'];
  // 질문 화면 틀. 넓은 화면에서는 옆 칸에 질문 6개 목록 + 다음 버튼
  function qShell(n, legend, help, body) {
    var bars = Q_NAMES.map(function (_, i) { return '<span' + (i + 1 < n ? ' class="on"' : i + 1 === n ? ' class="now"' : '') + '></span>'; }).join('');
    var list = Q_NAMES.map(function (name, i) {
      var q = 'q' + (i + 1), cur = i + 1 === n, done = !cur && valid(q);
      var inner = '<b>' + (done ? icon('check') : i + 1) + '</b>' + name;
      return '<li>' + (cur ? '<span aria-current="step">' + inner + '</span>' : '<button type="button" data-go="' + q + '"' + (done ? ' class="done"' : '') + '>' + inner + '</button>') + '</li>';
    }).join('');
    return '<div class="split">' +
      '<div class="stack-lg">' +
      '<div class="progress"><div class="bar" style="--n:6" aria-hidden="true">' + bars + '</div><span class="hint">필수 질문 ' + n + ' / 6</span></div>' + langBanner() +
      '<fieldset class="stack"><legend class="q-legend"><h1 tabindex="-1">' + legend + '</h1></legend>' +
      (help ? '<p class="lead">' + help + '</p>' : '') + body + '</fieldset></div>' +
      '<aside class="side"><nav class="qnav only-d" aria-label="질문 목록"><p class="qlist-title">필수 질문 6개</p><ol class="qlist">' + list + '</ol></nav>' + nextCta('q' + n) + '</aside></div>';
  }
  function nextCta(r) {
    var next = { q1: 'q2', q2: 'q3', q3: 'q4', q4: 'q5', q5: 'q6', q6: 'review' }[r];
    var label = r === 'q6' ? '입력한 내용 확인하기' : '다음 질문';
    return cta(primary(label, 'data-go="' + next + '" data-next', valid(r)) +
      '<p class="hint" data-next-hint' + (valid(r) ? ' hidden' : '') + '>답을 고르거나 입력하면 넘어갈 수 있어요</p>', ' ');
  }
  function radios(name, key, list) {
    return '<div class="choices">' + list.map(function (o) {
      return '<label class="choice"><input type="radio" name="' + name + '" value="' + o[0] + '" data-f="' + key + '"' + (S.f[key] === o[0] ? ' checked' : '') + '><span>' + o[1] + (o[2] ? '<small>' + o[2] + '</small>' : '') + '</span></label>';
    }).join('') + '</div>';
  }
  function vQ1() {
    return qShell(1, '일하는 곳에 직원이<br>몇 명인가요?', '사장님은 빼고, 아르바이트·일용직까지 평소 함께 일하는 사람 수예요.',
      radios('size', 'size', [['five', '5명 이상'], ['under', '5명 미만'], ['unsure', '잘 모르겠어요']]) +
      (S.f.size === 'unsure' ? '<p class="note" role="status">괜찮아요. 5명 이상으로 계산하고 결과에 \'추정\'이라고 표시할게요.</p>' : '') +
      '<details class="more"><summary>왜 물어보나요?</summary><p class="lead">직원이 5명보다 적은 곳은 연장·야간·휴일에 더 얹어 주는 수당이 법으로 정해져 있지 않아요. 주휴수당과 퇴직금은 직원 수와 상관없이 받을 수 있어요.</p></details>');
  }
  function vQ2() {
    return qShell(2, '언제부터 일했나요?', '근로계약서에 적힌 날이나, 처음 출근한 날을 골라 주세요.',
      '<div class="field"><label for="start">입사일</label><input id="start" class="input" type="date" data-f="start" value="' + esc(S.f.start) + '" max="2026-12-31"></div>');
  }
  function vQ3() {
    var f = S.f;
    var bad = f.employed === 'no' && f.end && f.start && f.end < f.start;
    return qShell(3, '지금도 그곳에서<br>일하고 있나요?', '그만뒀다면 퇴직금도 함께 계산해요.',
      radios('employed', 'employed', [['yes', '지금도 다니고 있어요'], ['no', '그만뒀어요']]) +
      (f.employed === 'no' ? '<div class="field"><label for="end">마지막으로 일한 날</label><input id="end" class="input" type="date" data-f="end" value="' + esc(f.end) + '"' + (bad ? ' aria-invalid="true" aria-describedby="end-err"' : '') + '>' +
        (bad ? '<p id="end-err" class="err">입사일보다 빠른 날이에요. 날짜를 다시 골라 주세요.</p>' : '') + '</div>' : ''));
  }
  function vQ4() {
    var f = S.f;
    var days = DAYS.map(function (d, i) {
      return '<button type="button" class="day" data-act="day" data-i="' + i + '" aria-pressed="' + f.days[i] + '">' + d + '</button>';
    }).join('');
    function sel(id, key, from, to, label) {
      var o = '<option value="">고르기</option>';
      for (var h = from; h <= to; h++) o += '<option value="' + h + '"' + (String(h) === f[key] ? ' selected' : '') + '>' + hh(h) + '</option>';
      return '<div class="field"><label for="' + id + '">' + label + '</label><select id="' + id + '" class="input" data-f="' + key + '">' + o + '</select></div>';
    }
    var brk = [['0', '없음'], ['0.5', '30분'], ['1', '1시간'], ['1.5', '1시간 30분']].map(function (b) {
      return '<option value="' + b[0] + '"' + (f.brk === b[0] ? ' selected' : '') + '>' + b[1] + '</option>';
    }).join('');
    var h = hours(), cnt = f.days.filter(Boolean).length;
    var sum = '';
    if (f.tStart !== '' && f.tEnd !== '' && f.brk !== '' && h <= 0) sum = '<p class="err" role="alert">퇴근 시각이 출근 시각보다 늦어야 해요.</p>';
    else if (h > 0 && cnt) sum = '<p class="sumline" role="status">하루 <strong>' + h + '시간</strong> · 일주일 <strong>' + (h * cnt) + '시간</strong>으로 계산할게요.</p>';
    return qShell(4, '평소 일한 요일과<br>시간을 골라 주세요', '',
      '<div class="stack"><span class="label" id="days-label">일한 요일 <span class="opt">(여러 개 고르기)</span></span><div class="days" role="group" aria-labelledby="days-label">' + days + '</div></div>' +
      '<div class="grid2">' + sel('tstart', 'tStart', 5, 14, '출근 시각') + sel('tend', 'tEnd', 12, 24, '퇴근 시각') + '</div>' +
      '<div class="field"><label for="brk">쉬는 시간</label><select id="brk" class="input" data-f="brk"><option value="">고르기</option>' + brk + '</select></div>' +
      sum +
      '<details class="more"><summary>요일마다 시간이 달랐어요</summary><p class="lead">실제 서비스에서는 요일별로 따로 고를 수 있어요. 교대 근무였다면 노동청 상담을 함께 받아 보세요.</p></details>');
  }
  function vQ5() {
    var f = S.f;
    var w = Number(f.wage);
    var warn = f.wageType === 'hour' && w > 0 && w < MIN_WAGE
      ? '<p class="warnbox" role="status">2026년 최저시급(10,320원)보다 적어요. 실제로 받은 돈이 아니라 약속한 금액이 맞는지 확인해 주세요.</p>' : '';
    return qShell(5, '급여는 어떻게<br>약속했나요?', '실제로 받은 돈이 아니라, 처음에 약속한 금액이에요.',
      radios('wageType', 'wageType', [['hour', '시급'], ['day', '일급'], ['month', '월급']]) +
      '<div class="field"><label for="wage">약속한 금액 (원)</label><input id="wage" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="wage" data-live value="' + esc(f.wage) + '"><p class="readable" data-readable="wage" aria-live="polite">' + readable(f.wage) + '</p></div>' + warn);
  }
  function vQ6() {
    var f = S.f;
    function num(id, key, label, unit) {
      return '<div class="field"><label for="' + id + '">' + label + ' <span class="opt">(' + unit + ')</span></label><input id="' + id + '" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="' + key + '" data-live value="' + esc(f[key]) + '"></div>';
    }
    return qShell(6, '못 받은 달에 실제로<br>받은 돈은 얼마인가요?', '한 푼도 못 받았다면 0을 적어 주세요.',
      '<div class="field"><label for="paid">실제로 받은 돈 (원)</label><input id="paid" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="paid" data-live value="' + esc(f.paid) + '"><p class="readable" data-readable="paid" aria-live="polite">' + readable(f.paid) + '</p></div>' +
      '<details class="more"' + (f.ot || f.night || f.holiday || f.allowance || f.month ? ' open' : '') + '><summary>자세히 · 연장·야간·휴일 근무, 그 밖의 수당 <span class="opt" style="margin-left:4px">선택</span></summary><div class="stack" style="padding-bottom:8px">' +
      '<p class="hint">입력하지 않으면 결과에 0원이 아니라 \'정보 없음\'으로 나와요.</p>' +
      '<div class="field"><label for="month">못 받은 달 <span class="opt">(비우면 마지막으로 일한 달)</span></label><input id="month" class="input" type="month" data-f="month" value="' + esc(f.month) + '"></div>' +
      num('ot', 'ot', '정해진 시간보다 더 일한 시간', '시간') + num('night', 'night', '그중 밤 10시~아침 6시', '시간') + num('holiday', 'holiday', '휴일에 일한 시간', '시간') + num('allow', 'allowance', '매달 같은 금액으로 받은 수당', '원') +
      '</div></details>');
  }

  // ---------- 화면: 내용 확인 ----------
  function vReview() {
    var f = S.f;
    // key: 줄 이름 (S.src · S.low · S.unread · S.fieldErr 공통)
    function row(label, value, key, goTo) {
      var src = S.src[key];
      var low = S.low[key] && !S.ok[key];
      var checked = S.low[key] && S.ok[key];
      var err = S.fieldErr[key];
      var val = value ? '<span>' + esc(value) + '</span>' : '<span class="empty">' + (S.unread[key] ? t('review.unread') : '입력 안 함') + '</span>';
      var tag = src === 'doc' ? '<span class="tag">서류</span>' : src === 'self' ? '<span class="tag self">직접</span>' : '';
      return '<div class="row' + (low ? ' low' : '') + '"><div class="row-main"><dt>' + label + '</dt><dd>' + val + tag + (checked ? '<span class="checked">' + icon('check') + '확인함</span>' : '') + '</dd>' +
        '<button type="button" class="row-edit" data-go="' + goTo + '" aria-label="' + label + ' 수정">수정</button></div>' +
        (err ? '<p class="err" role="alert">' + esc(err) + '</p>' : '') +
        (low ? '<p class="row-note">흐리게 읽혔어요. 서류와 같은 값인가요?</p><button type="button" class="ok-btn" data-act="ok" data-k="' + key + '">' + icon('check') + '네, 맞아요</button>' : '') + '</div>';
    }
    var wageLabel = { hour: '시급', day: '일급', month: '월급' }[f.wageType] || '';
    var timeText = f.tStart !== '' && f.tEnd !== '' ? hh(f.tStart) + ' ~ ' + hh(f.tEnd) + (f.brk !== '' ? ' · 쉬는 시간 ' + ({ '0': '없음', '0.5': '30분', '1': '1시간', '1.5': '1시간 30분' }[f.brk]) : '') : f.dayHours ? f.dayHours + '시간' : '';
    var groups = [
      ['일한 곳', (f.company ? row('사업장 이름', f.company, 'company', 'q1') : '') + row('직원 수', { five: '5명 이상', under: '5명 미만', unsure: '모름 (5명 이상으로 추정)' }[f.size], 'size', 'q1')],
      ['일한 기간', row('입사일', krDate(f.start), 'start', 'q2') + row('퇴사일', f.employed === 'yes' ? '지금도 다니는 중' : krDate(f.end), 'end', 'q3')],
      ['근무 시간', row('일한 요일', dayText(), 'days', 'q4') + row('하루', timeText, 'time', 'q4')],
      ['약속한 급여', row(wageLabel || '급여', f.wage ? won(f.wage) : '', 'wage', 'q5') + row('매달 받는 수당', f.allowance ? won(f.allowance) : '', 'allowance', 'q6')],
      ['받은 돈 · 추가 근무', (f.month || S.unread.month ? row('못 받은 달', f.month ? Number(f.month.slice(0, 4)) + '년 ' + Number(f.month.slice(5)) + '월' : '', 'month', 'q6') : '') + row('실제 받은 돈', f.paid !== '' ? won(f.paid) : '', 'paid', 'q6') + row('연장 근무', f.ot ? f.ot + '시간' : '', 'ot', 'q6') + row('그중 야간', f.night ? f.night + '시간' : '', 'night', 'q6') + row('휴일 근무', f.holiday ? f.holiday + '시간' : '', 'holiday', 'q6')]
    ].map(function (g) { return '<section class="group"><h2>' + g[0] + '</h2><dl class="rows">' + g[1] + '</dl></section>'; }).join('');
    var left = lowLeft();
    var ok = valid('review');
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(2) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">읽은 내용이 맞는지<br>확인해 주세요</h1><p class="lead">잘못 읽힌 값이 있으면 계산 전체가 틀려져요.</p></div>' +
      groups + '</div>' +
      '<aside class="side"><div class="note stack"><h2>표시 보는 법</h2><div class="legend-row" style="flex-direction:column"><span><span class="tag">서류</span> 서류에서 읽은 값</span><span><span class="tag self">직접</span> 직접 입력한 값</span><span><span class="tag warn">노란 줄</span> 확인이 필요한 값</span></div></div>' +
      '<p class="hint">금액은 AI가 아니라 근로기준법 계산식으로만 정해져요.</p>' +
      cta(primary(t('calc.btn'), 'data-act="calculate"', ok) +
        (ok ? '' : '<p class="hint">' + (left ? '확인이 필요한 값이 ' + left + '개 남았어요' : '빠진 필수 항목을 채워 주세요') + '</p>'), ' ') +
      '</aside></div>';
  }

  // ---------- 화면: 결과 ----------
  function vResult() {
    var R = viewResult();
    var items = R.items.map(function (it) {
      var right = it.amount != null ? '<span class="item-amt">' + money(it.amount) + '</span>' : '<span class="item-status">' + esc(it.status) + '</span>';
      // 막대 길이는 서버가 준 금액의 비율일 뿐, 새 숫자를 만들지 않는다
      var share = it.amount > 0 && R.due > 0 ? '<div class="share" aria-hidden="true"><span style="width:' + Math.min(100, Math.max(2, it.amount / R.due * 100)).toFixed(1) + '%"></span></div>' : '';
      return '<li><div class="item-head"><strong>' + esc(it.label) + '</strong>' + (it.estimated ? '<span class="tag">' + t('result.estimated') + '</span>' : '') + right + '</div>' + share +
        '<p class="item-exp">' + esc(it.explain) + '</p>' +
        '<div class="item-links"><details class="basis"><summary>' + t('result.basis') + '</summary><dl lang="ko"><dt>' + t('result.article') + '</dt><dd><strong>' + esc(it.article) + '</strong></dd><dt>' + t('result.gist') + '</dt><dd>' + esc(it.gist) + '</dd><dt>' + t('result.formula') + '</dt><dd>' + esc(it.formula) + '</dd></dl></details>' +
        (it.missing ? '<button type="button" class="btn-text" style="font-size:var(--fs-sm)" data-go="q6">' + t('result.input') + '</button>' : '') + '</div></li>';
    }).join('');
    var size = t(S.f.size === 'under' ? 'size.under' : 'size.five');
    var period = dotDate(S.f.start) + ' ~ ' + (S.f.employed === 'yes' ? t('result.employed') : dotDate(S.f.end));
    var summary = '<div class="receipt">' +
      '<span class="total-label">' + t('result.total') + '</span>' +
      '<span class="total">' + R.total.toLocaleString('ko-KR') + '<small>' + t('unit') + '</small></span>' +
      (S.lang === 'ko' ? '<span class="total-read">' + readable(R.total) + '</span>' : '') +
      '<p class="disclaim">' + t('result.disclaim') + ' <button type="button" class="btn-text" data-act="disclaim">' + t('result.more') + '</button></p></div>' +
      '<p class="hint">' + esc(t('result.meta', { period: period, size: size, paid: money(R.paid) })) + '</p>';
    var actions = '<div class="cta">' + primary(t('result.make'), 'data-go="form"') +
      '<div class="btn-row"><button type="button" class="btn btn-line" data-act="print">' + icon('print') + '<span>' + t('result.pdf') + '</span></button><button type="button" class="btn btn-line" data-go="counselor">' + icon('chat') + '<span>' + t('result.counselor') + '</span></button></div>' +
      '<button type="button" class="btn-text" data-go="guide">' + t('result.guide') + '</button>' +
      '<p class="hint">' + t('result.expire') + '</p></div>';
    return '<div class="wide col">' + progress(3) +
      '<h1 tabindex="-1" class="sr">' + t('title.result') + '</h1>' +
      '<div class="result-grid"><div class="stack r-sum">' + summary + '</div>' +
      '<div class="r-act">' + actions + '</div>' +
      '<div class="stack-lg r-items"><section><div class="items-head"><h2>' + t('result.items') + '</h2><span class="hint">' + t('result.itemsHint') + '</span></div><ul class="items">' + items + '</ul></section>' +
      '<details class="more"><summary>' + t('result.base') + '</summary><dl class="kv" style="padding-bottom:8px"><dt>' + t('result.hourly') + '</dt><dd>' + R.hourly + '</dd><dt>' + t('result.daily') + '</dt><dd>' + R.daily + '</dd><dt>' + t('result.notApplied') + '</dt><dd lang="ko">' + esc(R.notApplied.join(' ') || t('result.none')) + '</dd></dl>' +
      '<button type="button" class="btn-text" data-go="review">' + t('result.review') + '</button></details></div></div></div>';
  }

  // ---------- 화면: 진정서 정보 ----------
  function vForm() {
    var R = viewResult();
    var p = S.p;
    function inp(id, key, label, type, opts) {
      opts = opts || {};
      var miss = (opts.req && S.formTried && !p[key] && opts.req) || S.pErr[key];
      return '<div class="field"><label for="' + id + '">' + label + (opts.req ? '' : ' <span class="opt">(선택)</span>') + '</label>' +
        '<input id="' + id + '" class="input" type="' + type + '"' + (opts.mode ? ' inputmode="' + opts.mode + '"' : '') + (opts.ac ? ' autocomplete="' + opts.ac + '"' : '') + (opts.ph ? ' placeholder="' + opts.ph + '"' : '') +
        ' data-p="' + key + '" value="' + esc(p[key]) + '"' + (miss ? ' aria-invalid="true" aria-describedby="' + id + '-err"' : '') + '>' +
        (miss ? '<p id="' + id + '-err" class="err">' + esc(miss) + '</p>' : '') + '</div>';
    }
    var auto = '<dl class="kv"><dt>사업장</dt><dd>' + esc(S.f.company || '[사업장 이름]') + ' · ' + (S.f.size === 'under' ? '5명 미만' : '5명 이상') + '</dd><dt>일한 기간</dt><dd>' + esc(dotDate(S.f.start)) + ' ~ ' + esc(S.f.employed === 'yes' ? '재직 중' : dotDate(S.f.end)) + '</dd><dt>못 받은 돈</dt><dd><strong>' + won(R.total) + '</strong></dd></dl>';
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(4) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">진정서에 들어갈 내용을<br>적어 주세요</h1><p class="lead">계산 결과와 근무 정보는 자동으로 들어가요. 적은 정보는 초안을 만드는 데만 쓰고 저장하지 않아요.</p></div>' +
      '<section class="note stack only-m"><h2>자동으로 들어가는 내용</h2>' + auto +
      '<button type="button" class="btn-text" style="align-self:flex-start" data-go="review">내용 고치기</button></section>' +
      '<fieldset class="stack"><legend><h2>나 (진정인)</h2></legend>' +
      inp('p-name', 'name', '이름', 'text', { req: '이름을 적어 주세요', ac: 'name' }) +
      inp('p-phone', 'phone', '휴대폰 번호', 'tel', { req: '연락받을 번호를 적어 주세요', mode: 'tel', ac: 'tel', ph: '010-0000-0000' }) +
      inp('p-addr', 'addr', '사는 곳 주소', 'text', { ac: 'street-address' }) + '</fieldset>' +
      '<fieldset class="stack"><legend><h2>일한 곳 (피진정인)</h2></legend>' +
      (S.f.company ? '' : inp('p-company', 'company', '사업장 이름', 'text', { req: '사업장 이름을 적어 주세요' })) +
      inp('p-owner', 'owner', '사장님 이름', 'text', { req: '사장님 이름을 적어 주세요. 모르면 \'모름\'이라고 적어도 돼요' }) +
      inp('p-waddr', 'waddr', '사업장 주소', 'text', { req: '사업장 주소를 적어 주세요' }) +
      inp('p-wphone', 'wphone', '사업장 전화번호', 'tel', { mode: 'tel' }) +
      inp('p-job', 'job', '내가 한 일', 'text', { req: '어떤 일을 했는지 적어 주세요', ph: '예: 주방 보조' }) + '</fieldset>' +
      '<div class="field"><label for="p-facts">있었던 일 <span class="opt">(선택)</span></label><p id="facts-help" class="hint">언제부터 못 받았는지, 사장님이 뭐라고 했는지 편하게 적어 주세요. 문장은 진정서에 맞게 다듬어 드려요.</p>' +
      '<textarea id="p-facts" class="input" rows="5" aria-describedby="facts-help" data-p="facts">' + esc(p.facts) + '</textarea>' +
      (S.pErr.facts ? '<p class="err">' + esc(S.pErr.facts) + '</p>' : '') + '</div>' +
      '</div>' +
      '<aside class="side"><section class="note stack only-d"><h2>자동으로 들어가는 내용</h2>' + auto + '<button type="button" class="btn-text" style="align-self:flex-start" data-go="review">내용 고치기</button></section>' +
      '<p class="hint">' + icon('lock') + ' 적은 이름·주소는 AI에 보내지 않고, 초안을 만든 뒤 저장하지 않아요.</p>' +
      cta(primary('진정서 초안 만들기', 'data-act="make"'), ' ') + '</aside></div>' +
      '<dialog id="confirm" aria-labelledby="confirm-title"><div class="stack"><h2 id="confirm-title">만들기 전에 확인해 주세요</h2>' +
      '<p>이 초안은 법률 자문이 아니라, 진정서 서식을 채우는 걸 돕는 자료예요. 내용이 사실과 같은지는 직접 확인해야 해요.</p>' +
      '<ul class="hint" style="margin:0;padding-left:18px"><li>금액은 계산 결과 그대로 들어가요</li><li>있었던 일 문단은 미리보기에서 고칠 수 있어요</li></ul>' +
      primary(t('make.btn'), 'data-act="confirm-make"') +
      '<button type="button" class="btn-text" data-act="close-dialog">돌아가기</button></div></dialog>';
  }

  // ---------- 진정서 내용 (complaint-draft 응답) ----------
  // 금액이 든 문단은 고칠 수 없고, '진정 내용' 문단만 고칠 수 있다
  var EDITABLE = '진정 내용';
  function draftSections() {
    return S.draft.draftKo.sections.map(function (s) {
      return s.heading === EDITABLE && S.factsText != null ? { heading: s.heading, body: S.factsText } : s;
    });
  }
  function plainText() {
    var out = [S.draft.draftKo.title, ''];
    draftSections().forEach(function (s) { out.push('[' + s.heading + ']', s.body, ''); });
    out.push('2026년 11월    일', '진정인 ' + (S.p.name || '[이름]') + ' (서명)');
    return out.join('\n');
  }

  // ---------- 화면: 진정서 초안 ----------
  function vComplaint() {
    var d = S.draft;
    // 번역은 참고용. 문단 순서가 한국어 원문과 같다고 보고 나란히 붙인다
    var trs = d.translation ? d.translation.sections : [];
    var trLang = d.translation ? d.translation.lang : 'ko';
    var showTr = S.tr && trs.length > 0;
    var ko = draftSections().map(function (s, i) {
      var editable = s.heading === EDITABLE;
      var body;
      if (editable && S.editing) {
        body = '<label class="sr" for="facts-edit">진정 내용 고치기</label><textarea id="facts-edit" class="input" rows="7">' + esc(s.body) + '</textarea>' +
          '<div class="btn-row no-print"><button type="button" class="btn btn-line" data-act="facts-cancel">취소</button><button type="button" class="btn btn-primary" style="min-height:48px;font-size:1rem" data-act="facts-save">이대로 저장</button></div>';
      } else {
        body = '<p>' + esc(s.body) + '</p>' + (editable ? '<button type="button" class="small-btn no-print" data-act="facts-edit">' + icon('pen') + '이 문단 고치기</button>' : '');
      }
      var head = '<h3>' + esc(s.heading) + (editable ? '<span>AI가 다듬은 문장</span>' : '') + '</h3>';
      var inline = showTr && trs[i] ? '<p class="tr-inline" lang="' + trLang + '">' + esc(trs[i].body) + '</p>' : '';
      return '<section>' + head + body + inline + '</section>';
    }).join('');
    var tr = trs.map(function (s) {
      return '<section><h3>' + esc(s.heading) + '</h3><p>' + esc(s.body) + '</p></section>';
    }).join('');
    var seg = trs.length ?
      '<div class="seg no-print" role="group" aria-label="보기 방식"><button type="button" data-act="tr" data-v="0" aria-pressed="' + !S.tr + '">한국어 원문</button><button type="button" data-act="tr" data-v="1" aria-pressed="' + S.tr + '">번역 함께 보기</button></div>' : '';
    return '<div class="wide col stack-lg">' + progress(4) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">진정서 초안</h1><p class="note">법률 자문이 아닌 서식 작성 보조 자료예요. 사실과 같은지 확인한 뒤 내세요. 노동청에는 한국어 원문만 내요.</p>' +
      '<p class="hint no-print">금액은 계산 결과에서 가져와 여기서는 고칠 수 없어요. 고치려면 <button type="button" class="btn-text" style="font-size:inherit" data-go="review">입력한 내용을 바꿔 다시 계산</button>해 주세요.</p></div>' +
      seg +
      '<div class="paper-grid' + (showTr ? ' tr-on' : '') + '">' +
      '<article class="paper" lang="ko"><h2 class="doc-title">' + esc(d.draftKo.title) + '</h2>' + ko +
      '<p class="sign">2026년 11월 [  ]일<br>진정인 ' + esc(S.p.name || '[이름]') + ' (서명)</p>' +
      '<p class="foot">' + esc(d.disclaimer) + ' 제출 전 내용이 사실과 같은지 확인하세요.</p></article>' +
      (showTr ? '<article class="paper tr-col" lang="' + trLang + '" aria-label="참고용 번역"><p class="hint">' + ({ en: 'English', vi: 'Tiếng Việt' }[trLang] || trLang) + ' · 참고용 번역</p>' + tr + '</article>' : '') +
      '</div>' +
      (S.copyFallback ? '<div class="field no-print"><label for="copybox">아래 글을 길게 눌러 전체 선택 후 복사해 주세요</label><textarea id="copybox" class="input copybox" readonly>' + esc(plainText()) + '</textarea></div>' : '') +
      '</div>' +
      cta(primary('PDF로 저장', 'data-act="print"') +
        '<div class="btn-row"><button type="button" class="btn btn-line" data-act="print">' + icon('print') + '<span>인쇄</span></button><button type="button" class="btn btn-line" data-act="copy">' + icon('copy') + '<span>전체 복사</span></button></div>' +
        '<p class="hint">전체 복사는 노동포털 온라인 진정 입력란에 붙여 넣을 때 써요.</p>' +
        '<button type="button" class="btn-text" data-go="guide">어디에 어떻게 내나요?</button>', 'col wide');
  }

  // ---------- 화면: 절차 ----------
  function vGuide() {
    var steps = [1, 2, 3, 4, 5, 6].map(function (n) {
      return '<li><div class="tl-mark" aria-hidden="true"><span class="tl-num">' + n + '</span>' + (n < 6 ? '<span class="tl-line"></span>' : '') + '</div>' +
        '<div class="tl-body"><strong>' + t('guide.s' + n) + '</strong><p>' + t('guide.s' + n + 'b') + '</p>' +
        (n === 1 ? '<div class="links"><a href="https://labor.moel.go.kr" target="_blank" rel="noopener">' + t('guide.link1') + icon('out') + '</a><a href="https://www.moel.go.kr" target="_blank" rel="noopener">' + t('guide.link2') + icon('out') + '</a></div>' : '') + '</div></li>';
    }).join('');
    function phone(name, sub, num, tel) {
      return '<a class="phone" href="tel:' + tel + '">' + icon('phone') + '<span><strong>' + name + '</strong><small>' + sub + '</small></span><b>' + num + '</b></a>';
    }
    var bring = [1, 2, 3, 4].map(function (n) { return '<li>' + icon('check') + t('guide.bring' + n) + '</li>'; }).join('');
    return '<div class="split">' +
      '<div class="stack-lg">' +
      '<div class="stack"><span class="eyebrow">' + t('title.guide') + '</span><h1 tabindex="-1">' + t('guide.title') + '</h1><p class="lead">' + t('guide.lead') + '</p></div>' +
      '<ol class="timeline">' + steps + '</ol>' +
      '<section class="stack"><h2>' + t('guide.bring') + '</h2><ul class="bring">' + bring + '</ul></section></div>' +
      '<aside class="side">' +
      '<button type="button" class="callout" data-go="counselor">' + icon('chat', 'ico-lg') + '<span><strong>' + t('guide.callout') + '</strong><small>' + t('guide.calloutSub') + '</small></span>' + icon('arrow-r') + '</button>' +
      '<section class="note stack"><h2>' + t('guide.help') + '</h2><div class="phones">' +
      phone(t('guide.p1'), t('guide.p1s'), '1350', '1350') +
      phone(t('guide.p2'), t('guide.p2s'), '1577-0071', '15770071') +
      phone(t('guide.p3'), t('guide.p3s'), '132', '132') +
      '</div><p class="hint">' + t('guide.phoneHint') + '</p></section>' +
      cta(primary(t('guide.cta'), 'data-go="form"'), ' ') + '</aside></div>';
  }

  // ---------- 화면: 상담원 ----------
  function vCounselor() {
    var R = viewResult();
    var f = S.f, p = S.p;
    var top = { ko: '상담원에게 휴대폰을 그대로 보여 주세요.', en: 'This page is in Korean so the counselor can read it. Hand your phone to them.', vi: 'Trang này bằng tiếng Hàn để nhân viên tư vấn đọc được. Hãy đưa điện thoại cho họ.' }[S.lang];
    var langName = { ko: '', en: '영어', vi: '베트남어' }[S.lang];
    var rows = R.items.map(function (it) {
      return '<tr><th scope="row" style="text-align:left;font-weight:400">' + it.label + (it.estimated ? ' (추정)' : '') + '</th><td style="text-align:right;font-weight:700">' + (it.amount != null ? won(it.amount) : it.status) + '</td></tr>';
    }).join('');
    return '<div class="col stack-lg">' +
      '<p class="banner" lang="' + S.lang + '">' + esc(top) + '</p>' +
      '<article class="paper counsel" lang="ko"><div class="stack" style="gap:6px"><h1 tabindex="-1">임금체불 상담 요청</h1>' +
      (langName ? '<p>저는 ' + langName + '를 씁니다. 통역이 필요할 수 있습니다.</p>' : '') + '</div>' +
      '<div class="big-sum"><span class="total-label">받지 못한 돈 (참고 계산)</span><span class="total" style="font-size:2rem">' + won(R.total) + '</span><span>' + readable(R.total) + '</span></div>' +
      '<dl class="kv"><dt>사업장</dt><dd>' + esc(f.company || p.company || '[사업장 이름]') + ' · ' + (f.size === 'under' ? '5명 미만' : '5명 이상') + '</dd>' +
      '<dt>일한 기간</dt><dd>' + esc(dotDate(f.start)) + ' ~ ' + esc(f.employed === 'yes' ? '재직 중' : dotDate(f.end) + ' (퇴직)') + '</dd>' +
      '<dt>근무</dt><dd>' + esc(p.job || '') + (p.job ? ' · ' : '') + esc(dayText()) + (f.tStart !== '' ? ' ' + hh(f.tStart) + '~' + hh(f.tEnd) : '') + '</dd>' +
      '<dt>약속한 급여</dt><dd>' + ({ hour: '시급 ', day: '일급 ', month: '월 ' }[f.wageType] || '') + (f.wage ? won(f.wage) : '') + (f.allowance ? ' + 수당 ' + won(f.allowance) : '') + '</dd>' +
      '<dt>받은 돈</dt><dd>' + (f.paid !== '' ? won(f.paid) : '') + '</dd></dl>' +
      '<table style="width:100%;border-collapse:collapse"><caption>항목별 금액</caption><tbody>' + rows + '</tbody></table>' +
      '<p><strong>원하는 것</strong><br>밀린 임금' + (f.employed === 'yes' ? '을' : '과 퇴직금을') + ' 받고 싶습니다. 진정 절차를 안내해 주세요.</p>' +
      '<p class="foot">근로자가 입력한 내용으로 계산한 참고 자료입니다. 법률 자문이 아닙니다.</p></article></div>' +
      cta('<div class="btn-row"><button type="button" class="btn btn-line" data-act="print">' + icon('print') + '<span>PDF 저장</span></button><button type="button" class="btn btn-primary" data-act="back">돌아가기</button></div>');
  }

  var VIEWS = { start: vStart, upload: vUpload, q1: vQ1, q2: vQ2, q3: vQ3, q4: vQ4, q5: vQ5, q6: vQ6, review: vReview, result: vResult, form: vForm, complaint: vComplaint, guide: vGuide, counselor: vCounselor };

  function render(moveFocus) {
    document.documentElement.classList.toggle('big', !!S.big);
    document.documentElement.lang = TRANSLATED.indexOf(S.route) >= 0 ? S.lang : 'ko';
    app.innerHTML = topbar() + '<main id="main" class="wrap' + (S.route === 'start' ? ' start-bg' : '') + '"><div' + (moveFocus ? ' class="enter"' : '') + '>' + VIEWS[S.route]() + '</div></main>';
    if (moveFocus) {
      window.scrollTo(0, 0);
      var h = app.querySelector('h1');
      if (h) h.focus({ preventScroll: true });
    }
    save();
  }

  // ---------- 동작 ----------
  // POST /documents/extract 응답 → 확인 화면 값. 0.8 미만은 노란 줄, unreadable 은 빈칸
  // 서류를 하나씩 올리면 앞 서류에서 읽은 값에 덧붙인다
  function applyExtract(res) {
    var f = S.f;
    var weekly = 0;
    res.fields.forEach(function (x) {
      var v = x.value, row = ROW_OF[x.key];
      if (!row || v == null) return;
      if (x.key === 'companyName') f.company = String(v);
      else if (x.key === 'employeeCount') f.size = Number(v) >= 5 ? 'five' : 'under';
      else if (x.key === 'startDate') f.start = v;
      else if (x.key === 'endDate') { f.end = v; f.employed = 'no'; }
      // 주 n일 → 월요일부터 n일로 둔다 (확인 화면에서 고칠 수 있음)
      else if (x.key === 'workDaysPerWeek') f.days = DAYS.map(function (_, i) { return i < Number(v); });
      else if (x.key === 'dailyContractHours') f.dayHours = String(v);
      else if (x.key === 'weeklyContractHours') weekly = Number(v);
      else if (x.key === 'wageType') f.wageType = { HOURLY: 'hour', DAILY: 'day', MONTHLY: 'month' }[v] || '';
      else if (x.key === 'baseWage') f.wage = String(v);
      else if (x.key === 'allowances') f.allowance = String(v.reduce(function (s, a) { return s + Number(a.amount); }, 0) || '');
      else if (x.key === 'payMonth') f.month = v;
      else if (x.key === 'paidAmount') f.paid = String(v);
      else f[row] = String(v);
      S.src[row] = 'doc';
      S.low[row] = x.confidence < 0.8;
      S.ok[row] = false;
    });
    var cnt = f.days.filter(Boolean).length;
    if (!f.dayHours && weekly && cnt) f.dayHours = String(weekly / cnt);
    (res.unreadable || []).forEach(function (k) { if (ROW_OF[k]) S.unread[ROW_OF[k]] = true; });
    S.up.n = res.fields.length;
    S.up.unread = (res.unreadable || []).length;
    S.up.masked = res.maskedCount || 0;
  }
  function handleFile(input) {
    var file = input.files && input.files[0];
    if (!file) return;
    var name = file.name.toLowerCase();
    var okType = /\.(pdf|jpe?g|png)$/.test(name) || /^(application\/pdf|image\/jpeg|image\/png)$/.test(file.type);
    if (/\.hwp$/.test(name)) S.up = { status: 'error', name: file.name, msg: t('up.hwp') };
    else if (!okType) S.up = { status: 'error', name: file.name, msg: t('err.415') };
    else if (file.size > MAX_SIZE) S.up = { status: 'error', name: file.name, msg: t('err.413') };
    else {
      var form = new FormData();
      form.append('file', file);
      form.append('docType', S.docType.toUpperCase());
      S.up = { status: 'reading', name: file.name, msg: '' };
      api('/api/v1/documents/extract', form).then(function (res) {
        applyExtract(res);
        S.up.status = 'done';
        toast(t('up.toast'));
      }, function (e) {
        // 422 는 직접 입력 안내, 나머지(413·415·429 등)는 안내 문구
        S.up = { status: e.status === 422 ? 'fail' : 'error', name: file.name, msg: e.message };
      }).then(function () {
        if (S.route === 'upload') render(); else save();
      });
    }
    render();
  }
  function setField(el) {
    var key = el.dataset.f;
    var v = el.value;
    if (el.hasAttribute('data-live')) {
      v = v.replace(/[^0-9]/g, '').replace(/^0+(?=\d)/, '');
      if (el.value !== v) el.value = v;
    }
    S.f[key] = v;
    var srcKey = (key === 'tStart' || key === 'tEnd' || key === 'brk') ? 'time' : key === 'wageType' ? 'wage' : key === 'employed' ? 'end' : key;
    S.src[srcKey] = 'self';
    S.low[srcKey] = false;
    S.fieldErr[srcKey] = '';
    save();
  }
  function refreshNext() {
    var r = S.route;
    var ok = valid(r);
    var btn = app.querySelector('[data-next]');
    if (btn) btn.disabled = !ok;
    var hint = app.querySelector('[data-next-hint]');
    if (hint) hint.hidden = ok;
    app.querySelectorAll('[data-readable]').forEach(function (el) { el.textContent = readable(S.f[el.dataset.readable]); });
  }
  function doPrint() {
    if (!inFrame && typeof window.print === 'function') { window.print(); return; }
    toast('실제 서비스에서는 여기서 PDF 저장 창이 열려요. (프로토타입)');
  }
  function doCopy() {
    var text = plainText();
    var fail = function () { S.copyFallback = true; render(); var b = document.getElementById('copybox'); if (b) { b.focus(); b.select(); } toast('복사가 막혀 있어요. 아래 글을 직접 복사해 주세요'); };
    try {
      navigator.clipboard.writeText(text).then(function () { toast('진정서 전체를 복사했어요'); }, fail);
    } catch (e) { fail(); }
  }
  function make() {
    var p = S.p;
    var need = ['name', 'phone', 'owner', 'waddr', 'job'];
    if (!S.f.company) need.push('company');
    S.formTried = true;
    var missing = need.filter(function (k) { return !p[k]; });
    if (missing.length) {
      render();
      var first = app.querySelector('[aria-invalid="true"]');
      if (first) first.focus();
      toast('빠진 칸 ' + missing.length + '개를 채워 주세요');
      return;
    }
    document.getElementById('confirm').showModal();
  }
  // 언어를 바꾸면 문구 파일을 받고, 해설도 그 언어로 다시 받는다
  function setLang(v) {
    S.lang = v;
    return loadLang(v).then(function () {
      render();
      if (S.calc) loadExplanation();
    });
  }
  function toastT(k) { toast(t(k)); }

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-go],[data-act]');
    if (!t || t.disabled) return;
    if (t.dataset.go !== undefined && t.dataset.go !== '') { go(t.dataset.go); return; }
    var a = t.dataset.act;
    if (a === 'back') back();
    else if (a === 'big') { S.big = !S.big; render(); }
    else if (a === 'lang') setLang(t.dataset.v);
    else if (a === 'path') {
      S.path = t.dataset.v;
      // 서류로 시작하거나 서류 없이 직접 입력하면 빈 칸부터
      if (S.path === 'doc' || S.up.status !== 'done') { S.f = blankFields(); S.src = {}; S.low = {}; S.ok = {}; S.unread = {}; S.fieldErr = {}; }
      if (S.path === 'doc') S.up = { status: 'idle', name: '', msg: '' };
      go(S.path === 'manual' ? 'q1' : 'upload');
    }
    else if (a === 'day') { var i = Number(t.dataset.i); S.f.days[i] = !S.f.days[i]; S.src.days = 'self'; S.low.days = false; render(); var again = app.querySelector('[data-act="day"][data-i="' + i + '"]'); if (again) again.focus(); }
    else if (a === 'ok') { S.ok[t.dataset.k] = true; render(); }
    else if (a === 'disclaim') toastT('result.disclaimToast');
    else if (a === 'calculate') calculate(t);
    else if (a === 'print') doPrint();
    else if (a === 'copy') doCopy();
    else if (a === 'make') make();
    else if (a === 'confirm-make') makeDraft(t);
    else if (a === 'close-dialog') { var d2 = document.getElementById('confirm'); if (d2) d2.close(); }
    else if (a === 'tr') { S.tr = t.dataset.v === '1'; render(); }
    else if (a === 'facts-edit') { S.editing = true; render(); var ta = document.getElementById('facts-edit'); if (ta) ta.focus(); }
    else if (a === 'facts-cancel') { S.editing = false; render(); }
    else if (a === 'facts-save') { S.factsText = document.getElementById('facts-edit').value; S.editing = false; render(); toast('진정 내용을 고쳤어요'); }
  });
  document.addEventListener('change', function (e) {
    var t = e.target;
    var a = t.dataset.act;
    if (a === 'agree') { S.agreed = t.checked; render(); var c = document.getElementById('agree'); if (c) c.focus(); }
    else if (a === 'lang-select') setLang(t.value).then(function () { var s = document.getElementById('lang-top'); if (s) s.focus(); });
    else if (a === 'doctype') { S.docType = t.value; render(); var r2 = app.querySelector('[data-act="doctype"][value="' + t.value + '"]'); if (r2) r2.focus(); }
    else if (a === 'file') handleFile(t);
    else if (t.dataset.f !== undefined && !t.hasAttribute('data-live')) {
      setField(t);
      var id = t.id, val = t.value;
      render();
      var back2 = id ? document.getElementById(id) : app.querySelector('[data-f="' + t.dataset.f + '"][value="' + val + '"]');
      if (back2) back2.focus();
    }
  });
  document.addEventListener('input', function (e) {
    var t = e.target;
    if (t.hasAttribute('data-live')) { setField(t); refreshNext(); }
    else if (t.dataset.p !== undefined) { S.p[t.dataset.p] = t.value; S.pErr[t.dataset.p] = ''; save(); }
  });

  var start = (location.hash || '').slice(1);
  if (ALL.indexOf(start) >= 0 && (start === 'start' || S.agreed)) S.route = start;
  if (NEED_CALC.indexOf(S.route) >= 0 && !S.calc) S.route = 'review';
  if (S.route === 'complaint' && !S.draft) S.route = 'form';
  // 한국어(기본 문구)와 고른 언어 문구를 받은 뒤 그린다
  Promise.all([loadLang('ko'), loadLang(S.lang)]).then(function () { render(false); });
})();
