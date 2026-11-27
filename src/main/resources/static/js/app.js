(function () {
  'use strict';

  // 입력값은 이 휴대폰에만(localStorage), 이름 · 연락처 · 진정서는 탭을 닫으면 지워지게(sessionStorage)
  var KEY = 'wageback-v2', PKEY = 'wageback-v2-p';
  var DAYS = ['월', '화', '수', '목', '금', '토', '일'];
  var MIN_WAGE = 10320;
  var MAX_SIZE = 10 * 1024 * 1024;
  var ALL = ['start', 'sit', 'upload', 'q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'qx', 'cal', 'review', 'result', 'form', 'complaint', 'guide', 'counselor', 'help', 'ready', 'daeji', 'words', 'info', 'contract', 'deadline', 'after', 'letter'];
  // 동의 없이 바로 볼 수 있는 안내 화면 (시작 화면의 '더 확인할 수 있는 것' · 쉬운 말 사전)
  var FREE = ['sit', 'contract', 'deadline', 'after', 'words'];
  var STEP = { upload: 1, q1: 1, q2: 1, q3: 1, q4: 1, q5: 1, q6: 1, qx: 1, cal: 1, review: 2, result: 3, form: 4, complaint: 4, letter: 4 };
  var TITLE = { sit: 'title.sit', upload: 'title.upload', q1: 'title.q', q2: 'title.q', q3: 'title.q', q4: 'title.q', q5: 'title.q', q6: 'title.q', qx: 'title.q', cal: 'title.cal', review: 'title.review', result: 'title.result', form: 'title.form', complaint: 'title.complaint', letter: 'title.letter', guide: 'title.guide', counselor: 'title.counselor', help: 'title.help', ready: 'title.ready', daeji: 'title.daeji', words: 'title.words', info: 'title.info', contract: 'title.contract', deadline: 'title.deadline', after: 'title.after' };
  var NEED_CALC = ['result', 'form', 'complaint', 'counselor', 'letter'];
  // 결과 뒤 둘러보는 화면: 휴대폰은 아래 탭 막대, 넓은 화면은 위 탭으로 오간다 (입력 흐름 중에는 숨김)
  var TABS = [['result', 'coin'], ['guide', 'list'], ['help', 'pin'], ['ready', 'check']];
  var TAB_OF = { result: 'result', counselor: 'result', guide: 'guide', help: 'help', ready: 'ready', daeji: 'guide', words: 'guide', info: 'guide', contract: 'guide', deadline: 'guide', after: 'guide' };
  // 상황 진단: 시작 화면에서 여러 개 고른다 [코드, 아이콘]. 고른 것에 맞춰 질문 · 안내가 바뀐다
  var SITS = ['less', 'late', 'sev', 'fired', 'extra', 'minw', 'deduct', 'leave', 'payslip', 'shut', 'over14'];
  var mobile = window.matchMedia('(max-width: 959px)');
  // 카메라로 바로 찍기는 손가락으로 쓰는 기기(휴대폰 · 태블릿)에서만. PC 는 파일 고르기만 (화면 너비가 아니라 입력 방식으로 판단)
  var touch = window.matchMedia('(pointer: coarse)');
  // 쉬운 말 사전: 언어 파일의 w.<id> (말) · w.<id>.d (풀이) · w.<id>.law (근거, 한국어 파일에만)
  var WORDS = ['ordinaryWage', 'averageWage', 'weeklyHoliday', 'overtime', 'night', 'holidayWork', 'severance', 'minWage', 'underFive', 'complaint', 'laborOffice', 'daeji'];
  var ITEM_WORD = { OVERTIME: 'overtime', NIGHT: 'night', HOLIDAY: 'holidayWork', WEEKLY_HOLIDAY: 'weeklyHoliday', SEVERANCE: 'severance', MIN_WAGE_SHORTFALL: 'minWage' };
  // 추출 키 → 확인 화면 줄 이름
  var ROW_OF = { companyName: 'company', employeeCount: 'size', startDate: 'start', endDate: 'end', workDaysPerWeek: 'days', dailyContractHours: 'time', weeklyContractHours: 'time', wageType: 'wage', baseWage: 'wage', allowances: 'allowance', payMonth: 'month', paidAmount: 'paid', overtimeHours: 'ot', nightHours: 'night', holidayHours: 'holiday' };
  // 오류 field 앞부분 → 확인 화면 줄 · 진정서 입력칸
  var FIELD_ROW = [['workplace', 'size'], ['employment.startDate', 'start'], ['employment.endDate', 'end'], ['schedule', 'time'], ['wage.ordinaryAllowances', 'allowance'], ['wage', 'wage'], ['months[0].month', 'month'], ['months[0].paidAmount', 'paid'], ['months[0].overtime', 'ot'], ['months[0].night', 'night'], ['months[0].holiday', 'holiday'], ['months', 'more'], ['workDays', 'work']];
  var FIELD_P = { 'complainant.name': 'name', 'complainant.phone': 'phone', 'complainant.address': 'addr', 'respondent.companyName': 'company', 'respondent.ownerName': 'owner', 'respondent.address': 'waddr', 'respondent.phone': 'wphone', job: 'job', extraFacts: 'facts' };
  var L = {};

  function blankFields() {
    return { company: '', size: '', start: '', employed: '', end: '', days: [false, false, false, false, false, false, false], tStart: '', tEnd: '', brk: '', dayHours: '', wageType: '', wage: '', allowance: '', month: '', paid: '', ot: '', night: '', holiday: '', more: [], work: {}, notice: '', noticeDays: '', used: '', prob: '', year1: '', simple: '', shutDays: '', shutPaid: '' };
  }
  // 언어 목록은 i18n/languages.json (언어 파일을 더하고 여기에 한 줄 넣으면 늘어난다)
  var LANGS = [{ code: 'ko', name: '한국어' }];
  function detectLang() {
    var l = (navigator.language || 'ko').toLowerCase().slice(0, 2);
    return LANGS.some(function (x) { return x.code === l; }) ? l : LANGS.some(function (x) { return x.code === 'en'; }) ? 'en' : 'ko';
  }
  function fresh() {
    return {
      route: 'start', agreed: false, lang: 'ko', big: false, easy: false, sit: [], path: '',
      up: { status: 'idle' },
      f: blankFields(), src: {}, from: {}, conf: {}, low: {}, ok: {}, unread: {}, fieldErr: {},
      p: { name: '', phone: '', addr: '', owner: '', waddr: '', wphone: '', job: '', facts: '' }, pErr: {}, psrc: {},
      formTried: false, factsText: null, editing: false, tr: true,
      calc: null, explain: null, explainState: '', draft: null, lt: null, ltIn: { account: '', days: '7' },
      calM: '', calSel: '', ready: {}, office: { sido: '', sgg: '' }, dj: {},
      ct: { type: '', wage: '', daily: '', weekly: '', size: '', written: {}, res: null, read: false, busy: false }, dl: { end: '', fired: '', unpaid: '' }
    };
  }
  // 내용증명 초안(lt) · 계좌(ltIn)도 이름 · 연락처처럼 탭을 닫으면 지워지게(sessionStorage)
  var PRIVATE = ['p', 'draft', 'factsText', 'pErr', 'psrc', 'lt', 'ltIn'];
  function load() {
    var s = fresh();
    try {
      var raw = localStorage.getItem(KEY);
      if (raw) { s = Object.assign(s, JSON.parse(raw)); s.saved = true; }
      var p = JSON.parse(sessionStorage.getItem(PKEY) || 'null');
      if (p) PRIVATE.forEach(function (k) { if (p[k] !== undefined) s[k] = p[k]; });
    } catch (e) {}
    s.f = Object.assign(blankFields(), s.f);
    return s;
  }
  function save() {
    var keep = {}, priv = {};
    Object.keys(S).forEach(function (k) { if (k !== 'saved') (PRIVATE.indexOf(k) >= 0 ? priv : keep)[k] = S[k]; });
    try {
      localStorage.setItem(KEY, JSON.stringify(keep));
      sessionStorage.setItem(PKEY, JSON.stringify(priv));
    } catch (e) {}
  }
  // 내 정보 지우기: 이 휴대폰에 남긴 입력값 · 결과를 모두 지운다 (언어 · 큰 글씨만 남김). 묻는 창은 아래 시트
  function wipeAsk() {
    openSheet(t('wipe.btn'), '<p>' + t('wipe.confirm') + '</p>' +
      '<button type="button" class="btn btn-danger" data-act="wipe-yes">' + icon('trash') + '<span>' + t('wipe.yes') + '</span></button>' +
      '<button type="button" class="btn btn-line" data-act="sheet-close">' + t('confirm.back') + '</button>');
  }
  function wipe() {
    closeSheet();
    try { localStorage.removeItem(KEY); sessionStorage.removeItem(PKEY); } catch (e) {}
    var lang = S.lang, big = S.big;
    PAGES.forEach(function (p) { setUrl(p, null); });
    PAGES = [];
    S = fresh();
    S.lang = lang; S.big = big;
    stack = [];
    resumeTo = '';
    go('start');
    toast(t('wipe.done'));
  }

  // ---------- 아래 시트 (확인 · 풀이 · 언어 · 항목 상세). 휴대폰은 아래에서 올라오고, 넓은 화면은 가운데 창 ----------
  // 닫기: 바깥(어두운 곳) 누르기 · 손잡이 끌어 내리기 · Esc · 닫기 단추
  function openSheet(title, body, withClose) {
    sheetItem = -1;
    var d = document.getElementById('sheet');
    if (!d) {
      d = document.createElement('dialog');
      d.id = 'sheet';
      d.setAttribute('aria-labelledby', 'sheet-t');
      // 어떻게 닫히든(바깥 · 끌기 · Esc · 단추) 열려 있던 항목을 잊는다
      d.addEventListener('close', function () { sheetItem = -1; });
      document.body.appendChild(d);
    }
    // 열린 시트를 새로 그릴 때(쉬운 설명 도착)는 스크롤을 두고, 안에 있던 포커스는 제목으로
    var was = d.open, inside = was && d.contains(document.activeElement), top = d.scrollTop;
    d.innerHTML = '<div class="grab" aria-hidden="true"></div><div class="sheet-in stack"><h2 id="sheet-t" tabindex="-1">' + title + '</h2>' + body +
      (withClose ? '<button type="button" class="btn btn-primary" data-act="sheet-close"><span>' + t('words.close') + '</span></button>' : '') + '</div>';
    d.setAttribute('lang', S.lang);
    if (!was) d.showModal();
    d.scrollTop = was ? top : 0;
    if (inside) focusId('sheet-t');
  }
  function closeSheet() { var d = document.getElementById('sheet'); if (d && d.open) d.close(); }
  // 읽는 중 뼈대: 화면 읽기 프로그램에는 label 을 읽어 준다
  function skel(label, kinds) {
    return '<div class="skel-lines" role="status"><span class="sr">' + label + '</span>' + kinds.map(function (k) { return '<i class="skel' + (k ? ' ' + k : '') + '"></i>'; }).join('') + '</div>';
  }
  function langOf(code) { return LANGS.filter(function (l) { return l.code === code; })[0] || { code: code, name: code }; }
  var LANG_TITLE = '언어 / Language / Ngôn ngữ';

  var S = load();
  var sheetItem = -1;
  var openOt = false; // 다음에 그릴 질문 6 에서 연장 · 야간 · 휴일 칸을 펼쳐 둘지
  var resumeTo = '';
  var stack = [];
  var app = document.getElementById('app');
  var inFrame = window.self !== window.top;

  // ---------- 도우미 ----------
  // 괄호 뒤 조사가 다음 줄 첫머리로 떨어지지 않게: '(사용 촉진)는' → ')는' 를 한 덩어리로 (esc 다음에 쓴다)
  function nobr(html) { return html.replace(/\)([가-힣]{1,3})(?=[\s.,!?]|$)/g, '<span class="nw">)$1</span>'); }
  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  // 화면 문구: i18n/<언어>.json, 빠진 문구는 한국어
  function t(k, vars) {
    // 쉬운 한국어 보기: ko 파일의 easy.<키> 문구가 있으면 그것을 쓴다
    var s = (S.easy && S.lang === 'ko' && L.ko && L.ko['easy.' + k]) || (L[S.lang] && L[S.lang][k]) || (L.ko && L.ko[k]) || k;
    return s.replace(/\{(\w+)\}/g, function (_, v) { return (vars || {})[v]; });
  }
  var langLoad = {}; // 같은 언어 파일을 두 번 받지 않게 (한국어는 바탕 글 · 고른 언어로 두 번 불린다)
  function loadLang(lang) {
    if (L[lang]) return Promise.resolve();
    return langLoad[lang] || (langLoad[lang] = fetch('i18n/' + lang + '.json').then(function (r) { return r.json(); }).then(function (d) { L[lang] = d; }, function () { L[lang] = {}; }));
  }
  function money(n) { return t('money', { n: Number(n).toLocaleString('ko-KR') }); }
  function won(n) { return Number(n).toLocaleString('ko-KR') + '원'; }
  // 읽기 쉬운 금액: 한국어 '18만 4천 원', 다른 언어 'about 184 thousand won' (보여 주기만, 계산에 쓰지 않음)
  function korRead(n) {
    if (!n) return '0원';
    var eok = Math.floor(n / 1e8), man = Math.floor(n % 1e8 / 1e4), rest = n % 1e4, out = [];
    if (eok) out.push(eok.toLocaleString('ko-KR') + '억');
    if (man) out.push(man.toLocaleString('ko-KR') + '만');
    if (rest) out.push(rest % 1000 ? rest.toLocaleString('ko-KR') : rest / 1000 + '천');
    return out.join(' ') + (rest % 1000 ? '원' : ' 원');
  }
  function readable(n, lang) {
    if (n === '' || n == null) return '';
    n = Number(n);
    if ((lang || S.lang) === 'ko') return korRead(n);
    if (n < 1000) return '';
    var u = n >= 1e6 ? [1e6, 'read.million'] : [1e3, 'read.thousand'];
    return t('read.about', { n: (n / u[0]).toLocaleString(S.lang, { maximumFractionDigits: 2 }), u: t(u[1]) });
  }
  function term(id, label) {
    return '<button type="button" class="term" data-act="term" data-v="' + id + '">' + (label || t('w.' + id)) + '</button>';
  }
  function krDate(d) {
    if (!d) return '';
    var p = d.split('-');
    return Number(p[0]) + '년 ' + Number(p[1]) + '월 ' + Number(p[2]) + '일';
  }
  function dotDate(d) { return d ? d.replace(/-/g, '.') : ''; }
  // 연도까지: '2026년 9월' · 'Sep 2026'
  function ymName(ym) { return S.lang === 'ko' ? ym.slice(0, 4) + '년 ' + Number(ym.slice(5)) + '월' : monthName(ym); }
  // '2026-09' → '9월' · 'Sep 2026' · 'thg 9 2026' (브라우저 날짜 표기)
  function monthName(ym, lang) {
    lang = lang || S.lang;
    if (lang === 'ko') return Number(ym.slice(5)) + '월';
    return new Date(Date.UTC(+ym.slice(0, 4), +ym.slice(5) - 1, 1)).toLocaleDateString(lang, { year: 'numeric', month: 'short', timeZone: 'UTC' });
  }
  // 출근 · 퇴근 시각은 시간 단위 소수('9.5' = 09:30), 쉬는 시간도 시간 단위('0.75' = 45분)로 둔다. 퇴근이 출근보다 이르면 다음 날 퇴근
  function hours() {
    var f = S.f;
    if (f.tStart === '' || f.tEnd === '' || f.brk === '') return Number(f.dayHours) || 0;
    var span = Number(f.tEnd) - Number(f.tStart);
    if (span <= 0) span += 24;
    return Math.round((span - Number(f.brk)) * 100) / 100;
  }
  function dayText() {
    var d = DAYS.filter(function (_, i) { return S.f.days[i]; });
    return d.join(' ');
  }
  // 9.5 → '09:30' · 24 → '24:00'
  function hh(v) { var m = Math.round(Number(v) * 60); return pad(Math.floor(m / 60)) + ':' + pad(m % 60); }
  // '09:30' → '9.5' (빈 값이면 '')
  function hDec(hm) {
    if (!/^\d{1,2}:\d{2}$/.test(hm || '')) return '';
    var p = hm.split(':'), h = Number(p[0]), m = Number(p[1]);
    if (h > 24 || m > 59 || (h === 24 && m)) return '';
    return String(Math.round((h + m / 60) * 10000) / 10000);
  }
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
    dot: '<circle cx="12" cy="12" r="2.5" fill="currentColor"/>',
    lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18"/>',
    chat: '<path d="M4 5h16v11H10l-5 4v-4H4z"/><path d="M8 9.5h8M8 12.5h5"/>',
    print: '<path d="M7 9V3h10v6M7 18H5a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M7 14h10v7H7z"/>',
    copy: '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/>',
    pen: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/>',
    rotate: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
    x: '<path d="M6 6l12 12M18 6L6 18"/>',
    out: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
    sound: '<path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/>',
    stop: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
    cal: '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4M7.5 14h2M11 14h2M14.5 14h2M7.5 17.5h2M11 17.5h2"/>',
    book: '<path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5M8 7.5h8M8 11h5"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M8.5 21h7"/>',
    map: '<path d="M3.5 6.5L9 4l6 2.5L20.5 4v13.5L15 20l-6-2.5-5.5 2.5z"/><path d="M9 4v13.5M15 6.5V20"/>',
    pin: '<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    list: '<path d="M9 6.5h11M9 12h11M9 17.5h11"/><path d="M4 6.5l1 1 2-2M4 12l1 1 2-2M4 17.5l1 1 2-2"/>',
    coin: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 9l1.8 6.5L12 10.5l1.7 5L15.5 9M7.5 12h9"/>',
    trash: '<path d="M4.5 7h15M10 7V4.5h4V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    chev: '<path d="M9.5 6l6 6-6 6"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5M12 7.6v.4"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3.5 2"/>',
    moon: '<path d="M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z"/>',
    home: '<path d="M4 11l8-7 8 7v9.5H4z"/><path d="M10 20.5V15h4v5.5"/>',
  };
  function icon(name, cls) {
    return '<svg class="ico' + (cls ? ' ' + cls : '') + '" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + ICONS[name] + '</svg>';
  }
  // 월급지킴이 로고 (js/logo.js). 한국어는 글자 로고, 다른 언어는 동전 표시 + 그 언어 이름
  function brandMark() {
    var L = window.LOGO || {};
    if (S.lang === 'ko' && L.word) return '<span class="brand-mark alt">' + (L.mark || '') + '</span><span class="brand-t brand-word" aria-hidden="true">' + L.word + '</span>' + (L.mobile ? '<span class="brand-t brand-word-m" aria-hidden="true">' + L.mobile + '</span>' : '') + '<span class="sr">' + t('brand') + '</span>';
    return '<span class="brand-mark">' + (L.mark || '') + '</span><span class="brand-t">' + t('brand') + '</span>';
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
        var key = r.status === 404 ? 'err.expired' : data.code === 'DAILY_LIMIT' ? 'err.daily' : { 413: 'err.413', 415: 'err.415', 422: 'err.422', 429: 'err.429', 503: 'err.503' }[r.status] || 'err.default';
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
    S.calc = null; S.draft = null; S.lt = null;
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
  function toAgree() {
    var ag = document.getElementById('agree');
    if (ag) { ag.scrollIntoView({ block: 'center' }); ag.focus({ preventScroll: true }); }
  }
  // 못 받은 달 기본값: 그만뒀으면 마지막으로 일한 달, 다니는 중이면 지난달 (이번 달은 아직 끝나지 않아 월급날 전이다. 이번 달에 입사했으면 이번 달)
  function lastMonth() {
    var f = S.f;
    if (f.employed === 'no' && f.end) return f.end.slice(0, 7);
    var now = new Date(), prev = ymdOf(new Date(now.getFullYear(), now.getMonth() - 1, 1)).slice(0, 7);
    return f.start && f.start.slice(0, 7) > prev ? f.start.slice(0, 7) : prev;
  }
  function prevMonth(ym) { var d = new Date(+ym.slice(0, 4), +ym.slice(5) - 2, 1); return ymdOf(d).slice(0, 7); }
  // 못 받은 달 목록: 첫 달(질문 6) + 더한 달
  function monthList() {
    var out = [S.f.month || lastMonth()];
    S.f.more.forEach(function (m) { if (m.month && out.indexOf(m.month) < 0) out.push(m.month); });
    return out;
  }
  // 달력 하루 기록 → 일한 분 (퇴근이 출근보다 이르면 다음 날). 화면 표시용, 연장 · 야간 나누기는 서버 룰 엔진이 한다
  function mins(hm) { var p = hm.split(':'); return Number(p[0]) * 60 + Number(p[1]); }
  function workedMin(w) {
    var s = mins(w.s), e = mins(w.e);
    if (e <= s) e += 1440;
    return e - s - Number(w.b || 0);
  }
  function hrs(m) { return Math.round(m / 6) / 10; }
  function workDays() {
    var ms = monthList();
    return Object.keys(S.f.work).filter(function (d) { return ms.indexOf(d.slice(0, 7)) >= 0; }).sort().map(function (d) {
      var w = S.f.work[d];
      return { date: d, start: w.s, end: w.e, breakMinutes: Number(w.b || 0), holiday: !!w.h };
    });
  }
  // 평소 근무(질문 4)에 밤 10시~아침 6시가 들어 있거나 하루 8시간 · 주 40시간을 넘으면,
  // 달력 기록도 연장 · 야간 · 휴일 시간 입력도 없는 달은 평소 요일 · 시간으로 날마다 채워 보낸다.
  // 그래야 밤샘 근무자가 '야간 시간'을 따로 적지 않아도 야간 · 연장 가산이 계산된다 (나누기는 서버 룰 엔진)
  function usualNightMin() {
    var f = S.f, s = Math.round(Number(f.tStart) * 60), e = Math.round(Number(f.tEnd) * 60), n = 0;
    if (e <= s) e += 1440;
    for (var day = -1440; day <= 1440; day += 1440) n += Math.max(0, Math.min(e, day + 1800) - Math.max(s, day + 1320));
    return n;
  }
  function autoDays() {
    var f = S.f, cnt = f.days.filter(Boolean).length, h = hours();
    if (f.tStart === '' || f.tEnd === '' || f.brk === '' || !cnt || f.ot !== '' || f.night !== '' || f.holiday !== '') return [];
    if (!(usualNightMin() > 0 || h > 8 || h * cnt > 40)) return [];
    var recorded = Object.keys(f.work).map(function (d) { return d.slice(0, 7); }), now = today(), out = [];
    monthList().forEach(function (ym) {
      if (recorded.indexOf(ym) >= 0) return;
      var n = new Date(Date.UTC(+ym.slice(0, 4), +ym.slice(5), 0)).getUTCDate();
      for (var d = 1; d <= n; d++) {
        var date = ym + '-' + pad(d), wd = (new Date(date + 'T00:00:00Z').getUTCDay() + 6) % 7;
        if (f.days[wd] && inPeriod(date) && date <= now) out.push({ date: date, start: hh(f.tStart), end: hh(f.tEnd).replace('24:00', '00:00'), breakMinutes: Math.round(Number(f.brk) * 60), holiday: false });
      }
    });
    return out;
  }
  // 화면 입력값 → POST /api/v1/calculations 요청 (API.md 형식 + v2 workDays)
  function toRequest() {
    var f = S.f;
    var count = f.days.filter(Boolean).length;
    var h = hours();
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
        month: f.month || lastMonth(),
        paidAmount: Number(f.paid),
        overtimeHours: hoursOrNull(f.ot),
        nightHours: hoursOrNull(f.night),
        holidayHoursWithin8: holiday == null ? null : Math.min(holiday, 8),
        holidayHoursOver8: holiday == null ? null : Math.max(holiday - 8, 0),
        absentWeeks: 0
      }].concat(f.more.filter(function (m) { return m.month; }).map(function (m) {
        return { month: m.month, paidAmount: Number(m.paid) || 0, overtimeHours: null, nightHours: null, holidayHoursWithin8: null, holidayHoursOver8: null, absentWeeks: 0 };
      })),
      lastThreeMonthsWages: null,
      workDays: workDays().concat(autoDays()),
      // v2 6단계: 고른 상황에서 물은 것만 보낸다 (API_v2_추가.md)
      dismissal: has('fired') && f.notice ? { dismissed: true, noticeDaysBefore: f.notice === 'none' ? 0 : f.notice === 'full' ? 30 : Number(f.noticeDays) } : undefined,
      annualLeave: has('leave') && f.used !== '' ? { usedDays: Number(f.used) } : undefined,
      probation: has('minw') && f.prob === 'yes' ? { onProbation: true, contractOneYearOrMore: f.year1 === 'yes', simpleLabor: f.simple ? f.simple === 'yes' : null } : undefined,
      // 7단계: 지연이자는 오늘까지 센다(baseDate null). 재직 중이면 늘 보낸다(2025. 10. 23.부터 재직자도 이자). 휴업수당은 쉰 날 · 그동안 받은 돈
      delayInterest: (has('over14') && f.employed === 'no') || f.employed === 'yes' ? { baseDate: null } : undefined,
      shutdown: has('shut') && Number(f.shutDays) > 0 ? { days: Number(f.shutDays), paidAmount: Number(f.shutPaid) || 0 } : undefined
    };
  }
  function has(code) { return S.sit.indexOf(code) >= 0; }
  // 쉬운 말 풀이 한 줄 (회색 작은 글). 꼭 필요한 낱말에만 ko 파일 easy.<키>.d 로 둔다
  function gloss(k) { var g = t(k); return g === k ? '' : '<small class="gloss">' + g + '</small>'; }
  // 고른 상황 중 따로 물어볼 것이 있으면 7번째 질문(qx)을 넣는다
  function needX() { return has('fired') || has('leave') || has('minw') || has('shut'); }
  function qList() { return ['q1', 'q2', 'q3', 'q4', 'q5', 'q6'].concat(needX() ? ['qx'] : []); }
  // POST /calculations 응답 → 화면에서 쓰는 값
  function viewResult() {
    var c = S.calc;
    var explain = S.explain || {}, nth = {};
    var pending = S.explainState === 'loading' ? t('explain.loading') : S.explainState === 'fail' ? t('explain.fail') : S.explainState === '503' ? t('err.503') : '';
    var names = S.lang === 'ko' ? {} : L[S.lang] || {};
    return {
      total: c.summary.totalUnpaid,
      paid: c.summary.totalPaid,
      hourly: c.hourlyOrdinaryWage ? Number(c.hourlyOrdinaryWage.value).toLocaleString('ko-KR', { maximumFractionDigits: 2 }) + '원' : t('result.none'),
      hourlyFormula: c.hourlyOrdinaryWage ? c.hourlyOrdinaryWage.formula : '',
      daily: c.dailyAverageWage ? won(c.dailyAverageWage.value) : t('result.none'),
      dailyFormula: c.dailyAverageWage ? c.dailyAverageWage.formula : '',
      notApplied: (c.notApplied || []).map(function (n) { return n.reason; }),
      // 달력에 직접 적지 않았는데 평소 근무로 날마다 채운 경우(autoDays): '하루 기록이 있는 달은…' 대신 그렇게 계산했다는 화면 문구
      // 서버 경고는 한국어 문장이라, 쉬운 한국어 · 다른 언어로 볼 때는 경고 코드에 맞춘 화면 문구(warn.<코드>)를 쓴다 (없으면 서버 문장)
      warnings: (c.warnings || []).map(function (n) {
        if (n.code === 'DAILY_RECORDS' && !workDays().length) return t('result.autoDays');
        // 평소 근무로 날마다 채워 보냈으면 연장 시간은 이미 계산됐다 ('적어야 나와요'가 아니라). 월급제면 월급에 그 수당이 들어 있었는지 확인하라고
        if (n.code === 'OVER_FORTY_HOURS' && autoDays().length) return t(S.f.wageType === 'month' ? 'result.over40Month' : 'result.over40');
        var k = 'warn.' + n.code, own = (S.lang !== 'ko' || S.easy) ? t(k) : k;
        return own !== k ? own : n.message;
      }),
      weeks: c.workWeeks || [],
      items: c.items.map(function (it) {
        nth[it.code] = (nth[it.code] || 0) + 1;
        var b = (it.legalBasis || [])[0] || {};
        var easyName = S.easy && S.lang === 'ko' && L.ko['easy.item.' + it.code];
        var name = easyName || names['item.' + it.code] || it.label;
        // 서류 · 입력에 0시간이라고 적혀 있어 계산할 것이 없는 수당은 '정보 없음 · 입력하기' 대신 '해당 없음 (0시간)' (금액은 서버 그대로)
        var zero = it.status === 'NO_DATA' && !(S.f.work && Object.keys(S.f.work).length) && ({ NIGHT: S.f.night, HOLIDAY: S.f.holiday, OVERTIME: S.f.ot }[it.code] === '0');
        return {
          code: it.code,
          name: name,
          month: it.month,
          ko: it.label,
          gloss: easyName ? (L.ko['easy.item.' + it.code + '.d'] || '') : '',
          basis: it.legalBasis || [],
          label: name + (it.month ? ' (' + monthName(it.month) + ')' : ''),
          amount: it.status === 'CALCULATED' ? it.amount : null,
          status: it.status === 'CALCULATED' ? '' : zero ? t('status.ZERO_HOURS') : t('status.' + it.status),
          missing: it.status === 'NO_DATA' && !zero,
          estimated: !!it.estimated,
          explain: explain[it.code + '#' + nth[it.code]] || explain[it.code] || pending, // explain[it.code]: 예전 형식으로 저장된 해설
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
      S.lt = null;
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
      var seen = {};
      (res.items || []).forEach(function (i) { seen[i.code] = (seen[i.code] || 0) + 1; m[i.code + '#' + seen[i.code]] = i.text; });
      S.explain = m;
      S.explainState = 'done';
    }, function (e) {
      if (expired(e)) return;
      S.explainState = e.status === 503 ? '503' : 'fail';
    }).then(function () {
      if (S.route === 'result') rerender(); else save();
      if (sheetItem >= 0) itemSheet(sheetItem);
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
      closeSheet();
      S.draft = res;
      S.factsText = null;
      S.editing = false;
      go('complaint');
    }, function (e) {
      closeSheet();
      if (expired(e)) return;
      var key = e.status === 400 && FIELD_P[e.field];
      if (key) S.pErr[key] = e.message;
      render();
      toast(e.message);
    });
  }

  // 내용증명 초안: 진정서 칸에 적은 값을 그대로 보내고, 계좌 · 기한만 더한다. 금액은 계산 id 로만 정해진다
  function letterBody() {
    var f = S.f, p = S.p;
    return {
      sender: { name: p.name, phone: p.phone, address: p.addr },
      recipient: { companyName: f.company || p.company, ownerName: p.owner, address: p.waddr, phone: p.wphone },
      job: p.job, employed: f.employed === 'yes', extraFacts: p.facts,
      account: S.ltIn.account, payDays: Number(S.ltIn.days) || 7, lang: S.lang
    };
  }
  function letterReady() { var p = S.p; return !!(p.name && (S.f.company || p.company) && p.owner && p.waddr); }
  function makeLetter(btn) {
    btn.disabled = true;
    btn.textContent = t('lt.busy');
    api(calcUrl('/demand-letter'), letterBody()).then(function (res) {
      S.lt = res;
      render();
      focusId('lt-title');
    }, function (e) {
      if (expired(e)) return;
      render();
      toast(e.message);
    });
  }

  // ---------- 검사 ----------
  function valid(r) {
    var f = S.f;
    switch (r) {
      case 'q1': return !!f.size;
      case 'q2': return !!f.start && f.start <= today();
      case 'q3': return f.employed === 'yes' || (f.employed === 'no' && !!f.end && f.end >= f.start && f.end <= today());
      case 'q4': return f.days.some(Boolean) && hours() > 0;
      case 'q5': return !!f.wageType && Number(f.wage) > 0;
      case 'q6': return f.paid !== '' && f.more.every(function (m) { return m.month && m.paid !== ''; }) && monthList().length === f.more.length + 1;
      case 'qx': return (!has('fired') || (!!f.notice && (f.notice !== 'some' || Number(f.noticeDays) > 0))) && (!has('leave') || f.used !== '') &&
        (!has('minw') || f.prob === 'no' || (f.prob === 'yes' && !!f.year1 && !!f.simple)) && (!has('shut') || Number(f.shutDays) > 0);
      case 'review':
        return qList().every(valid) && Object.keys(S.low).every(function (k) { return !S.low[k] || S.ok[k]; });
      default: return true;
    }
  }
  function lowLeft() {
    return Object.keys(S.low).filter(function (k) { return S.low[k] && !S.ok[k]; }).length;
  }

  // ---------- 이동 ----------
  function go(r, opts) {
    if (ALL.indexOf(r) < 0) r = 'start';
    if (r !== 'start' && !S.agreed && FREE.indexOf(r) < 0) r = 'start';
    if (NEED_CALC.indexOf(r) >= 0 && !S.calc) r = 'review';
    if (r === 'complaint' && !S.draft) r = 'form';
    if (r === 'qx' && !needX()) r = 'review'; // 따로 물어볼 상황이 없으면 7번째 질문은 건너뛴다
    if (!opts || !opts.back) stack.push(S.route);
    stopSpeak(); speaking = '';
    closeSheet();
    // 앱처럼 옆으로 밀기: 앞으로는 오른쪽에서, 뒤로는 왼쪽에서, 탭끼리는 살짝 나타나기
    var tabs = !!S.calc && !!TAB_OF[S.route] && !!TAB_OF[r];
    var dir = tabs ? 'fade' : opts && opts.back ? 'from-l' : 'from-r';
    // 앞에서 이미 받은 값으로 그 화면의 빈 칸을 채운다 (들어갈 때 한 번. 사용자가 지운 칸을 다시 채우지 않게)
    if (r !== S.route && PREFILL[r]) PREFILL[r]();
    S.route = r;
    save();
    var url = '#' + r;
    try {
      if (inFrame) history.replaceState(null, '', url);
      else if (!opts || !opts.fromPop) history.pushState(null, '', url);
      else if (location.hash !== url) history.replaceState(null, '', url); // 뒤로 가기로 온 화면을 다른 화면으로 돌렸으면 주소도 맞춘다
    } catch (e) {}
    render(dir);
  }
  function home() {
    if (S.route === 'start') { window.scrollTo(0, 0); var h = app.querySelector('h1'); if (h) h.focus({ preventScroll: true }); return; }
    if (S.agreed && FREE.indexOf(S.route) < 0) resumeTo = S.route;
    go('start');
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
      return '<li' + (step === i ? ' aria-current="step"' : step > i ? ' class="done"' : '') + '><b>' + (step > i ? icon('check') : i) + '</b>' + stepName(i) + '</li>';
    }).join('');
    // 상표(로고)는 어느 화면에서나 시작 화면으로 가는 링크. 쓰던 값은 그대로 두고, 시작 화면에 '이어서 하기'가 뜬다
    var brand = '<a class="brand" href="#start" data-act="home">' + brandMark() + '<span class="sr">, ' + t('brand.home') + '</span></a>';
    var left = r === 'start'
      ? brand
      : '<button type="button" class="icon-btn" data-act="back" aria-label="' + t('top.back') + '">' + icon('arrow-l') + '</button>' +
        '<span class="brand-desk">' + brand + '</span>' +
        '<span class="top-title">' + esc(TITLE[r] ? t(TITLE[r]) : '') + '</span>';
    var me = langOf(S.lang);
    return '<header class="top"><div class="top-in">' + left +
      // 시작 화면은 아래 「이렇게 진행돼요」가 같은 네 걸음을 보여 주므로 머리줄 걸음 표시를 뺀다
      (hasTabs() ? tabs('tabs-top') : r === 'start' ? '' : '<ol class="steps-nav" aria-label="' + t('top.steps') + '">' + steps + '</ol>') +
      '<div class="top-tools">' +
      // 쉬운 말: 한국어일 때 위쪽 메뉴에 늘 두고, 누를 때마다 켜고 끈다
      (S.lang === 'ko' ? '<button type="button" id="easy-top" class="easy-chip" data-act="easy-tg" aria-pressed="' + !!S.easy + '">' + (S.easy ? icon('check') : '') + '<span>' + t('top.easy') + '</span></button>' : '') +
      '<button type="button" class="big-btn" data-act="big" aria-pressed="' + S.big + '" aria-label="' + t('top.big') + '">가<span aria-hidden="true">+</span></button>' +
      '<button type="button" id="lang-top" class="lang-btn" data-act="lang-sheet" aria-haspopup="dialog" aria-label="' + esc(t('top.lang') + ': ' + me.name) + '">' + icon('globe') + '<span lang="' + me.code + '">' + esc(me.name) + '</span></button>' +
      '</div></div></header>';
  }
  function hasTabs() { return !!S.calc && !!TAB_OF[S.route]; }
  // 탭 막대: 아이콘 + 짧은 글. 사전 · 대지급금은 '절차' 탭 아래 화면으로 친다
  function tabs(cls) {
    var cur = TAB_OF[S.route];
    return '<nav class="' + cls + '" aria-label="' + t('tab.nav') + '">' + TABS.map(function (x) {
      return '<button type="button" class="tab" data-go="' + x[0] + '"' + (cur === x[0] ? ' aria-current="page"' : '') + '>' + icon(x[1]) + '<span>' + t('tab.' + x[0]) + '</span></button>';
    }).join('') + '</nav>';
  }
  function langSheet() {
    openSheet(LANG_TITLE, '<div class="nav-list">' + LANGS.map(function (l) {
      return '<button type="button" class="nav-row" data-act="lang" data-v="' + l.code + '" lang="' + l.code + '" aria-pressed="' + (S.lang === l.code) + '"><span>' + esc(l.name) + '</span>' + (S.lang === l.code ? icon('check', 'chev') : '') + '</button>';
    }).join('') + '</div><p class="hint">' + t('lang.note') + '</p>');
  }
  // 목록 줄: 글 · 오른쪽 꺾쇠 (바깥 링크는 화살표). 밑줄 친 웹 링크 대신 쓴다
  function navRow(attrs, label, sub) {
    var link = /href=/.test(attrs), out = /target=/.test(attrs);
    return (link ? '<a class="nav-row" ' : '<button type="button" class="nav-row" ') + attrs + '><span>' + label + (sub ? '<small>' + sub + '</small>' : '') + '</span>' +
      icon(out ? 'out' : 'chev', 'chev') + (link ? '</a>' : '</button>');
  }
  // 결과 화면 '더 할 수 있는 일' 칸: 제목 · 한 줄 설명
  function tile(attrs, label, sub) {
    return '<button type="button" class="act" ' + attrs + '><span class="act-t"><b>' + label + '</b>' + (sub ? '<small>' + sub + '</small>' : '') + '</span>' + icon('chev', 'chev') + '</button>';
  }
  // 번역이 직접 번역(검수 전)이면 알림
  function langBanner() {
    var note = S.lang !== 'ko' && t('langNote');
    return note && note !== 'langNote' ? '<p class="banner no-print">' + esc(note) + '</p>' : '';
  }
  function progress(step) {
    var bars = [1, 2, 3, 4].map(function (i) { return '<span' + (i < step ? ' class="on"' : i === step ? ' class="now"' : '') + '></span>'; }).join('');
    return '<div class="progress only-m"><div class="bar" aria-hidden="true">' + bars + '</div><span class="hint">' + t('progress', { n: step, label: stepName(step) }) + '</span></div>';
  }
  // 1단계 이름: 서류 없이 직접 입력하면 '서류' 대신 '입력'
  function stepName(i) { return t(i === 1 && S.path === 'manual' ? 'step.1m' : 'step.' + i); }
  function cta(inner, extraClass) {
    return '<div class="cta ' + (extraClass || 'col') + '">' + inner + '</div>';
  }
  // 주 버튼: 글자만. 화살표를 덧붙이지 않는다 (공공 서비스 단추처럼 할 일을 글로만)
  function primary(label, attrs, enabled) {
    return '<button type="button" class="btn btn-primary" ' + attrs + (enabled === false ? ' disabled' : '') + '><span>' + label + '</span></button>';
  }

  // ---------- 화면: 시작 ----------
  // 문서 미리보기: 이 서비스가 실제로 인쇄해 주는 상담 요청서(앞)와 진정서 초안(뒤)의 첫 장 그림.
  // 시험 입력(이름 홍길동)으로 끝까지 가서 PDF 로 저장한 것을 그대로 그림으로 뽑았다(_도구/real_doc.py). 둘 다 한국어 원문이라 언어와 상관없이 같다
  function preview() {
    return '<figure class="docs" aria-hidden="true"><div class="docs-pile">' +
      '<img class="dp dp-back" src="/img/doc-complaint.webp" width="900" height="1273" alt="" decoding="async">' +
      '<img class="dp dp-front" src="/img/doc-counsel.webp" width="900" height="1273" alt="" decoding="async">' +
      '</div><figcaption>' + t('start.pvNote') + '</figcaption></figure>';
  }
  function vStart() {
    // 이 휴대폰에 입력하던 값이 있으면 '이어서 · 처음부터'
    var resume = resumeTo ? '<section class="resume stack" aria-labelledby="resume-t"><h2 id="resume-t">' + t('resume.title') + '</h2><p>' + t('resume.body', { where: t(TITLE[resumeTo] || 'title.q') }) + '</p>' +
      '<div class="btn-row"><button type="button" class="btn btn-primary" data-act="resume"><span>' + t('resume.go') + '</span></button>' +
      '<button type="button" class="btn btn-line" data-act="restart">' + t('resume.restart') + '</button></div><p class="hint">' + t('resume.note') + '</p></section>' : '';
    // 언어 · 쉬운 말은 머리줄에만. 처음 온 외국인이 찾게 다른 언어 이름만 작은 링크로 한 번 더
    var others = LANGS.filter(function (l) { return l.code !== S.lang; }).map(function (l) { return '<span lang="' + l.code + '">' + esc(l.name) + '</span>'; }).join(', ');
    var steps = [1, 2, 3, 4].map(function (i) { return '<li><b>' + t('start.s' + i) + '</b><span>' + t('start.s' + i + 'd') + '</span></li>'; }).join('');
    return '<div class="start">' +
      '<div class="start-band">' +
      '<section class="hero">' +
      '<h1 tabindex="-1">' + t('start.title') + '</h1>' +
      '<p class="lead">' + t('start.lead') + '</p>' + resume +
      // deslop-allow two-button-hero: 서류가 있는 사람과 없는 사람의 길이 처음부터 다르다
      cta(primary(t('start.docBtn'), 'data-act="pick" data-v="doc"') +
        '<button type="button" class="btn btn-line" data-act="pick" data-v="manual">' + t('start.manualBtn') + '</button>' +
        '<p class="trust">' + t('start.trust') + '<br><a href="tel:1350">' + t('start.call') + '</a></p>', 'start-cta') + '</section>' +
      preview() + '</div>' +
      '<section class="flow" aria-labelledby="flow-t"><h2 id="flow-t">' + t('start.flowTitle') + '</h2><ol>' + steps + '</ol></section>' +
      '<footer class="foot-bar no-print"><ul class="foot-links">' +
      [['data-go="contract"', 'title.contract'], ['data-go="deadline"', 'title.deadline'], ['data-go="after"', 'title.after'], ['data-go="words"', 'title.words'], ['data-act="wipe"', 'wipe.btn']].map(function (l) {
        return '<li><button type="button" class="btn-text" ' + l[0] + '>' + t(l[1]) + '</button></li>';
      }).join('') +
      (others ? '<li><button type="button" class="btn-text" data-act="lang-sheet">' + others + '</button></li>' : '') + '</ul>' +
      '<p class="hint">' + t('foot.saved') + '</p><p class="hint">' + t('foot.team') + '</p></footer></div>';
  }

  // ---------- 화면: 상황 고르기 (시작에서 단추를 누른 다음) ----------
  function vSit() {
    // 고른 개수는 안내 글 자리에 바꿔 쓴다. 문구의 | 는 줄을 바꿔도 되는 자리(덩어리째 넘어감)
    return '<div class="sit-page stack-lg">' + langBanner() +
      '<fieldset class="stack"><legend class="sit-title"><h1 tabindex="-1">' + t('sit.title') + '</h1></legend><p class="hint" role="status">' + (S.sit.length ? t('sit.chosen', { n: S.sit.length }) : t('sit.help')) + '</p><div class="sit-grid">' +
      SITS.map(function (x) {
        return '<label class="sit" for="sit-' + x + '"><input id="sit-' + x + '" class="sr" type="checkbox" data-act="sit" value="' + x + '"' + (has(x) ? ' checked' : '') + '><span class="sit-t">' + t('sit.' + x).split('|').map(function (c) { return '<span>' + c + '</span>'; }).join(' ') + gloss('sit.' + x + '.d') + '</span><span class="sit-mark" aria-hidden="true">' + icon('check') + '</span></label>';
      }).join('') + '</div></fieldset>' +
      // 단추는 흐리게 꺼 두지 않는다. 확인하지 않고 누르면 체크 칸으로 데려가고 알림을 한 번 띄운다
      '<div class="agree-row"><p>' + t('start.noteBody') + '</p>' +
      '<label class="check" for="agree"><input id="agree" type="checkbox" data-act="agree"' + (S.agreed ? ' checked' : '') + '><span>' + t('start.agree') + '</span></label></div>' +
      cta(primary(t(S.path === 'manual' ? 'sit.goManual' : 'sit.goDoc'), 'data-act="path" data-v="' + (S.path === 'manual' ? 'manual' : 'doc') + '"'), 'sit-cta') + '</div>';
  }

  // ---------- 화면: 서류 올리기 ----------
  // 찍기 전 안내 그림 (72x52, 선 1.75)
  var HOWTO = {
    flat: '<rect class="pf" x="27" y="3" width="18" height="13" rx="2.5"/><path class="hl" d="M36 20v8M33 25l3 3 3-3"/><path class="pf" d="M10 47h52l-8-13H18z"/><path d="M22 38h20M20 42h26"/>',
    corner: '<rect class="pf" x="22" y="9" width="28" height="34" rx="2"/><path d="M27 17h18M27 22h18M27 27h12"/><path class="hl" d="M16 13V5h8M48 5h8v8M56 39v8h-8M24 47h-8v-8"/>',
    light: '<rect class="pf" x="12" y="13" width="30" height="36" rx="2"/><path d="M17 21h20M17 26h20M17 31h14"/><circle class="hl" cx="57" cy="13" r="5"/><path class="hl" d="M57 3v2M57 21v2M47 13h2M65 13h2M50 6l1.5 1.5M62.5 18.5L64 20M64 6l-1.5 1.5"/>'
  };
  function vUpload() {
    var u = S.up;
    var busy = u.status === 'reading';
    var how = [['flat', 'up.how1'], ['corner', 'up.how2'], ['light', 'up.how3']].map(function (h) {
      return '<li><svg viewBox="0 0 72 52" aria-hidden="true" focusable="false">' + HOWTO[h[0]] + '</svg><span>' + t(h[1]) + '</span></li>';
    }).join('');
    var status = '';
    if (busy) {
      status = '<div class="filecard" role="status"><div class="scan" aria-hidden="true"><i></i><i></i><i></i></div><p>' + t('up.reading', { n: u.n }) + '</p></div>';
    } else if (u.status === 'done') {
      status = '<div class="filecard done" role="status"><div class="filecard-head">' + icon('check') + '<span>' + t('up.done', { n: u.n }) + '</span></div>' +
        (u.unread || u.masked ? '<p>' + (u.unread ? t('up.unread', { n: u.unread }) : '') + (u.masked ? ' ' + t('up.masked', { n: u.masked }) : '') + '</p>' : '') + '</div>';
    } else if (u.status === 'error') {
      status = '<div class="warnbox" role="alert"><strong>' + esc(u.msg) + '</strong></div>';
    } else if (u.status === 'fail') {
      status = '<div class="warnbox stack" role="alert"><strong>' + esc(u.msg) + '</strong><p>' + t('up.fail') + '</p><button type="button" class="btn btn-line" data-act="path" data-v="manual">' + t('up.manual') + '</button></div>';
    }
    var list = PAGES.length
      ? '<section class="stack" aria-labelledby="pages-title"><h2 id="pages-title" tabindex="-1">' + t('up.listTitle', { n: PAGES.length, max: MAX_PAGES }) + '</h2><ol class="pages">' + PAGES.map(function (p, i) { return pageCard(p, i, busy); }).join('') + '</ol></section>'
      : '';
    var waiting = PAGES.some(function (p) { return p.state === 'prep'; });
    var bad = PAGES.some(function (p) { return p.state === 'bad'; });
    var unread = PAGES.some(function (p) { return p.state === 'ready'; });
    var read = Object.keys(S.src).some(function (k) { return S.src[k] === 'doc'; });
    // 다 읽었으면 '확인하기', 아니면(못 읽음 포함) '읽기'
    var main = (u.status === 'done' && !unread && !waiting) || (!PAGES.length && read)
      ? primary(t('up.reviewBtn'), 'data-go="review"', true)
      : PAGES.length ? primary(t('up.readBtn', { n: PAGES.length }), 'data-act="read"', !waiting && !bad && !busy)
      : '<p class="hint">' + t('up.readNone') + '</p>';
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(1) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('up.h1') + '</h1><p class="lead">' + t('up.lead') + '</p></div>' +
      '<section><h2 class="sr">' + t('up.howTitle') + '</h2><ul class="howto">' + how + '</ul></section>' +
      '<div class="capture">' +
      '<div class="btn-row' + (touch.matches ? '' : ' one') + '">' +
      (touch.matches ? '<label class="btn btn-primary" for="cam">' + icon('camera') + '<span>' + t('up.cam') + '</span></label>' : '') +
      '<label class="btn ' + (touch.matches ? 'btn-line' : 'btn-primary') + '" for="file">' + icon('file') + '<span>' + t('up.pick') + '</span></label></div>' +
      (touch.matches ? '<input id="cam" class="sr" type="file" accept="image/*" capture="environment" data-act="files"' + (busy ? ' disabled' : '') + '>' : '') +
      '<input id="file" class="sr" type="file" accept="application/pdf,image/*,.hwp" multiple data-act="files"' + (busy ? ' disabled' : '') + '>' +
      '<input id="retake" class="sr" type="file" accept="application/pdf,image/*"' + (touch.matches ? ' capture="environment"' : '') + ' data-act="retake-file" tabindex="-1" aria-hidden="true">' +
      '<p class="hint only-d">' + t('up.dropHint') + '</p>' +
      '<p class="hint">' + t('up.limits', { max: MAX_PAGES }) + '<br>' + t('up.hwpHint') + '</p></div>' +
      list + status + '</div>' +
      '<aside class="side no-print">' + qrBox() +
      '<p class="note">' + icon('lock') + ' ' + t('up.privacy') + '</p>' +
      cta(main + (bad && !busy ? '<p class="hint">' + t('up.removeBad') + '</p>' : '') +
        '<button type="button" class="btn-text" data-act="path" data-v="manual">' + t('up.manual') + '</button>', ' ') +
      '</aside></div>';
  }
  // 사진 한 장 카드: 미리보기 · 서류 종류 · 검사 결과 · 돌리기/다시 찍기/빼기
  function pageCard(p, i, busy) {
    var thumb = p.url ? '<img src="' + p.url + '" alt="">' : icon('file');
    var state = { prep: t('up.preparing'), reading: t('up.readingOne'), ok: t('up.pageOk', { n: p.n }), fail: t('up.pageFail') }[p.state] || '';
    var types = ['payslip', 'contract'].map(function (d) { return '<option value="' + d + '"' + (p.type === d ? ' selected' : '') + '>' + t('up.' + d) + '</option>'; }).join('');
    var warn = p.state === 'bad' ? '<p class="err">' + esc(p.msg) + '</p>'
      : p.warn.length ? '<div class="qual"><ul>' + p.warn.map(function (w) { return '<li>' + t('up.q.' + w) + '</li>'; }).join('') + '</ul><p>' + t('up.qStill') + '</p></div>' : '';
    var dis = busy ? ' disabled' : '';
    function btn(act, ico, label) {
      return '<button type="button" class="pg-btn" id="' + act + p.id + '" data-act="pg-' + act + '" data-id="' + p.id + '"' + dis + '>' + icon(ico) + '<span>' + label + '</span></button>';
    }
    return '<li class="pg ' + p.state + '">' +
      '<div class="pg-thumb">' + thumb + '</div>' +
      '<div class="pg-body"><p class="pg-title"><b>' + t('up.page', { n: i + 1 }) + '</b>' + (state ? '<span class="pg-state">' + state + '</span>' : '') + '</p>' +
      '<p class="pg-name">' + esc(p.file.name) + '</p>' +
      (p.state === 'bad' ? '' : '<label class="sr" for="type' + p.id + '">' + t('up.typeLabel') + '</label><select id="type' + p.id + '" class="input pg-type" data-act="pg-type" data-id="' + p.id + '"' + dis + '>' + types + '</select>') +
      warn +
      '<div class="pg-acts">' + (p.url ? btn('rotate', 'rotate', t('up.rotate')) : '') + (touch.matches ? btn('retake', 'camera', t('up.retake')) : btn('retake', 'file', t('up.replace'))) + btn('remove', 'x', t('up.remove')) + '</div></div></li>';
  }
  // 데스크톱: 휴대폰으로 이 주소 열기 QR (주소만 담는다. 사진은 주고받지 않음)
  var qrSvg = '';
  function qrBox() {
    if (mobile.matches) return '';
    if (!qrSvg) {
      if (!window.qrcode) { loadQr(); return ''; }
      var q = window.qrcode(0, 'M');
      q.addData(location.origin + location.pathname);
      q.make();
      qrSvg = q.createSvgTag(4, 0);
    }
    var local = /^(localhost|127\.|\[::1\])/.test(location.hostname);
    return '<section class="qr"><div class="qr-code" role="img" aria-label="' + t('up.qrAlt') + '">' + qrSvg + '</div>' +
      '<div class="stack"><h2>' + t('up.qrTitle') + '</h2><p>' + t('up.qrText') + '</p><p class="hint">' + t(local ? 'up.qrLocal' : 'up.qrNote') + '</p></div></section>';
  }
  function loadQr() {
    if (loadQr.on) return;
    loadQr.on = true;
    var s = document.createElement('script');
    s.src = 'js/vendor/qrcode.js';
    s.onload = function () { if (S.route === 'upload') rerender(); };
    document.head.appendChild(s);
  }

  // ---------- 화면: 직접 입력 ----------
  // 질문 화면 틀. 넓은 화면에서는 옆 칸에 질문 6개 목록 + 다음 버튼
  // 질문 목록: 넓은 화면은 옆 칸에 늘 보이고, 휴대폰은 진행 표시를 누르면 아래 시트로 뜬다
  function qListHtml(n) {
    return qList().map(function (q, j) {
      var i = j + 1, cur = i === n, done = !cur && valid(q), seen = (S.qSeen || {})[q];
      // 아직 열어 보지 않았는데 서류로 채워진 질문은 체크 대신 '서류에서 채움' 으로 구분한다
      var inner = '<b>' + (done && seen ? icon('check') : i) + '</b>' + t('q.name' + i) + (done && !seen ? ' <small class="q-src">' + t('q.fromDoc') + '</small>' : '');
      return '<li>' + (cur ? '<span aria-current="step">' + inner + '</span>' : '<button type="button" data-go="' + q + '"' + (done ? ' class="done"' : '') + '>' + inner + '</button>') + '</li>';
    }).join('');
  }
  function qSheet(n) { openSheet(t('q.listNav'), '<ol class="qlist">' + qListHtml(n) + '</ol>'); }
  function qShell(n, legend, help, body) {
    var qs = qList();
    S.qSeen = S.qSeen || {}; S.qSeen[qs[n - 1]] = 1;
    var bars = qs.map(function (_, j) { var i = j + 1; return '<span' + (i < n ? ' class="on"' : i === n ? ' class="now"' : '') + '></span>'; }).join('');
    // 진행 표시에 지금 질문의 이름도 붙인다: 「질문 5 / 6 · 약속한 급여」
    var list = qListHtml(n), where = t('q.progress', { n: n, total: qs.length }) + ', ' + t('q.name' + n);
    return '<div class="split">' +
      '<div class="stack-lg">' +
      '<div class="progress"><div class="bar" style="--n:' + qs.length + '" aria-hidden="true">' + bars + '</div><span class="hint only-d">' + where + '</span>' +
      '<button type="button" class="q-jump only-m" data-act="q-sheet" data-n="' + n + '" aria-haspopup="dialog" aria-label="' + where + ', ' + t('q.listNav') + '">' + icon('list') + '<span>' + where + '</span></button></div>' + langBanner() +
      '<fieldset class="stack"><legend class="q-legend"><h1 tabindex="-1">' + legend + '</h1></legend>' +
      (help ? '<p class="lead">' + help + '</p>' : '') + body + '</fieldset></div>' +
      '<aside class="side"><nav class="qnav only-d" aria-label="' + t('q.listNav') + '"><p class="qlist-title">' + t('q.listTitle', { n: qs.length }) + '</p><ol class="qlist">' + list + '</ol></nav>' + nextCta(qs[n - 1]) + '</aside></div>';
  }
  function nextCta(r) {
    var next = { q1: 'q2', q2: 'q3', q3: 'q4', q4: 'q5', q5: 'q6', q6: needX() ? 'qx' : 'review', qx: 'review' }[r];
    // 이전: 앞 질문으로. 첫 질문이면 서류 화면(서류로 시작한 경우)이나 시작 화면으로
    var qs = qList(), prev = qs[qs.indexOf(r) - 1] || (S.path === 'doc' ? 'upload' : 'sit');
    return cta('<div class="btn-row q-nav"><button type="button" class="btn btn-line" data-go="' + prev + '">' + icon('arrow-l') + '<span>' + t('q.prev') + '</span></button>' +
      primary(t(next === 'review' ? 'q.toReview' : 'q.next'), 'data-go="' + next + '" data-next', valid(r)) + '</div>' +
      '<p class="hint" data-next-hint' + (valid(r) ? ' hidden' : '') + '>' + t('q.nextHint') + '</p>', ' ');
  }
  function radios(name, key, list) {
    return '<div class="choices">' + list.map(function (o) {
      return '<label class="choice"><input type="radio" name="' + name + '" value="' + o[0] + '" data-f="' + key + '"' + (S.f[key] === o[0] ? ' checked' : '') + '><span>' + o[1] + (o[2] ? '<small>' + o[2] + '</small>' : '') + '</span></label>';
    }).join('') + '</div>';
  }
  function vQ1() {
    return qShell(1, t('q1.title'), t('q1.help'),
      radios('size', 'size', [['five', t('q1.five')], ['under', t('q1.under')], ['unsure', t('q1.unsure')]]) +
      (S.f.size === 'unsure' ? '<p class="note" role="status">' + t('q1.unsureNote') + '</p>' : '') +
      '<details class="more"><summary>' + t('q1.why') + '</summary><p class="lead">' + t('q1.whyBody') + ' ' + term('underFive') + '</p></details>');
  }
  function vQ2() {
    return qShell(2, t('q2.title'), t('q2.help'),
      '<div class="field"><label for="start">' + t('q2.label') + '</label><input id="start" class="input" type="date" data-f="start" value="' + esc(S.f.start) + '" max="' + today() + '"></div>');
  }
  function vQ3() {
    var f = S.f;
    var early = f.employed === 'no' && f.end && f.start && f.end < f.start;
    var future = f.employed === 'no' && f.end > today();
    var bad = early || future;
    return qShell(3, t('q3.title'), t('q3.help'),
      radios('employed', 'employed', [['yes', t('q3.yes')], ['no', t('q3.no')]]) +
      (f.employed === 'no' ? '<div class="field"><label for="end">' + t('q3.end') + '</label><input id="end" class="input" type="date" data-f="end" value="' + esc(f.end) + '" max="' + today() + '"' + (bad ? ' aria-invalid="true" aria-describedby="end-err"' : '') + '>' +
        (bad ? '<p id="end-err" class="err">' + t(early ? 'q3.endErr' : 'q3.endFuture') + '</p>' : '') + '</div>' : ''));
  }
  // 쉬는 시간(시간 단위 소수) → '1시간 30분' · '45분'
  function brkName(v) {
    var m = Math.round(Number(v) * 60);
    if (!m) return t('brk.0');
    var h = Math.floor(m / 60), r = m % 60;
    return [h ? t('cal.hoursShort', { h: h }) : '', r ? t('cal.min', { n: r }) : ''].filter(Boolean).join(' ');
  }
  // 하루 · 일주일 시간 안내 (입력할 때마다 바로 바뀐다)
  function q4Sum() {
    var f = S.f, h = hours(), cnt = f.days.filter(Boolean).length;
    if (f.tStart !== '' && f.tEnd !== '' && f.brk !== '' && h <= 0) return '<p class="err" role="alert">' + t('q4.timeErr') + '</p>';
    if (h > 0 && cnt) {
      var nm = f.tStart !== '' && f.tEnd !== '' ? Math.min(usualNightMin(), Math.round(h * 60)) : 0;
      return '<p class="sumline" role="status">' + t('q4.sum', { h: h, w: Math.round(h * cnt * 100) / 100 }) +
        (nm > 0 ? '<br>' + t('q4.night', { h: Math.round(nm / 6) / 10 }) : '') + '</p>';
    }
    return '';
  }
  function vQ4() {
    var f = S.f;
    var days = DAYS.map(function (_, i) {
      return '<button type="button" class="day" data-act="day" data-i="' + i + '" aria-pressed="' + f.days[i] + '">' + t('day.' + i) + '</button>';
    }).join('');
    // 출근 · 퇴근: 시 · 분을 직접 넣는다 (30분 · 45분도). 쉬는 시간은 분으로
    function time(id, key, label) {
      // 브라우저 시간 칸은 '오전/오후'부터 골라야 해서 숫자를 바로 칠 수 없다 → 숫자만 치면 '0830' → '08:30' 으로 맞춰 주는 글자 칸 (24시간제)
      return '<div class="field"><label for="' + id + '">' + label + '</label><input id="' + id + '" class="input input-time" type="text" inputmode="numeric" autocomplete="off" maxlength="5" placeholder="' + (key === 'tStart' ? '09:00' : '18:00') + '" data-f="' + key + '" data-live data-conv="time" value="' + (f[key] === '' ? '' : hh(f[key])) + '"></div>';
    }
    var brkMin = f.brk === '' ? '' : String(Math.round(Number(f.brk) * 60));
    return qShell(4, t('q4.title'), '',
      '<div class="stack"><span class="label" id="days-label">' + t('q4.days') + ' <span class="opt">' + t('q4.multi') + '</span></span><div class="days" role="group" aria-labelledby="days-label">' + days + '</div></div>' +
      '<div class="grid2">' + time('tstart', 'tStart', t('q4.start')) + time('tend', 'tEnd', t('q4.end')) + '</div>' +
      '<p class="hint">' + t('q4.timeHint') + '</p>' +
      '<div class="field"><label for="brk">' + t('q4.break') + ' <span class="opt">(' + t('cal.minUnit') + ')</span></label>' +
      '<input id="brk" class="input input-short" type="text" inputmode="numeric" autocomplete="off" maxlength="3" data-f="brk" data-live data-conv="min" value="' + brkMin + '" placeholder="' + esc(t('q4.breakPh')) + '"></div>' +
      '<div data-q4sum>' + q4Sum() + '</div>' +
      '<details class="more"><summary>' + t('q4.diff') + '</summary><div class="stack" style="padding-bottom:12px"><p class="lead">' + t('q4.diffBody') + '</p>' +
      '<button type="button" class="btn btn-line" data-go="cal">' + icon('cal') + '<span>' + t('cal.open') + '</span></button></div></details>');
  }
  function vQ5() {
    var f = S.f;
    var w = Number(f.wage);
    var warn = f.wageType === 'hour' && w > 0 && w < MIN_WAGE
      ? '<p class="warnbox" role="status">' + t('q5.minWarn') + ' ' + term('minWage') + '</p>' : '';
    return qShell(5, t('q5.title'), t('q5.help'),
      radios('wageType', 'wageType', [['hour', t('q5.hour')], ['day', t('q5.day')], ['month', t('q5.month')]]) +
      '<div class="field"><label for="wage">' + t('q5.amount') + '</label><input id="wage" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="wage" data-live value="' + esc(f.wage) + '"><p class="readable" data-readable="wage" aria-live="polite">' + readable(f.wage) + '</p></div>' + warn);
  }
  function vQ6() {
    var f = S.f;
    function num(id, key, label, unit) {
      return '<div class="field"><label for="' + id + '">' + label + ' <span class="opt">(' + unit + ')</span></label><input id="' + id + '" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="' + key + '" data-live value="' + esc(f[key]) + '"></div>';
    }
    var recorded = workDays().length;
    var dup = monthList().length !== f.more.filter(function (m) { return m.month; }).length + 1;
    // 못 받은 달 더하기: 달 + 그 달에 받은 돈
    var more = f.more.map(function (m, i) {
      return '<li class="month-row"><div class="field"><label for="mm' + i + '">' + t('q6.extraMonth') + '</label><input id="mm' + i + '" class="input" type="month" data-m="' + i + '" data-k="month" value="' + esc(m.month) + '"></div>' +
        '<div class="field"><label for="mp' + i + '">' + t('q6.extraPaid') + '</label><input id="mp' + i + '" class="input" type="text" inputmode="numeric" autocomplete="off" data-m="' + i + '" data-k="paid" value="' + esc(m.paid) + '"><p class="readable" data-readable-m="' + i + '" aria-live="polite">' + readable(m.paid) + '</p></div>' +
        '<button type="button" class="pg-btn" data-act="month-del" data-i="' + i + '">' + icon('x') + '<span>' + t('q6.removeMonth') + '</span></button></li>';
    }).join('');
    var extraN = [f.ot, f.night, f.holiday, f.allowance].filter(function (v) { return v !== ''; }).length;
    // 고르는 줄: 왼쪽 아이콘 · 제목 · 한 줄 설명 · 오른쪽 상태(몇 개 적음). 달력은 다른 화면, 나머지 둘은 그 자리에서 펼친다
    function optHead(ico, title, sub, badge) {
      return icon(ico) + '<span class="opt-t"><b>' + title + '</b><small>' + sub + '</small></span>' + (badge ? '<span class="tag self">' + badge + '</span>' : '') + icon('chev', 'chev');
    }
    // 제목에 고른 달을 넣는다 (달을 바꾸면 제목도 바뀜)
    return qShell(6, t('q6.titleM', { m: ymName(f.month || lastMonth()) }), t('q6.help'),
      // 1) 꼭 적을 것: 못 받은 달 · 그 달에 받은 돈
      '<div class="paid-box stack"><div class="grid2 paid-grid">' +
      '<div class="field"><label for="month">' + t('q6.month') + '</label><input id="month" class="input" type="month" data-f="month" value="' + esc(f.month || lastMonth()) + '" max="' + today().slice(0, 7) + '"><p class="hint">' + t('q6.monthHint') + '</p></div>' +
      '<div class="field"><label for="paid">' + t('q6.paid') + '</label><input id="paid" class="input input-money" type="text" inputmode="numeric" autocomplete="off" data-f="paid" data-live value="' + esc(f.paid) + '" placeholder="0"><p class="readable" data-readable="paid" aria-live="polite">' + readable(f.paid) + '</p></div>' +
      '</div>' + partialNote() + '</div>' +
      // 2) 해당되면 누르는 목록 (세 줄 모두 같은 모양. 위 꼭 적을 칸과는 구분선 하나로 나눈다)
      '<section class="stack opt-sec" aria-labelledby="opt-t"><p id="opt-t" class="opt-line">' + t('q6.moreLine') + '</p>' +
      '<div class="opt-list">' +
      '<button type="button" class="opt-row" data-go="cal">' + optHead('cal', t('q6.calShort'), recorded ? t('q6.calHas', { n: recorded }) : t('q6.calSub'), recorded ? t('review.workN', { n: recorded }) : '') + '</button>' +
      '<details class="opt-row-d"' + (has('extra') || extraN || openOt ? ' open' : '') + '><summary class="opt-row">' + optHead('moon', t('q6.otTitle'), t('q6.otSub'), extraN ? t('q6.filled', { n: extraN }) : '') + '</summary>' +
      '<div class="opt-body stack"><p class="hint">' + t('q6.noData') + (recorded ? ' ' + t('q6.calWins') : '') + '</p>' +
      '<div class="grid2">' + num('ot', 'ot', t('q6.ot'), t('unit.hours')) + num('night', 'night', t('q6.night'), t('unit.hours')) + num('holiday', 'holiday', t('q6.holiday'), t('unit.hours')) + num('allow', 'allowance', t('q6.allow'), t('unit.won')) + '</div></div></details>' +
      '<details class="opt-row-d"' + (f.more.length ? ' open' : '') + '><summary class="opt-row">' + optHead('plus', t('q6.extra'), t('q6.extraSub'), f.more.length ? t('q6.filled', { n: f.more.length }) : '') + '</summary>' +
      '<div class="opt-body stack"><p class="hint">' + t('q6.extraHelp') + '</p>' + (more ? '<ol class="months">' + more + '</ol>' : '') +
      (dup && f.more.every(function (m) { return m.month; }) ? '<p class="err" role="alert">' + t('q6.dup') + '</p>' : '') +
      '<button type="button" class="btn btn-line" data-act="month-add">' + icon('plus') + '<span>' + t('q6.addMonth') + '</span></button></div></details>' +
      '</div></section>');
  }

  // 못 받은 달이 그만둔 달이고 달 중간에 그만뒀으면: 그 달은 일한 날만큼만 계산한다는 것과, 월급날이 아니라 그 달 일한 것에 대해 받은 돈을 묻는다는 것
  function partialNote() {
    var f = S.f, ym = f.month || lastMonth();
    var note = '<p class="hint">' + t('q6.which') + '</p>';
    if (f.employed !== 'no' || !f.end || f.end.slice(0, 7) !== ym) return note;
    var last = new Date(Date.UTC(+ym.slice(0, 4), +ym.slice(5), 0)).getUTCDate(), d = Number(f.end.slice(8));
    if (d >= last) return note;
    // 그만둔 달에 며칠만 일했으면 앞달도 못 받았을 수 있다: 앞달을 '못 받은 달'에 바로 더하는 단추 (이미 더했으면 안 보임)
    var prev = prevMonth(ym), added = f.more.some(function (m) { return m.month === prev; });
    return note + '<div class="note stack" style="gap:var(--s2)"><p>' + icon('info') + ' ' + t('q6.partial', { m: ymName(ym), d: d, last: last }) + '</p>' +
      (d <= 10 && !added && (!f.start || f.start.slice(0, 7) <= prev) ? '<p>' + t('q6.prevAsk', { m: ymName(prev) }) + '</p><button type="button" class="btn btn-line" style="align-self:flex-start" data-act="month-add-prev" data-v="' + prev + '">' + icon('plus') + '<span>' + t('q6.prevAdd', { m: ymName(prev) }) + '</span></button>' : '') + '</div>';
  }
  // 7번째 질문: 고른 상황(해고 · 연차 · 최저임금 · 휴업)에 맞춰 필요한 것만
  function vQx() {
    var f = S.f, body = '';
    function num(id, key, label) {
      return '<div class="field"><label for="' + id + '">' + label + '</label><input id="' + id + '" class="input" type="text" inputmode="numeric" autocomplete="off" data-f="' + key + '" data-live value="' + esc(f[key]) + '"></div>';
    }
    if (has('fired')) {
      body += '<section class="stack"><h2>' + t('qx.fired') + '</h2>' + radios('notice', 'notice', [['none', t('qx.none')], ['some', t('qx.some')], ['full', t('qx.full')]]) +
        (f.notice === 'some' ? num('nd', 'noticeDays', t('qx.days')) : '') + '<p class="hint">' + t('qx.firedHint') + '</p></section>';
    }
    if (has('leave')) {
      body += '<section class="stack"><h2>' + t('qx.leave') + '</h2>' + num('used', 'used', t('qx.used')) + '<p class="hint">' + t(f.employed === 'yes' ? 'qx.leaveStay' : 'qx.leaveHint') + '</p></section>';
    }
    if (has('minw')) {
      body += '<section class="stack"><h2>' + t('qx.prob') + '</h2>' + radios('prob', 'prob', [['no', t('qx.no')], ['yes', t('qx.yes')]]) +
        (f.prob === 'yes' ? '<p class="label">' + t('qx.year1') + '</p>' + radios('year1', 'year1', [['yes', t('qx.yes')], ['no', t('qx.no')]]) +
          '<p class="label">' + t('qx.simple') + '</p>' + radios('simple', 'simple', [['no', t('qx.no')], ['yes', t('qx.yes')]]) : '') +
        '<p class="hint">' + t('qx.probHint') + '</p></section>';
    }
    if (has('shut')) {
      body += '<section class="stack"><h2>' + t('qx.shut') + '</h2>' + num('sd', 'shutDays', t('qx.shutDays')) + num('sp', 'shutPaid', t('qx.shutPaid')) + '<p class="hint">' + t('qx.shutHint') + '</p></section>';
    }
    return qShell(7, t('qx.title'), t('qx.help'), body);
  }

  // ---------- 화면: 알아 두면 좋은 것 (data/guides.json, 공식 출처만. 금액 판정 없음) ----------
  var GUIDES = null, GUIDE_DAY = '';
  var GUIDE_SIT = { payslip: 'payslip', deduction: 'deduct', unfair: 'fired', jobless: 'over14' }; // 안내 id → 시작 화면 상황
  function loadGuides() {
    if (GUIDES) return;
    GUIDES = [];
    fetch('data/guides.json').then(function (r) { return r.ok ? r.json() : []; }).then(function (d) {
      GUIDES = (d.guides || []).filter(function (g) { return g && g.id; });
      GUIDE_DAY = d.checkedAt || '';
      if (GUIDES.length && (S.route === 'info' || S.route === 'after' || TAB_OF[S.route])) render();
    }, function () {});
  }
  // 글은 문자열 또는 { ko, en, vi }. 그 언어가 없으면 한국어
  function pick(v) { return v && typeof v === 'object' && !Array.isArray(v) ? v[S.lang] || v.ko || '' : v || ''; }
  function infoRow() {
    loadGuides();
    return GUIDES.length ? navRow('data-go="info"', t('title.info'), t('info.sub')) : '';
  }
  // after: '그만둔 뒤 받을 수 있는 것'(group after)만
  function vInfo(after) {
    loadGuides();
    function mine(g) { return has(GUIDE_SIT[g.id]) ? 1 : 0; }
    var list = GUIDES.filter(function (g) { return !after || g.group === 'after'; }).sort(function (a, b) { return mine(b) - mine(a); }).map(function (g) {
      var src = (g.sources || []).map(function (x) {
        return navRow('href="' + esc(x.url) + '" target="_blank" rel="noopener" lang="ko"', esc(x.name), esc(t('info.checked', { d: GUIDE_DAY })));
      }).join('');
      var body = pick(g.lines) || [];
      return '<li class="info-card stack"><h2>' + esc(pick(g.title)) + (mine(g) ? ' <span class="tag">' + t('info.mine') + '</span>' : '') + '</h2>' +
        '<ul class="bring">' + body.map(function (b) { return '<li>' + icon('dot') + '<span>' + nobr(esc(b)) + '</span></li>'; }).join('') + '</ul>' +
        (src ? '<div class="nav-list">' + src + '</div>' : '') + '</li>';
    }).join('');
    return '<div class="wide col stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t(after ? 'title.after' : 'title.info') + '</h1><p class="lead">' + t(after ? 'after.lead' : 'info.lead') + '</p></div>' +
      (list ? '<ol class="plain info-list">' + list + '</ol>' : '<p class="hint">' + t('info.none') + '</p>') +
      '<p class="hint">' + t('info.note') + '</p>' + (hasTabs() ? '' : cta(primary(t('counsel.back'), 'data-act="back"'))) + '</div>';
  }

  // ---------- 화면: 근무 달력 ----------
  // 날짜를 누르면 아래에 출근 · 퇴근 · 쉬는 시간 · 휴일 칸. 연장 · 야간 · 휴일 시간 나누기는 계산할 때 룰 엔진이 한다
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function inPeriod(d) {
    var f = S.f;
    return (!f.start || d >= f.start) && (f.employed !== 'no' || !f.end || d <= f.end);
  }
  function vCal() {
    var f = S.f, ms = monthList();
    if (ms.indexOf(S.calM) < 0) S.calM = ms[0];
    var ym = S.calM, y = +ym.slice(0, 4), m = +ym.slice(5);
    var n = new Date(Date.UTC(y, m, 0)).getUTCDate(), lead = (new Date(Date.UTC(y, m - 1, 1)).getUTCDay() + 6) % 7;
    var cells = '';
    for (var i = 0; i < lead; i++) cells += '<span class="cal-blank" aria-hidden="true"></span>';
    var total = 0, count = 0;
    for (var d = 1; d <= n; d++) {
      var date = ym + '-' + pad(d), w = f.work[date], wd = (lead + d - 1) % 7, ok = inPeriod(date);
      if (w) { total += workedMin(w); count++; }
      var label = krOrLocal(date) + (w ? ', ' + w.s + '~' + w.e + ', ' + t('cal.hoursShort', { h: hrs(workedMin(w)) }) + (w.h ? ', ' + t('cal.holiday') : '') : '');
      cells += '<button type="button" id="cd' + date + '" class="cal-day' + (wd > 4 ? ' we' : '') + (w ? ' has' : '') + (w && w.h ? ' hol' : '') + '" data-act="cal-day" data-d="' + date + '" aria-pressed="' + (S.calSel === date) + '" aria-label="' + esc(label) + '"' + (ok ? '' : ' disabled') + '>' +
        '<b>' + d + '</b>' + (w ? '<small>' + (w.h ? t('cal.holShort') : '') + hrs(workedMin(w)) + 'h</small>' : '') + '</button>';
    }
    var heads = DAYS.map(function (_, i) { return '<span aria-hidden="true">' + t('day.' + i) + '</span>'; }).join('');
    var nav = ms.length > 1 ? '<div class="field"><label for="cal-month">' + t('cal.month') + '</label><select id="cal-month" class="input" data-act="cal-month">' +
      ms.map(function (x) { return '<option value="' + x + '"' + (x === ym ? ' selected' : '') + '>' + ymName(x) + '</option>'; }).join('') + '</select></div>' : '';
    var usual = f.days.some(Boolean) && f.tStart !== '' && f.tEnd !== '';
    var editor = '';
    if (S.calSel && S.calSel.slice(0, 7) === ym) {
      var cur = f.work[S.calSel] || { s: usual ? hh(f.tStart).replace('24:00', '00:00') : '09:00', e: usual ? hh(f.tEnd).replace('24:00', '00:00') : '18:00', b: f.brk !== '' ? Number(f.brk) * 60 : 60, h: false };
      var bopts = [0, 30, 60, 90, 120].map(function (b) { return '<option value="' + b + '"' + (Number(cur.b) === b ? ' selected' : '') + '>' + t('cal.min', { n: b }) + '</option>'; }).join('');
      editor = '<section class="cal-edit stack" aria-labelledby="ce-title"><h2 id="ce-title" tabindex="-1">' + krOrLocal(S.calSel) + '</h2>' +
        '<div class="grid2"><div class="field"><label for="ce-s">' + t('q4.start') + '</label><input id="ce-s" class="input" type="time" step="600" value="' + cur.s + '"></div>' +
        '<div class="field"><label for="ce-e">' + t('q4.end') + '</label><input id="ce-e" class="input" type="time" step="600" value="' + cur.e + '"></div></div>' +
        '<p class="hint">' + t('cal.nextDay') + '</p>' +
        '<div class="field"><label for="ce-b">' + t('q4.break') + '</label><select id="ce-b" class="input">' + bopts + '</select></div>' +
        '<label class="check" for="ce-h"><input id="ce-h" type="checkbox"' + (cur.h ? ' checked' : '') + '><span>' + t('cal.holidayQ') + ' ' + '</span></label>' +
        '<p class="hint">' + t('cal.holidayHint') + ' ' + term('holidayWork') + '</p>' +
        '<p class="err" id="ce-err" role="alert" hidden>' + t('cal.err') + '</p>' +
        '<div class="btn-row"><button type="button" class="btn btn-primary" data-act="cal-save"><span>' + t('cal.save') + '</span></button>' +
        (f.work[S.calSel] ? '<button type="button" class="btn btn-line" data-act="cal-del">' + icon('trash') + '<span>' + t('cal.del') + '</span></button>' : '<button type="button" class="btn btn-line" data-act="cal-close">' + t('cal.close') + '</button>') + '</div></section>';
    }
    var usualText = usual ? t('cal.fillBody', { days: f.days.map(function (on, i) { return on ? t('day.' + i) : ''; }).filter(Boolean).join(', '), s: hh(f.tStart), e: hh(f.tEnd) }) : '';
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(1) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('cal.title') + '</h1><p class="lead">' + t('cal.lead') + ' ' + term('overtime') + ' ' + term('night') + '</p></div>' +
      nav +
      '<div class="cal" role="group" aria-label="' + esc(ymName(ym)) + '"><p class="cal-head">' + ymName(ym) + '</p><div class="cal-grid"><div class="cal-week" aria-hidden="true">' + heads + '</div><div class="cal-days">' + cells + '</div></div></div>' +
      editor + '</div>' +
      '<aside class="side"><div class="note stack" role="status"><h2>' + t('cal.sumTitle') + '</h2><p>' + t('cal.sum', { n: count, h: hrs(total) }) + '</p><p class="hint">' + t('cal.sumHint') + '</p></div>' +
      (usual ? '<section class="stack"><button type="button" class="btn btn-line" data-act="cal-fill">' + icon('cal') + '<span>' + t('cal.fill') + '</span></button><p class="hint">' + usualText + '</p></section>' : '') +
      (count ? '<button type="button" class="btn-text" data-act="cal-clear">' + t('cal.clear') + '</button>' : '') +
      cta(primary(t('cal.done'), 'data-act="back"'), ' ') + '</aside></div>';
  }
  // 날짜를 그 언어로: 2026년 9월 7일 (월) · Mon, Sep 7, 2026
  function krOrLocal(d) {
    var dt = new Date(d + 'T00:00:00Z');
    if (S.lang === 'ko') return krDate(d) + ' (' + DAYS[(dt.getUTCDay() + 6) % 7] + ')';
    return dt.toLocaleDateString(S.lang, { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric', timeZone: 'UTC' });
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
      var val = value ? '<span>' + esc(value) + '</span>' : '<span class="empty">' + t(S.unread[key] ? 'review.unread' : 'review.empty') + '</span>';
      var tag = src === 'doc' ? '<span class="tag">' + t('review.doc') + (S.from[key] ? ', ' + t('up.page', { n: S.from[key] }) : '') + '</span>' : src === 'self' ? '<span class="tag self">' + t('review.self') + '</span>' : '';
      return '<div class="row' + (low ? ' low' : '') + '"><div class="row-main"><dt>' + label + '</dt><dd>' + val + tag + (checked ? '<span class="checked">' + icon('check') + t('review.checked') + '</span>' : '') + '</dd>' +
        '<button type="button" class="row-edit" data-go="' + goTo + '" aria-label="' + esc(t('review.editOf', { what: label })) + '">' + t('review.edit') + '</button></div>' +
        (err ? '<p class="err" role="alert">' + esc(err) + '</p>' : '') +
        (low ? '<p class="row-note">' + (S.conf[key] ? t('review.conflict', { v: esc(S.conf[key]) }) : t('review.blurry')) + '</p><button type="button" class="ok-btn" data-act="ok" data-k="' + key + '">' + icon('check') + t('review.yes') + '</button>' : '') + '</div>';
    }
    var timeText = f.tStart !== '' && f.tEnd !== '' ? hh(f.tStart) + ' ~ ' + hh(f.tEnd) + (f.brk !== '' ? ', ' + t('q4.break') + ' ' + brkName(f.brk) : '') : f.dayHours ? t('cal.hoursShort', { h: f.dayHours }) : '';
    var dayText2 = f.days.map(function (on, i) { return on ? t('day.' + i) : ''; }).filter(Boolean).join(' ');
    var rec = workDays().length;
    var moreRows = f.more.map(function (m) { return row(m.month ? ymName(m.month) : t('q6.extraMonth'), m.paid !== '' ? won(m.paid) : '', 'more', 'q6'); }).join('');
    var groups = [
      [t('review.g1'), (f.company ? row(t('review.company'), f.company, 'company', 'q1') : '') + row(t('q.name1'), { five: t('q1.five'), under: t('q1.under'), unsure: t('review.unsure') }[f.size], 'size', 'q1')],
      [t('review.g2'), row(t('q2.label'), f.start ? krOrLocal(f.start) : '', 'start', 'q2') + row(t('review.end'), f.employed === 'yes' ? t('q3.yes') : f.end ? krOrLocal(f.end) : '', 'end', 'q3')],
      [t('review.g3'), row(t('q4.days'), dayText2, 'days', 'q4') + row(t('review.day'), timeText, 'time', 'q4') + row(t('review.work'), rec ? t('review.workN', { n: rec }) : '', 'work', 'cal')],
      [t('review.g4'), row({ hour: t('q5.hour'), day: t('q5.day'), month: t('q5.month') }[f.wageType] || t('q.name5'), f.wage ? won(f.wage) : '', 'wage', 'q5') + row(t('review.allow'), f.allowance ? won(f.allowance) : '', 'allowance', 'q6')],
      [t('review.g5'), (f.month || S.unread.month ? row(t('q6.extraMonth'), f.month ? ymName(f.month) : '', 'month', 'q6') : '') + row(t('q.name6'), f.paid !== '' ? won(f.paid) : '', 'paid', 'q6') + moreRows +
        row(t('review.ot'), f.ot ? t('cal.hoursShort', { h: f.ot }) : '', 'ot', 'q6') + row(t('review.night'), f.night ? t('cal.hoursShort', { h: f.night }) : '', 'night', 'q6') + row(t('review.holiday'), f.holiday ? t('cal.hoursShort', { h: f.holiday }) : '', 'holiday', 'q6')]
    ].map(function (g) { return '<section class="group"><h2>' + g[0] + '</h2><dl class="rows">' + g[1] + '</dl></section>'; }).join('');
    var left = lowLeft();
    var ok = valid('review');
    // 답하지 않은 질문이 있으면 무엇이 빠졌는지 · 어디로 가면 되는지를 맨 위에 (계산 단추만 꺼져 있으면 찾을 수 없다)
    var qs = qList(), missQ = qs.filter(function (q) { return !valid(q); });
    var missBox = missQ.length ? '<section class="warnbox stack" aria-labelledby="miss-t"><h2 id="miss-t">' + t('review.missTitle') + '</h2><div class="nav-list">' +
      missQ.map(function (q) { return navRow('data-go="' + q + '"', t('q.name' + (qs.indexOf(q) + 1))); }).join('') + '</div></section>' : '';
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(2) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t(Object.keys(S.src).some(function (k) { return S.src[k] === 'doc'; }) ? 'review.title' : 'review.titleSelf') + '</h1><p class="lead">' + t('review.lead') + '</p></div>' +
      missBox + groups + '</div>' +
      '<aside class="side"><div class="note stack"><h2>' + t('review.legend') + '</h2><div class="legend-row" style="flex-direction:column"><span><span class="tag">' + t('review.doc') + '</span> ' + t('review.legDoc') + '</span><span><span class="tag self">' + t('review.self') + '</span> ' + t('review.legSelf') + '</span><span><span class="tag warn">' + t('review.legWarnTag') + '</span> ' + t('review.legWarn') + '</span></div></div>' +
      '<p class="hint">' + t('review.rule') + '</p>' +
      cta(primary(t('calc.btn'), 'data-act="calculate"', ok) +
        (ok ? '' : '<p class="hint">' + (left ? t('review.left', { n: left }) : t('review.missing')) + '</p>'), ' ') +
      '</aside></div>';
  }

  // ---------- 화면: 결과 ----------
  // 달별 못 받은 돈 막대 (직접 SVG). 막대 높이는 서버가 준 항목 금액을 달마다 더한 것뿐, 새 금액을 만들지 않는다
  function chart(R) {
    var by = {};
    R.items.forEach(function (it) { if (it.month && it.amount) by[it.month] = (by[it.month] || 0) + it.amount; });
    var ms = Object.keys(by).sort();
    if (!ms.length) return '';
    var max = Math.max.apply(null, ms.map(function (m) { return by[m]; }));
    var W = Math.max(320, ms.length * 64), H = 200, top = 34, base = H - 30, bw = Math.min(44, W / ms.length - 20);
    var bars = ms.map(function (m, i) {
      var cx = (i + 0.5) * W / ms.length, h = Math.max(4, (base - top) * by[m] / max), y = base - h;
      return '<g><title>' + esc(monthName(m) + ' ' + money(by[m])) + '</title>' +
        '<path class="bar-m" d="M' + (cx - bw / 2) + ' ' + base + 'V' + (y + 4) + 'q0 -4 4 -4h' + (bw - 8) + 'q4 0 4 4V' + base + 'z"/>' +
        '<text class="bar-v" x="' + cx + '" y="' + (y - 8) + '" text-anchor="middle">' + esc(short(by[m])) + '</text>' +
        '<text class="bar-l" x="' + cx + '" y="' + (H - 8) + '" text-anchor="middle">' + esc(monthName(m)) + '</text></g>';
    }).join('');
    var sev = R.items.filter(function (it) { return it.code === 'SEVERANCE' && it.amount; })[0];
    var rows = ms.map(function (m) { return '<tr><th scope="row">' + esc(monthName(m)) + '</th><td>' + money(by[m]) + '</td></tr>'; }).join('');
    // 접어 두기: 결과 화면은 합계 · 항목이 먼저. 펼치면 막대 + 같은 값의 표(화면 읽기 프로그램 · 색 구분이 어려운 사람용)
    return '<details class="chart more"><summary id="chart-t">' + icon('cal') + t('result.chart') + '</summary><div class="stack chart-in">' +
      '<svg viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-labelledby="chart-t" focusable="false"><line class="bar-base" x1="0" x2="' + W + '" y1="' + base + '" y2="' + base + '"/>' + bars + '</svg>' +
      '<table class="chart-table"><caption class="sr">' + t('result.chart') + '</caption><tbody>' + rows + '</tbody></table>' +
      (sev ? '<p class="hint">' + t('result.chartSev', { n: money(sev.amount) }) + '</p>' : '') + '</div></details>';
  }
  // 막대 위 짧은 표기: 한국어 '18.4만', 다른 언어 '184K'
  function short(n) {
    if (S.lang === 'ko') return n >= 1e4 ? (Math.round(n / 1e3) / 10).toLocaleString('ko-KR') + '만' : n.toLocaleString('ko-KR');
    return n >= 1e6 ? (Math.round(n / 1e4) / 100) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'K' : String(n);
  }
  // '왜 이 금액인가요?' 단계 풀이: 기준 금액 → (하루 기록) → 계산식 → 받은 돈 빼기 → 법 근거
  function steps(it, R) {
    var out = [], f = it.formula, paidAt = f.indexOf(' − 받은 ');
    if (/HOLIDAY|OVERTIME|NIGHT|WEEKLY|MIN_WAGE/.test(it.code) || (it.code === 'BASE_WAGE' && S.f.wageType !== 'month')) {
      out.push([t('why.hourly', { w: term('ordinaryWage') }), R.hourlyFormula + ' = ' + R.hourly]);
    }
    if (it.code === 'SEVERANCE' && R.dailyFormula) out.push([t('why.daily', { w: term('averageWage') }), R.dailyFormula + ' = ' + R.daily]);
    if (/OVERTIME|NIGHT|HOLIDAY/.test(it.code)) {
      R.weeks.filter(function (w) { return w.month === it.month; }).forEach(function (w) {
        out.push([t('why.week', { d: krOrLocal(w.weekStart) }), w.formula]);
      });
    }
    // 받은 돈을 뺀 항목은 「2,299,000원 − 1,800,000원 = 499,000원」 한 줄로
    out.push([t('why.calc'), paidAt > 0 && it.amount != null ? f.slice(0, paidAt) + ' − ' + f.slice(paidAt + 6) + ' = ' + money(it.amount) : f]);
    // 근거 링크는 서버 응답이라 https 주소일 때만 링크로 (javascript: 같은 주소는 글자로만)
    var law = it.basis.map(function (b) {
      var name = esc(b.law + ' ' + b.article);
      return (/^https:\/\//.test(b.url || '') ? '<a href="' + esc(b.url) + '" target="_blank" rel="noopener">' + name + icon('out') + '</a>' : name) + ' ' + esc(b.summary);
    }).join('<br>');
    return '<ol class="why">' + out.map(function (s) { return '<li><b>' + s[0] + '</b><span lang="ko">' + esc(s[1]) + '</span></li>'; }).join('') +
      (law ? '<li><b>' + t('why.law') + '</b><span lang="ko">' + law + '</span></li>' : '') + '</ol>';
  }
  function vResult() {
    var R = viewResult();
    // 항목은 한 줄씩: 이름 · 금액 · 꺾쇠. 누르면 아래 시트에 쉬운 설명 · 계산식 · 근거
    // 금액이 있는 항목 · '월급에 포함' 같은 상태만 한 줄씩. 적지 않아 계산하지 않은 것은 한 줄로 묶고, 0원인 것은 아래에 작게 (눌러서 이유 보기)
    var missing = [], zero = [], cardZero = [];
    // 직접 적은 달력 기록이 있는 달 (평소 근무로 자동으로 채운 달의 '연장 · 야간 · 휴일 0원'은 보여 주지 않는다)
    var recorded = workDays().map(function (d) { return d.date.slice(0, 7); });
    // 고른 상황 카드와 관련된 항목이 0원이면 이유를 결과 위에 바로
    var CARD_ITEM = { SEVERANCE: 'sev', ANNUAL_LEAVE: 'leave', DISMISSAL_NOTICE: 'fired' };
    var items = R.items.map(function (it, i) {
      if (it.missing) { if (missing.indexOf(it.name) < 0) missing.push(it.name); return ''; }
      if (it.amount === 0 && /^(OVERTIME|NIGHT|HOLIDAY)$/.test(it.code) && recorded.indexOf(it.month) < 0) return '';
      if (it.amount === 0 && CARD_ITEM[it.code] && has(CARD_ITEM[it.code])) {
        cardZero.push('<li><b>' + esc(it.label) + ': ' + money(0) + '</b> <button type="button" class="btn-text" data-act="item" data-i="' + i + '">' + t('result.zeroWhyBtn') + '</button></li>');
        return '';
      }
      if (it.amount === 0) { zero.push('<button type="button" class="chip" data-act="item" data-i="' + i + '">' + esc(it.label) + '</button>'); return ''; }
      var right = it.amount != null ? '<span class="item-amt">' + money(it.amount) + '</span>' : '<span class="item-status">' + esc(it.status) + '</span>';
      var sub = it.amount ? readable(it.amount) : '';
      return '<li><button type="button" class="item-row" data-act="item" data-i="' + i + '"><span class="item-name">' + esc(it.label) +
        (it.gloss ? '<small class="gloss">' + esc(it.gloss) + '</small>' : '') +
        (it.estimated || sub ? '<small>' + (it.estimated ? '<span class="tag">' + t('result.estimated') + '</span> ' : '') + esc(sub) + '</small>' : '') + '</span>' + right + icon('chev', 'chev') + '</button></li>';
    }).join('');
    if (missing.length) {
      items += '<li><button type="button" class="item-row miss-row" data-act="miss-go"><span class="item-name">' + t('result.missRow', { n: missing.length }) + '<small>' + esc(missing.join(', ')) + '</small></span>' +
        '<span class="item-status">' + t('result.input') + '</span>' + icon('chev', 'chev') + '</button></li>';
    }
    var zeroBox = zero.length ? '<div class="zero-box"><span class="hint">' + t('result.zero') + '</span><div class="chips">' + zero.join('') + '</div></div>' : '';
    var size = t(S.f.size === 'under' ? 'size.under' : 'size.five') + (S.f.size === 'unsure' ? ' (' + t('result.estimated') + ')' : '');
    var period = dotDate(S.f.start) + ' ~ ' + (S.f.employed === 'yes' ? t('result.employed') : dotDate(S.f.end));
    var summary = '<div class="receipt"><div class="receipt-in">' +
      '<span class="total-label">' + t('result.total') + '</span>' +
      '<span class="total"><b data-count="' + R.total + '">' + R.total.toLocaleString('ko-KR') + '</b><small>' + t('unit') + '</small></span>' +
      '<span class="total-read">' + esc(readable(R.total)) + '</span>' +
      '<p class="disclaim">' + t('result.disclaim') + ' <button type="button" class="btn-text" data-act="disclaim">' + t('result.more') + '</button></p></div></div>' +
      '<p class="hint">' + esc(t('result.meta', { period: period, size: size, paid: money(R.paid) })) + '</p>';
    // 계산할 때 알아 둘 점: 항목별 금액 아래에 접어 둔다 (제목에 몇 개인지. 펼치면 전부)
    var warns = R.warnings.length ? '<details class="warn-d"><summary><span>' + t('result.warn') + ' <span class="warn-n">(' + R.warnings.length + ')</span></span></summary><ul>' +
      R.warnings.map(function (w) { return '<li>' + nobr(esc(w)) + '</li>'; }).join('') + '</ul></details>' : '';
    // 고른 상황과 관련된 0원 · 계산하지 않는 상황(방값 · 밥값 공제, 명세서)은 안내 화면으로
    var notes = (cardZero.length ? '<section class="note stack card-note" aria-labelledby="cz-t"><h2 id="cz-t">' + t('result.cardZeroTitle') + '</h2><ul class="plain">' + cardZero.join('') + '</ul></section>' : '') +
      (has('deduct') || has('payslip') ? '<section class="note stack card-note"><p>' + t(has('deduct') ? 'result.deductNote' : 'result.payslipNote') + '</p>' +
        '<button type="button" class="btn btn-line" style="align-self:flex-start" data-go="info">' + icon('info') + '<span>' + t('title.info') + '</span></button></section>' : '');
    var weeks = R.weeks.length ? '<dt>' + t('result.weeks') + '</dt><dd><ul class="weeks" lang="ko">' + R.weeks.map(function (w) { return '<li><b>' + esc(krOrLocal(w.weekStart)) + '~</b> ' + esc(w.formula) + '</li>'; }).join('') + '</ul></dd>' : '';
    // 더 할 수 있는 일: 무엇을 하려는지에 따라 세 묶음 (저장 · 다음 할 일 · 알아 두기). 다시 보기 · 지우기는 맨 아래 작은 글자 단추
    loadGuides();
    function group(title, tiles) {
      return '<section class="act-group stack" aria-label="' + esc(title) + '"><h3 class="label">' + title + '</h3><div class="act-grid">' + tiles + '</div></section>';
    }
    var more = '<section class="next-sec stack-lg" aria-labelledby="more-t"><h2 id="more-t">' + t('result.next') + '</h2>' +
      group(t('result.gSave'), tile('data-act="print"', t('result.pdf'), t('result.expire')) + tile('data-go="counselor"', t('result.counselor'), t('guide.calloutSub'))) +
      group(t('result.gNext'), tile('data-go="letter"', t('cp.letter'), t('cp.letterSub')) + tile('data-go="daeji"', t('title.daeji'), t('dj.sub')) + tile('data-go="guide"', t('guide.title'), t('guide.sub'))) +
      group(t('result.gInfo'), (GUIDES.length ? tile('data-go="info"', t('title.info'), t('info.sub')) : '') +
        tile('data-go="contract"', t('title.contract'), t('ct.sub')) + tile('data-go="deadline"', t('title.deadline'), t('dl.sub')) + tile('data-go="after"', t('title.after'), t('after.sub'))) +
      '<div class="act-foot"><button type="button" class="btn-text" data-go="review">' + icon('pen') + t('result.review') + '</button><button type="button" class="btn-text" data-act="wipe">' + icon('trash') + t('wipe.btn') + '</button></div></section>';
    // 휴대폰: 합계 · 진정서 단추 · 받을 돈만 먼저 보이고, 읽어 주기 · 달별 그래프 · 알아 둘 점 · 계산 기준 · 더 할 수 있는 일은 '더보기' 안 (.m-more)
    // 넓은 화면은 모두 그대로 보인다
    var moreBtn = '<button type="button" id="m-more-btn" class="m-more-btn only-m" data-act="m-more" aria-expanded="' + !!S.moreOpen + '">' + t(S.moreOpen ? 'result.moreClose' : 'result.moreOpen') + icon('chev', 'chev') + '</button>';
    return '<div class="wide col' + (S.moreOpen ? ' more-open' : '') + '">' + langBanner() +
      '<h1 tabindex="-1" class="sr">' + t('title.result') + '</h1>' +
      '<div class="result-grid"><div class="stack r-sum">' + summary + '<div class="m-more stack">' + speakBtn('result') + chart(R) + '</div>' + cta(primary(t('result.make'), 'data-go="form"'), ' ') + '</div>' +
      // 받을 돈이 먼저, 그 아래 고른 상황 안내 · 알아 둘 점
      '<div class="stack-lg r-items"><section><div class="items-head"><h2>' + t('result.items') + '</h2><span class="hint">' + t('result.itemsHint') + '</span></div><ul class="items">' + items + '</ul>' + zeroBox + '</section>' + notes + moreBtn +
      '<div class="m-more stack-lg">' + warns +
      '<details class="more"><summary>' + t('result.base') + '</summary><dl class="kv"><dt>' + term('ordinaryWage', t('result.hourly')) + '</dt><dd>' + R.hourly + '</dd><dt>' + term('averageWage', t('result.daily')) + '</dt><dd>' + R.daily + '</dd>' + weeks + '<dt>' + t('result.notApplied') + '</dt><dd lang="ko">' + esc(R.notApplied.join(' ') || t('result.none')) + '</dd></dl></details></div>' +
      '</div></div><div class="m-more">' + more + '</div></div>';
  }
  // 항목 상세 시트: 금액(서버 값 그대로) · 쉬운 설명(자동 생성 고지 한 번) · 단계 풀이 · 쉬운 말
  function itemSheet(i) {
    var R = viewResult(), it = R.items[i];
    if (!it) return;
    var word = ITEM_WORD[it.code];
    var exp = S.explainState === 'loading' ? skel(t('explain.loading'), ['', '', 'short'])
      : '<p>' + esc(it.explain) + '</p>' + (S.explainState === 'done' ? '<p class="hint">' + icon('info') + ' ' + t('explain.note') + '</p>' : '');
    openSheet(esc(it.label),
      (it.amount != null ? '<p class="sheet-amt">' + money(it.amount) + '<small>' + esc(readable(it.amount)) + '</small></p>' : '<p class="item-status">' + esc(it.status) + '</p>') +
      exp + '<h3 class="label">' + t('result.why') + '</h3>' + steps(it, R) +
      (word ? '<p>' + term(word, t('result.wordOf', { w: t('w.' + word) })) + '</p>' : '') +
      (it.missing ? '<button type="button" class="btn btn-line" data-go="' + (/OVERTIME|NIGHT|HOLIDAY/.test(it.code) ? 'cal' : 'q6') + '">' + icon('pen') + '<span>' + t('result.input') + '</span></button>' : ''), true);
    sheetItem = i;
  }

  // ---------- 읽어 주기 ----------
  // 한국어는 서버가 영상 나레이션과 같은 목소리(Gemini Charon, 안 되면 edge-tts InJoon)로 소리를 만들어 보낸다(/api/tts). 크롬에는 이 목소리가 없어서다.
  // 서버가 안 되거나 영어, 베트남어면 브라우저 목소리(Web Speech API)로 읽는다. 둘 다 안 되면 단추를 숨긴다
  var canSpeak = 'speechSynthesis' in window && typeof window.SpeechSynthesisUtterance === 'function';
  var speaking = '', audio = null;
  function voiceFor() {
    var vs = window.speechSynthesis.getVoices();
    if (!vs.length) return null;
    var mine = vs.filter(function (v) { return v.lang.toLowerCase().replace('_', '-').indexOf(S.lang) === 0; });
    if (!mine.length) return false;
    // 자연스러운 목소리 먼저: 엣지의 InJoon, 크롬의 Google 한국의, 그 언어의 Neural(en-US, vi-VN), 마지막에 기본 목소리
    function pick(re) { return mine.filter(function (v) { return re.test(v.name); })[0]; }
    return pick(/(InJoon|인준).*(Online|Natural)/i) || pick(/^Google 한국/) || pick(/(SunHi|선히).*(Online|Natural)/i) ||
      pick(/(Online|Natural|Neural).*(United States|Vietnam)|(en-US|vi-VN).*(Online|Natural|Neural)/i) || pick(/Online|Natural|Neural/i) ||
      pick(/^Google/) || mine.filter(function (v) { return v.default; })[0] || mine[0];
  }
  function speakBtn(kind) {
    if (S.lang !== 'ko' && (!canSpeak || voiceFor() === false)) return '';
    var on = speaking === kind;
    return '<button type="button" class="btn btn-line speak no-print" data-act="speak" data-v="' + kind + '" aria-pressed="' + on + '">' + icon(on ? 'stop' : 'sound') + '<span>' + t(on ? 'speak.stop' : 'speak.btn') + '</span></button>';
  }
  function plain(html) { var d = document.createElement('div'); d.innerHTML = html.replace(/<br>/g, ' '); return d.textContent; }
  // 금액은 '549만 5,561원' 처럼 읽기 쉬운 꼴로, 항목 사이는 마침표로 끊어 잠깐 쉬게 한다 (쉬운 말을 켜도 같은 규칙)
  function speechText(kind) {
    if (kind === 'result') {
      var R = viewResult();
      return [t('speak.total', { n: readable(R.total) || money(R.total) })].concat(R.items.filter(function (it) { return it.amount; }).map(function (it) {
        return it.label + ', ' + (readable(it.amount) || money(it.amount));
      }), [t('result.disclaim')]).join('. ');
    }
    return [plain(t('guide.title')), plain(t('guide.lead'))].concat([1, 2, 3, 4, 5, 6].map(function (n) { return n + '. ' + t('guide.s' + n) + '. ' + t('guide.s' + n + 'b'); })).join('. ');
  }
  // 문장마다 끊는다 (5.5 같은 소수점은 뒤에 빈칸이 없어 안 끊긴다). 문장 끝에 마침표가 없으면 붙인다
  function sentences(text) {
    return text.replace(/\.\./g, '.').replace(/([.?!])\s+/g, '$1\n').split('\n').map(function (x) { x = x.trim(); return x && !/[.?!]$/.test(x) ? x + '.' : x; })
      .filter(function (x) { return x; });
  }
  // 서버 한 번에 600자까지라 문장 단위로 묶어 보낸다
  function chunks(list) {
    var out = [''];
    list.forEach(function (x) { if ((out[out.length - 1] + ' ' + x).length > 600) out.push(''); out[out.length - 1] = (out[out.length - 1] + ' ' + x).trim(); });
    return out.filter(function (x) { return x; });
  }
  function stopSpeak() {
    if (canSpeak) window.speechSynthesis.cancel();
    if (audio) { audio.pause(); URL.revokeObjectURL(audio.src); audio = null; }
  }
  function speakDone(kind) { if (speaking === kind) { speaking = ''; if (S.route === kind) rerender(); } }
  function speakLocal(kind, list) {
    if (!canSpeak || voiceFor() === false) { speakDone(kind); return; }
    var ss = window.speechSynthesis, v = voiceFor(), i = 0;
    function next() {
      if (speaking !== kind) return;
      if (i >= list.length) { speakDone(kind); return; }
      var u = new window.SpeechSynthesisUtterance(list[i++]);
      if (v) u.voice = v;
      u.lang = v ? v.lang : S.lang;
      u.rate = 1;
      u.onend = function () { setTimeout(next, 350); };
      u.onerror = function () { speakDone(kind); };
      ss.speak(u);
    }
    next();
  }
  function speakServer(kind, list) {
    var parts = chunks(list), i = 0;
    var jobs = parts.map(function (x) {
      return fetch('/api/tts', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: x }) }).then(function (r) {
        if (!r.ok || (r.headers.get('Content-Type') || '').indexOf('audio/') !== 0) throw new Error('tts');
        return r.blob();
      });
    });
    function next() {
      if (speaking !== kind) return;
      if (i >= jobs.length) { audio = null; speakDone(kind); return; }
      var at = i++;
      jobs[at].then(function (b) {
        if (speaking !== kind) return;
        audio = new Audio(URL.createObjectURL(b));
        audio.onended = function () { URL.revokeObjectURL(audio.src); next(); };
        return audio.play();
      }).catch(function () {
        // 서버 목소리가 안 되면 남은 글을 브라우저 목소리로 이어 읽는다
        if (speaking !== kind) return;
        audio = null;
        speakLocal(kind, [].concat.apply([], parts.slice(at).map(function (x) { return sentences(x); })));
      });
    }
    next();
  }
  function speak(kind) {
    stopSpeak();
    if (speaking === kind) { speaking = ''; render(); return; }
    var list = sentences(speechText(kind));
    speaking = kind;
    if (S.lang === 'ko') speakServer(kind, list); else speakLocal(kind, list);
    rerender();
  }
  if (canSpeak) window.speechSynthesis.onvoiceschanged = function () { if (S.route === 'result' || S.route === 'guide') rerender(); };

  // ---------- 말로 입력하기 (Web Speech API) ----------
  // 지원하지 않는 브라우저에서는 단추를 숨긴다. 말한 내용은 글자로 바꾸려고 브라우저의 음성 인식 서비스로 보내진다
  var Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  var SPEECH_LANG = { ko: 'ko-KR', en: 'en-US', vi: 'vi-VN' };
  var rec = null;
  function listenBtn() {
    if (!Recognition) return '';
    var on = !!rec;
    return '<button type="button" id="listen" class="small-btn no-print" data-act="listen" aria-pressed="' + on + '">' + icon(on ? 'stop' : 'mic') +
      '<span>' + t(on ? 'voice.stop' : 'voice.start') + '</span></button>' +
      '<p class="hint" role="status">' + t(on ? 'voice.listening' : 'voice.note') + '</p>';
  }
  function listen() {
    if (rec) { rec.stop(); return; }
    // 이미 적은 글 뒤에 이어서 적는다. 서버 제한(2000자)에 맞춰 자른다
    var base = S.p.facts ? S.p.facts.replace(/\s*$/, ' ') : '';
    rec = new Recognition();
    rec.lang = SPEECH_LANG[S.lang] || 'ko-KR';
    rec.interimResults = true;
    rec.continuous = true;
    rec.onresult = function (e) {
      if (S.route !== 'form') { rec.stop(); return; }
      var done = '', now = '';
      for (var i = 0; i < e.results.length; i++) {
        if (e.results[i].isFinal) done += e.results[i][0].transcript; else now += e.results[i][0].transcript;
      }
      S.p.facts = (base + done).slice(0, 2000);
      var ta = document.getElementById('p-facts');
      if (ta) ta.value = (base + done + now).slice(0, 2000);
      save();
    };
    rec.onerror = function (e) {
      toastT(e.error === 'not-allowed' || e.error === 'service-not-allowed' ? 'voice.denied' : e.error === 'no-speech' ? 'voice.nothing' : 'voice.fail');
    };
    rec.onend = function () { rec = null; if (S.route === 'form') { render(); focusId('listen'); } };
    try {
      rec.start();
    } catch (err) {
      rec = null;
      toastT('voice.fail');
      return;
    }
    render();
    focusId('listen');
  }

  // ---------- 진정서 칸 입력 도우미 ----------
  // 전화번호: 숫자만 받아 적는 대로 하이픈을 넣는다 (010-1234-5678 · 02-123-4567 · 031-123-4567 · 1588-0075)
  function telFmt(v) {
    var d = String(v || '').replace(/\D/g, '');
    if (/^1[5-9]/.test(d)) { d = d.slice(0, 8); return d.length > 4 ? d.slice(0, 4) + '-' + d.slice(4) : d; }
    var a = d.indexOf('02') === 0 ? 2 : 3;
    d = d.slice(0, a === 2 ? 10 : 11);
    var r = d.slice(a);
    if (!r) return d;
    if (d.indexOf('01') === 0) return d.slice(0, a) + '-' + (r.length > 4 ? r.slice(0, 4) + '-' + r.slice(4) : r);
    if (r.length <= 3) return d.slice(0, a) + '-' + r;
    var m = r.length > 7 ? 4 : 3;
    return d.slice(0, a) + '-' + r.slice(0, m) + '-' + r.slice(m);
  }
  // 하이픈을 넣어도 커서는 방금 적은 숫자 뒤에 둔다
  function setTel(el) {
    var n = el.value.slice(0, el.selectionStart).replace(/\D/g, '').length, v = telFmt(el.value), i = 0;
    if (v === el.value) return v;
    el.value = v;
    while (n > 0 && i < v.length) { if (/\d/.test(v[i])) n--; i++; }
    el.setSelectionRange(i, i);
    return v;
  }
  // 사는 곳 주소 찾기: 카카오 우편번호 서비스(키 없음). 누를 때 처음 한 번만 불러온다
  // 못 불러왔으면(인터넷 · 차단) 이번 방문 동안만 직접 적게 둔다 (저장하지 않음)
  var pcLoad = null, addrManual = false;
  function loadPostcode() {
    if (window.daum && daum.Postcode) return Promise.resolve();
    if (!pcLoad) pcLoad = new Promise(function (ok, no) {
      var sc = document.createElement('script');
      sc.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      sc.onload = function () { daum.postcode.load(ok); };
      sc.onerror = function () { pcLoad = null; sc.remove(); no(); };
      document.head.appendChild(sc);
    });
    return pcLoad;
  }
  function findAddr(k) {
    loadPostcode().then(function () {
      openSheet(t('form.addrFind'), '<div id="pc-box" class="pc-box"></div>', false);
      new daum.Postcode({
        width: '100%', height: '100%',
        oncomplete: function (d) {
          var a = (d.userSelectedType === 'J' ? d.jibunAddress : d.roadAddress) || d.address;
          if (d.apartment === 'Y' && d.buildingName) a += ' (' + d.buildingName + ')';
          S.lt = null; S.p[k] = a; S.pErr[k] = ''; if (S.psrc) S.psrc[k] = ''; save();
          closeSheet(); render(); focusId('p-' + k);
        }
      }).embed(document.getElementById('pc-box'));
    }, function () {
      addrManual = true; render(); focusId('p-' + k); toastT('form.addrFail');
    });
  }
  // 가게 찾기: 가게 이름으로 찾아 고르면 주소를 채운다 (가게 이름 · 전화번호 칸이 비어 있으면 그것도)
  // 카카오 장소 검색은 우리 서버(/api/v1/places)가 대신 부른다. 키는 서버에만 있다
  var places = [], plSeq = 0;
  function findPlace() {
    var q = S.f.company || S.p.company || '';
    openSheet(t('form.plTitle'), '<div class="pl-form"><input id="pl-q" class="input" type="search" enterkeyhint="search" autocomplete="off" aria-label="' + esc(t('form.plQ')) + '" placeholder="' + esc(t('form.plPh')) + '" value="' + esc(q) + '">' +
      '<button type="button" class="btn btn-primary" data-act="pl-go"><span>' + t('form.plGo') + '</span></button></div><div id="pl-list" class="pl-list" aria-live="polite"></div>', false);
    if (q.trim().length >= 2) searchPlace(); else focusId('pl-q');
  }
  function searchPlace() {
    var box = document.getElementById('pl-q'), list = document.getElementById('pl-list');
    if (!box || !list) return;
    var q = box.value.trim(), seq = ++plSeq;
    if (q.length < 2) { list.innerHTML = '<p class="hint">' + t('form.plShort') + '</p>'; box.focus(); return; }
    list.innerHTML = '<p class="hint">' + t('form.plWait') + '</p>';
    fetch('/api/v1/places?q=' + encodeURIComponent(q)).then(function (r) { if (!r.ok) throw r; return r.json(); }).then(function (d) {
      if (seq !== plSeq) return;
      places = d;
      list.innerHTML = d.length ? '<ul class="plain">' + d.map(function (pl, i) {
        return '<li><button type="button" class="pl-item" data-act="pl-pick" data-i="' + i + '"><strong>' + esc(pl.name) + (pl.category ? ' <small>' + esc(pl.category) + '</small>' : '') + '</strong><span>' + esc(pl.address) + '</span></button></li>';
      }).join('') + '</ul>' : '<p class="hint">' + t('form.plNone') + '</p>';
    }, function () { if (seq === plSeq) list.innerHTML = '<p class="hint">' + t('form.plFail') + '</p>'; });
  }
  function pickPlace(i) {
    var pl = places[i], p = S.p;
    if (!pl) return;
    function put(k, v) { p[k] = v; S.pErr[k] = ''; if (S.psrc) S.psrc[k] = ''; }
    put('waddr', pl.address);
    if (!S.f.company && !p.company) put('company', pl.name);
    if (!p.wphone && pl.phone) put('wphone', pl.phone);
    S.lt = null; save(); closeSheet(); render(); focusId('p-waddr');
  }

  // ---------- 화면: 진정서 정보 ----------
  function vForm() {
    var R = viewResult();
    var p = S.p;
    function inp(id, key, label, type, opts) {
      opts = opts || {};
      // 빈 필수 칸은 빨간 테두리 없이 표시만 (문서 만들기를 누르면 첫 빈 칸으로 이동). 서버가 알려 준 오류만 문구로
      var empty = opts.req && S.formTried && !p[key], miss = S.pErr[key];
      var doc = p[key] && (S.psrc || {})[key] === 'doc' ? ' <span class="tag">' + t('form.fromDoc') + '</span>' : '';
      // find: 'addr' = 주소 찾기로만 넣는 칸 (못 불러오면 직접), 'place' = 가게 이름으로 찾기 + 직접 적기
      var post = opts.find === 'addr' && !addrManual;
      var box = '<input id="' + id + '" class="input" type="' + type + '"' + (opts.mode ? ' inputmode="' + opts.mode + '"' : '') + (opts.ac ? ' autocomplete="' + opts.ac + '"' : '') + (opts.ph ? ' placeholder="' + esc(opts.ph) + '"' : '') +
        (post ? ' readonly data-act="addr" data-k="' + key + '" placeholder="' + esc(t('form.addrPh')) + '"' : '') +
        ' data-p="' + key + '" value="' + esc(p[key]) + '"' + (opts.req ? ' aria-required="true"' : '') + (empty ? ' data-miss' : '') + (miss ? ' aria-invalid="true" aria-describedby="' + id + '-err"' : '') + '>';
      if (opts.find === 'place' || post) {
        box = '<div class="addr-row">' + box + (post ? '<button type="button" class="btn btn-line" data-act="addr" data-k="' + key + '">' + icon('search') + '<span>' + t('form.addrFind') + '</span></button>'
          : '<button type="button" class="btn btn-line" data-act="place">' + icon('search') + '<span>' + t('form.plBtn') + '</span></button>') + '</div>';
      }
      return '<div class="field' + (opts.wide ? ' wide-f' : '') + '"><label for="' + id + '">' + label + (opts.req ? ' <span class="req">' + t('form.req') + '</span>' : '') + doc + '</label>' + box +
        (miss ? '<p id="' + id + '-err" class="err">' + esc(miss) + '</p>' : '') + '</div>';
    }
    function docTag(k) { return S.src[k] === 'doc' ? ' <span class="tag">' + t('form.fromDoc') + '</span>' : ''; }
    var auto = '<dl class="kv"><dt>' + t('form.kCompany') + '</dt><dd><span data-auto-company>' + esc(S.f.company || S.p.company || t('form.noCompany')) + '</span>, ' + t(S.f.size === 'under' ? 'size.under' : 'size.five') + docTag('company') + '</dd><dt>' + t('form.kPeriod') + '</dt><dd>' + esc(dotDate(S.f.start)) + ' ~ ' + esc(S.f.employed === 'yes' ? t('result.employed') : dotDate(S.f.end)) + docTag('start') + '</dd><dt>' + t('form.kUnpaid') + '</dt><dd><strong>' + money(R.total) + '</strong></dd></dl>';
    var nDoc = Object.keys(S.psrc || {}).filter(function (k) { return S.psrc[k] === 'doc' && p[k]; }).length;
    var docNote = nDoc ? '<p class="note" role="status">' + icon('check') + ' ' + t('form.docNote', { n: nDoc }) + '</p>' : '';
    var got = READY.filter(function (k) { return S.ready[k]; }).length;
    // 이미 채워진 내용 + 고치기 + 챙길 서류를 한 상자에 (넓은 화면은 옆 칸, 휴대폰은 맨 위)
    var autoBox = function (cls) {
      return '<section class="note stack auto-box ' + cls + '"><div class="auto-head"><h2>' + t('form.auto') + '</h2><button type="button" class="btn-text" data-go="review">' + t('form.fix') + '</button></div>' + auto +
        '<div class="nav-list">' + navRow('data-go="ready"', t('form.readyGo'), t('form.ready', { n: got, m: READY.length })) + '</div></section>';
    };
    return '<div class="split">' +
      '<div class="stack-lg">' + progress(4) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('form.title') + '</h1><p class="lead">' + t('form.lead') + '</p></div>' + docNote +
      autoBox('only-m') +
      '<fieldset class="stack"><legend><h2>' + t('form.me') + '</h2></legend><div class="form-grid">' +
      inp('p-name', 'name', t('form.name'), 'text', { req: t('form.nameReq'), ac: 'name' }) +
      inp('p-phone', 'phone', t('form.phone'), 'tel', { req: t('form.phoneReq'), mode: 'tel', ac: 'tel', ph: '010-0000-0000' }) +
      inp('p-addr', 'addr', t('form.addr'), 'text', { ac: 'street-address', wide: true, find: 'addr' }) + '</div></fieldset>' +
      '<fieldset class="stack"><legend><h2>' + t('form.work') + '</h2></legend><div class="form-grid">' +
      (S.f.company ? '' : inp('p-company', 'company', t('form.company'), 'text', { req: t('form.companyReq') })) +
      inp('p-owner', 'owner', t('form.owner'), 'text', { req: t('form.ownerReq') }) +
      inp('p-waddr', 'waddr', t('form.waddr'), 'text', { req: t('form.waddrReq'), wide: true, find: 'place' }) +
      inp('p-wphone', 'wphone', t('form.wphone'), 'tel', { mode: 'tel', ph: '02-000-0000' }) +
      inp('p-job', 'job', t('form.job'), 'text', { req: t('form.jobReq'), ph: t('form.jobPh') }) + '</div></fieldset>' +
      '<div class="field"><label for="p-facts">' + t('form.facts') + '</label><p id="facts-help" class="hint">' + t('form.factsHelp') + '</p>' +
      '<textarea id="p-facts" class="input" rows="5" aria-describedby="facts-help" data-p="facts">' + esc(p.facts) + '</textarea>' +
      listenBtn() +
      (S.pErr.facts ? '<p class="err">' + esc(S.pErr.facts) + '</p>' : '') + '</div>' +
      '</div>' +
      '<aside class="side">' + autoBox('only-d') +
      '<p class="hint privacy">' + icon('lock') + ' ' + t('form.privacy') + '</p>' +
      cta(primary(t('form.make'), 'data-act="make"'), ' ') + '</aside></div>';
  }

  // ---------- 진정서 내용 (complaint-draft 응답) ----------
  // 금액이 든 문단은 고칠 수 없고, '진정 내용' 문단만 고칠 수 있다
  var EDITABLE = '진정 내용';
  function draftSections() {
    return S.draft.draftKo.sections.map(function (s) {
      return s.heading === EDITABLE && S.factsText != null ? { heading: s.heading, body: S.factsText } : s;
    });
  }
  function signMonth() { var d = new Date(); return d.getFullYear() + '년 ' + (d.getMonth() + 1) + '월'; }
  function plainText() {
    var out = [S.draft.draftKo.title, ''];
    draftSections().forEach(function (s) { out.push('[' + s.heading + ']', s.body, ''); });
    out.push(signMonth() + '    일', '진정인 ' + (S.p.name || '[이름]') + ' (서명)');
    return out.join('\n');
  }

  // ---------- 화면: 진정서 초안 ----------
  // 진정서 본문은 노동청에 내는 한국어 원문 그대로, 화면 안내만 고른 언어로
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
        body = '<label class="sr" for="facts-edit">' + t('cp.editLabel') + '</label><textarea id="facts-edit" class="input" rows="7">' + esc(s.body) + '</textarea>' +
          '<div class="btn-row no-print"><button type="button" class="btn btn-line" data-act="facts-cancel">' + t('cp.cancel') + '</button><button type="button" class="btn btn-primary" style="min-height:48px;font-size:1rem" data-act="facts-save">' + t('cp.save') + '</button></div>';
      } else {
        body = '<p>' + esc(s.body) + '</p>' + (editable ? '<button type="button" class="small-btn no-print" data-act="facts-edit">' + icon('pen') + t('cp.editBtn') + '</button>' : '');
      }
      // 다듬기에 실패해 적은 그대로 들어갔으면 표시를 바꾸고 직접 고치라고 알린다 (직접 고친 뒤에는 표시 없음)
      var raw = d.factsAsWritten && S.factsText == null;
      var head = '<h3>' + esc(s.heading) + (editable && S.factsText == null ? '<span class="no-print' + (raw ? ' raw' : '') + '">' + t(raw ? 'cp.rawTag' : 'cp.aiTag') + '</span>' : '') + '</h3>' +
        (editable && raw && !S.editing ? '<p class="warnbox no-print">' + t('cp.rawNote') + '</p>' : '');
      var inline = showTr && trs[i] ? '<p class="tr-inline" lang="' + trLang + '">' + esc(trs[i].body) + '</p>' : '';
      return '<section>' + head + body + inline + '</section>';
    }).join('');
    var tr = trs.map(function (s) {
      return '<section><h3>' + esc(s.heading) + '</h3><p>' + esc(s.body) + '</p></section>';
    }).join('');
    var trName = langOf(trLang).name;
    var seg = trs.length ?
      '<div class="seg no-print" role="group" aria-label="' + t('cp.view') + '"><button type="button" data-act="tr" data-v="0" aria-pressed="' + !S.tr + '">' + t('cp.ko') + '</button><button type="button" data-act="tr" data-v="1" aria-pressed="' + S.tr + '">' + t('cp.both') + '</button></div>' : '';
    return '<div class="split' + (showTr ? ' cp-both' : '') + '"><div class="stack-lg">' + progress(4) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('title.complaint') + '</h1><p class="note">' + t('cp.note') + '</p>' +
      '<p class="hint no-print">' + t('cp.fixHint') + '</p><button type="button" class="btn-text no-print" style="align-self:flex-start" data-go="review">' + t('cp.recalc') + '</button></div>' +
      seg +
      '<div class="paper-grid' + (showTr ? ' tr-on' : '') + '">' +
      '<article class="paper" lang="ko"><h2 class="doc-title">' + esc(d.draftKo.title) + '</h2>' + ko +
      '<p class="sign">' + signMonth() + ' [  ]일<br>진정인 ' + esc(S.p.name || '[이름]') + ' (서명)</p>' +
      '<p class="foot">' + esc(d.disclaimer) + ' 제출 전 내용이 사실과 같은지 확인하세요.</p></article>' +
      (showTr ? '<article class="paper tr-col" lang="' + trLang + '" aria-label="' + t('cp.trLabel') + '"><p class="hint">' + esc(trName) + ' ' + t('cp.trLabel') + '</p>' + tr + '</article>' : '') +
      '</div>' +
      (S.copyFallback ? '<div class="field no-print"><label for="copybox">' + t('cp.copyBox') + '</label><textarea id="copybox" class="input copybox" readonly lang="ko">' + esc(plainText()) + '</textarea></div>' : '') +
      '</div><aside class="side no-print">' +
      saveBtns() +
      '<p class="hint">' + t('cp.docxHint') + '<br>' + t('cp.copyHint') + '</p>' +
      '<button type="button" class="btn-text" style="align-self:flex-start" data-go="guide">' + t('cp.where') + '</button>' +
      '<div class="nav-list">' + navRow('data-go="letter"', t('cp.letter'), t('cp.letterSub')) + '</div></aside></div>';
  }

  // 저장 단추: PDF(인쇄 창) · 한글/Word 파일(.docx) · 인쇄 · 전체 복사
  function saveBtns() {
    // 휴대폰에서는 첫 .cta(PDF 저장)만 아래 고정 칸으로 간다. 나머지 셋은 본문에 남겨 고정 칸이 화면을 덜 가리게
    return cta(primary(t('cp.pdf'), 'data-act="print"'), ' ') + '<div class="cta save-more">' +
      '<button type="button" class="btn btn-line" data-act="docx">' + icon('file') + '<span>' + t('cp.docx') + '</span></button>' +
      '<div class="btn-row"><button type="button" class="btn btn-line" data-act="print">' + icon('print') + '<span>' + t('cp.print') + '</span></button><button type="button" class="btn btn-line" data-act="copy">' + icon('copy') + '<span>' + t('cp.copy') + '</span></button></div></div>';
  }

  // ---------- 한글 · Word 파일(.docx) ----------
  // 압축하지 않은 zip 에 Word 문서 XML 세 개만 담는다. 한글(한컴오피스)과 Word 둘 다 연다. 서버로 보내지 않고 이 기기에서 만든다
  var CRC = null;
  function crc32(b) {
    if (!CRC) { CRC = []; for (var n = 0; n < 256; n++) { var c = n; for (var k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; CRC[n] = c >>> 0; } }
    var x = -1;
    for (var i = 0; i < b.length; i++) x = CRC[(x ^ b[i]) & 255] ^ (x >>> 8);
    return (x ^ -1) >>> 0;
  }
  function zipStore(files, type) {
    var enc = new TextEncoder(), parts = [], dir = [], off = 0;
    files.forEach(function (f) {
      var name = enc.encode(f[0]), d = enc.encode(f[1]), crc = crc32(d);
      var h = new DataView(new ArrayBuffer(30));
      h.setUint32(0, 0x04034b50, true); h.setUint16(4, 20, true); h.setUint16(6, 0x0800, true); h.setUint16(12, 0x21, true);
      h.setUint32(14, crc, true); h.setUint32(18, d.length, true); h.setUint32(22, d.length, true); h.setUint16(26, name.length, true);
      parts.push(new Uint8Array(h.buffer), name, d);
      var c = new DataView(new ArrayBuffer(46));
      c.setUint32(0, 0x02014b50, true); c.setUint16(4, 20, true); c.setUint16(6, 20, true); c.setUint16(8, 0x0800, true); c.setUint16(14, 0x21, true);
      c.setUint32(16, crc, true); c.setUint32(20, d.length, true); c.setUint32(24, d.length, true); c.setUint16(28, name.length, true); c.setUint32(42, off, true);
      dir.push(new Uint8Array(c.buffer), name);
      off += 30 + name.length + d.length;
    });
    var size = dir.reduce(function (s, a) { return s + a.length; }, 0);
    var e = new DataView(new ArrayBuffer(22));
    e.setUint32(0, 0x06054b50, true); e.setUint16(8, files.length, true); e.setUint16(10, files.length, true); e.setUint32(12, size, true); e.setUint32(16, off, true);
    return new Blob(parts.concat(dir, [new Uint8Array(e.buffer)]), { type: type });
  }
  function xmlEsc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  // 문단 하나: opts.b 굵게 · opts.sz 글자 크기(반 포인트) · opts.c 가운데 · opts.r 오른쪽 · opts.after 아래 띄움
  function wPara(text, opts) {
    opts = opts || {};
    var rpr = '<w:rPr><w:rFonts w:ascii="Malgun Gothic" w:hAnsi="Malgun Gothic" w:eastAsia="맑은 고딕"/>' + (opts.b ? '<w:b/>' : '') + '<w:sz w:val="' + (opts.sz || 22) + '"/></w:rPr>';
    return '<w:p><w:pPr>' + (opts.c ? '<w:jc w:val="center"/>' : opts.r ? '<w:jc w:val="right"/>' : '') + '<w:spacing w:after="' + (opts.after == null ? 120 : opts.after) + '" w:line="360" w:lineRule="auto"/></w:pPr>' +
      (text ? '<w:r>' + rpr + '<w:t xml:space="preserve">' + xmlEsc(text) + '</w:t></w:r>' : '') + '</w:p>';
  }
  function docx(title, sections, tail) {
    var body = wPara(title, { b: true, sz: 36, c: true, after: 360 });
    sections.forEach(function (s) {
      body += wPara(s.heading, { b: true, sz: 24, after: 80 });
      String(s.body).split('\n').forEach(function (line) { body += wPara(line); });
      body += wPara('', { after: 120 });
    });
    tail.forEach(function (line, i) { body += wPara(line, { r: i < tail.length - 1, sz: i < tail.length - 1 ? 22 : 18, after: i === tail.length - 2 ? 360 : 120 }); });
    var W = 'http://schemas.openxmlformats.org/';
    return zipStore([
      ['[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="' + W + 'package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'],
      ['_rels/.rels', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="' + W + 'package/2006/relationships"><Relationship Id="rId1" Type="' + W + 'officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'],
      ['word/document.xml', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="' + W + 'wordprocessingml/2006/main"><w:body>' + body +
        '<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1701" w:right="1440" w:bottom="1440" w:left="1440" w:header="851" w:footer="992" w:gutter="0"/></w:sectPr></w:body></w:document>']
    ], 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
  }
  function saveDocx() {
    var name = S.p.name || '[이름]', blob, file;
    if (S.route === 'letter' && S.lt) {
      blob = docx(S.lt.draftKo.title, S.lt.draftKo.sections, [todayDot(), '발신인 ' + name + ' (서명 또는 날인)', S.lt.disclaimer + ' 보내기 전 내용이 사실과 같은지 확인하세요.']);
      file = '내용증명_초안.docx';
    } else {
      blob = docx(S.draft.draftKo.title, draftSections(), [signMonth() + ' [  ]일', '진정인 ' + name + ' (서명)', S.draft.disclaimer + ' 제출 전 내용이 사실과 같은지 확인하세요.']);
      file = '임금체불_진정서_초안.docx';
    }
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = file;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
    toastT('cp.docxDone');
  }

  // ---------- 화면: 회사에 보내는 내용증명 초안 (demand-letter 응답) ----------
  // 보내는 사람 · 받는 사람 · 금액은 앞에서 받은 값 그대로. 여기서 고르는 것은 계좌(선택) · 기한뿐
  function todayDot() { var d = new Date(); return d.getFullYear() + '. ' + (d.getMonth() + 1) + '. ' + d.getDate() + '.'; }
  function letterText() {
    var out = [S.lt.draftKo.title, ''];
    S.lt.draftKo.sections.forEach(function (s) { out.push('[' + s.heading + ']', s.body, ''); });
    out.push(todayDot(), '발신인 ' + (S.p.name || '[이름]') + ' (서명 또는 날인)');
    return out.join('\n');
  }
  function vLetter() {
    var p = S.p, f = S.f, R = viewResult(), d = S.lt;
    var sev = f.employed !== 'yes' && R.items.some(function (it) { return it.code === 'SEVERANCE' && it.amount; });
    var head = '<div class="stack"><h1 tabindex="-1">' + t('title.letter') + '</h1><p class="lead">' + t('lt.lead') + '</p><p class="note">' + t('lt.note') + '</p></div>';
    if (!d) {
      var ok = letterReady();
      var who = '<dl class="kv"><dt>' + t('lt.from') + '</dt><dd>' + esc(p.name || '—') + (p.addr ? '<br>' + esc(p.addr) : '') + '</dd>' +
        '<dt>' + t('lt.to') + '</dt><dd>' + esc((f.company || p.company || '—') + (p.owner ? ' (' + p.owner + ')' : '')) + (p.waddr ? '<br>' + esc(p.waddr) : '') + '</dd>' +
        '<dt>' + t('lt.amount') + '</dt><dd><strong>' + money(R.total) + '</strong></dd></dl>';
      var days = ['7', '10', '14'].map(function (n) { return '<option value="' + n + '"' + (S.ltIn.days === n ? ' selected' : '') + '>' + t('lt.daysN', { n: n }) + '</option>'; }).join('');
      return '<div class="split"><div class="stack-lg">' + progress(4) + langBanner() + head +
        '<section class="note stack">' + who + '<button type="button" class="btn-text" style="align-self:flex-start" data-go="form">' + t('lt.fix') + '</button></section>' +
        (ok ? '' : '<p class="warnbox" role="status">' + t('lt.need') + '</p>') +
        '<div class="field"><label for="lt-account">' + t('lt.account') + ' <span class="opt">(' + t('opt') + ')</span></label><p id="lt-account-h" class="hint">' + t('lt.accountHint') + '</p>' +
        '<input id="lt-account" class="input" type="text" autocomplete="off" maxlength="80" data-lt="account" aria-describedby="lt-account-h" placeholder="' + esc(t('lt.accountPh')) + '" value="' + esc(S.ltIn.account) + '"></div>' +
        '<div class="field"><label for="lt-days">' + t('lt.days') + '</label><p id="lt-days-h" class="hint">' + t('lt.daysHint') + '</p><select id="lt-days" class="input" data-lt="days" aria-describedby="lt-days-h">' + days + '</select></div>' +
        (sev ? '<p class="hint">' + t('lt.sev') + '</p>' : '') + '<p class="hint">' + t('lt.facts') + '</p></div>' +
        '<aside class="side"><p class="hint">' + icon('lock') + ' ' + t('lt.privacy') + '</p>' +
        cta(ok ? primary(t('lt.make'), 'data-act="make-letter"') : primary(t('lt.needGo'), 'data-go="form"'), ' ') + '</aside></div>';
    }
    // 초안: 우체국에 내는 한국어 원문 + 고른 언어의 참고 번역(문단 순서가 같다)
    var trs = d.translation ? d.translation.sections : [], trLang = d.translation ? d.translation.lang : 'ko', showTr = S.tr && trs.length > 0;
    var ko = d.draftKo.sections.map(function (s, i) {
      return '<section><h3>' + esc(s.heading) + '</h3><p>' + esc(s.body) + '</p>' + (showTr && trs[i] ? '<p class="tr-inline" lang="' + trLang + '">' + esc(trs[i].body) + '</p>' : '') + '</section>';
    }).join('');
    var tr = trs.map(function (s) { return '<section><h3>' + esc(s.heading) + '</h3><p>' + esc(s.body) + '</p></section>'; }).join('');
    var seg = trs.length ? '<div class="seg no-print" role="group" aria-label="' + t('cp.view') + '"><button type="button" data-act="tr" data-v="0" aria-pressed="' + !S.tr + '">' + t('cp.ko') + '</button><button type="button" data-act="tr" data-v="1" aria-pressed="' + S.tr + '">' + t('cp.both') + '</button></div>' : '';
    return '<div class="split' + (showTr ? ' cp-both' : '') + '"><div class="stack-lg">' + progress(4) + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('title.letter') + '</h1><p class="note">' + t('lt.note') + '</p>' +
      (d.factsAsWritten ? '<p class="warnbox no-print">' + t('lt.rawNote') + '</p>' : '') + '</div>' + seg +
      '<div class="paper-grid' + (showTr ? ' tr-on' : '') + '">' +
      '<article class="paper" lang="ko"><h2 class="doc-title" id="lt-title" tabindex="-1">내 용 증 명</h2>' + ko +
      '<p class="sign">' + todayDot() + '<br>발신인 ' + esc(p.name || '[이름]') + ' (서명 또는 날인)</p>' +
      '<p class="foot">' + esc(d.disclaimer) + ' 보내기 전 내용이 사실과 같은지 확인하세요.</p></article>' +
      (showTr ? '<article class="paper tr-col" lang="' + trLang + '" aria-label="' + t('cp.trLabel') + '"><p class="hint">' + esc(langOf(trLang).name) + ' ' + t('cp.trLabel') + '</p>' + tr + '</article>' : '') + '</div>' +
      (S.copyFallback ? '<div class="field no-print"><label for="copybox">' + t('cp.copyBox') + '</label><textarea id="copybox" class="input copybox" readonly lang="ko">' + esc(letterText()) + '</textarea></div>' : '') +
      '</div><aside class="side no-print">' +
      saveBtns() +
      '<p class="hint">' + t('cp.docxHint') + '</p><p class="hint">' + t('lt.post') + '</p>' + (sev ? '<p class="hint">' + t('lt.sev') + '</p>' : '') +
      '<button type="button" class="btn-text" style="align-self:flex-start" data-act="letter-reset">' + t('lt.again') + '</button></aside></div>';
  }

  // ---------- 화면: 절차 ----------
  function vGuide() {
    var steps = [1, 2, 3, 4, 5, 6].map(function (n) {
      return '<li><div class="tl-mark" aria-hidden="true"><span class="tl-num">' + n + '</span>' + (n < 6 ? '<span class="tl-line"></span>' : '') + '</div>' +
        '<div class="tl-body"><strong>' + t('guide.s' + n) + '</strong><p>' + t('guide.s' + n + 'b') + '</p>' +
        (n === 1 ? '<div class="nav-list">' + navRow('href="https://labor.moel.go.kr" target="_blank" rel="noopener"', t('guide.link1')) + navRow('data-go="help"', t('guide.link2')) + '</div>' + '<p class="hint terms"><span class="terms-label">' + icon('book') + t('words.hard') + '</span>' + term('complaint') + ' ' + term('laborOffice') + '</p>' : '') +
        (n === 6 ? '<div class="nav-list">' + navRow('data-go="daeji"', t('title.daeji')) + '</div>' : '') + '</div></li>';
    }).join('');
    var bring = [1, 2, 3, 4].map(function (n) { return '<li>' + icon('dot') + t('guide.bring' + n) + '</li>'; }).join('');
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('guide.title') + '</h1><p class="lead">' + t('guide.lead') + '</p>' + speakBtn('guide') + '</div>' +
      '<ol class="timeline">' + steps + '</ol>' +
      '<section class="stack"><h2>' + t('guide.bring') + '</h2><ul class="bring">' + bring + '</ul><div class="nav-list">' + navRow('data-go="ready"', t('guide.readyGo')) + navRow('data-go="words"', t('title.words')) + infoRow() + '</div></section></div>' +
      '<aside class="side">' +
      '<button type="button" class="callout" data-go="counselor">' + icon('chat', 'ico-lg') + '<span><strong>' + t('guide.callout') + '</strong><small>' + t('guide.calloutSub') + '</small></span>' + icon('arrow-r') + '</button>' +
      '<section class="note stack"><h2>' + t('guide.help') + '</h2>' + helpPhones() + '<p class="hint">' + t('guide.phoneHint') + '</p></section>' +
      cta(primary(t('guide.cta'), 'data-go="form"'), ' ') + '</aside></div>';
  }

  // ---------- 화면: 상담원 (창구에서 보여 주거나 인쇄. 본문은 늘 한국어 · 큰 글씨) ----------
  function vCounselor() {
    var R = viewResult();
    var f = S.f, p = S.p;
    var me = langOf(S.lang);
    var by = {};
    R.items.forEach(function (it) { if (it.month && it.amount) by[it.month] = (by[it.month] || 0) + it.amount; });
    var ms = monthList().slice().sort();
    var recorded = workDays().map(function (d) { return d.date.slice(0, 7); });
    var rows = R.items.filter(function (it) {
      if (it.amount === 0 && /^(OVERTIME|NIGHT|HOLIDAY)$/.test(it.code) && recorded.indexOf(it.month) < 0) return false;
      return it.amount != null || it.missing;
    }).map(function (it) {
      var name = it.ko;
      return '<tr><th scope="row">' + esc(name + (it.month ? ' (' + Number(it.month.slice(5)) + '월)' : '') + (it.estimated ? ' (추정)' : '')) + '</th><td class="n">' + (it.amount != null ? won(it.amount) : '정보 없음') + '</td></tr>';
    }).join('');
    var monthsRows = ms.map(function (m) { return '<li>' + m.slice(0, 4) + '년 ' + Number(m.slice(5)) + '월: 월급과 수당 ' + won(by[m] || 0) + '</li>'; }).join('');
    loadOffices();
    var office = officeOf();
    var brought = READY.filter(function (k) { return S.ready[k]; }).map(function (k) { return L.ko['ready.' + k]; });
    return '<div class="col stack-lg">' +
      '<p class="banner no-print">' + esc(t('counsel.top')) + '</p>' +
      '<article class="paper counsel" lang="ko"><div class="stack" style="gap:6px"><h1 tabindex="-1">임금체불 상담 요청</h1>' +
      (S.lang !== 'ko' ? '<p class="counsel-lang">저는 ' + esc(me.koName || me.name || S.lang) + '를 씁니다. 통역이 필요할 수 있습니다.</p>' : '') + '</div>' +
      '<div class="big-sum"><span class="total-label">받지 못한 돈 합계 (참고 계산)</span><span class="total">' + won(R.total) + '</span><span>' + korRead(R.total) + '</span></div>' +
      '<dl class="kv"><dt>체불 기간</dt><dd>' + (ms.length ? ms[0].slice(0, 4) + '년 ' + Number(ms[0].slice(5)) + '월' + (ms.length > 1 ? ' ~ ' + ms[ms.length - 1].slice(0, 4) + '년 ' + Number(ms[ms.length - 1].slice(5)) + '월' : '') + ' (' + ms.length + '개월)' : '') + '</dd>' +
      '<dt>받은 돈</dt><dd>' + won(R.paid) + '</dd>' +
      '<dt>사업장</dt><dd>' + esc(f.company || p.company || '(적지 않음)') + ', ' + (f.size === 'under' ? '5명 미만' : '5명 이상') + '</dd>' +
      '<dt>일한 기간</dt><dd>' + esc(dotDate(f.start)) + ' ~ ' + esc(f.employed === 'yes' ? '재직 중' : dotDate(f.end) + ' (퇴직)') + '</dd>' +
      '<dt>근무</dt><dd>' + esc(p.job || '') + (p.job ? ', ' : '') + esc(dayText()) + (f.tStart !== '' ? ' ' + hh(f.tStart) + '~' + hh(f.tEnd) : '') + (workDays().length ? ', 달력 기록 ' + workDays().length + '일' : '') + '</dd>' +
      '<dt>약속한 급여</dt><dd>' + ({ hour: '시급 ', day: '일급 ', month: '월 ' }[f.wageType] || '') + (f.wage ? won(f.wage) : '') + (f.allowance ? ' + 수당 ' + won(f.allowance) : '') + '</dd>' +
      (office ? '<dt>관할 노동청</dt><dd>' + esc(office.name) + '</dd>' : '') + '</dl>' +
      (monthsRows ? '<section class="stack" style="gap:4px"><h2>달별 못 받은 월급과 수당 (퇴직금 빼고)</h2><ul class="counsel-months">' + monthsRows + '</ul></section>' : '') +
      '<table><caption>항목별 금액</caption><tbody>' + rows + '<tr class="sum"><th scope="row">합계</th><td class="n">' + won(R.total) + '</td></tr></tbody></table>' +
      (brought.length ? '<p><strong>가져온 증거</strong><br>' + esc(brought.join(', ')) + '</p>' : '') +
      '<p><strong>원하는 것</strong><br>밀린 임금' + (f.employed === 'yes' ? '을' : '과 퇴직금을') + ' 받고 싶습니다. 진정 절차를 안내해 주세요.</p>' +
      '<p class="foot">근로자가 입력한 내용으로 계산한 참고 자료입니다. 법률 자문이 아닙니다. 금액은 근로기준법 계산식으로만 계산했습니다(계산 규칙 버전 ' + esc(S.calc.engineVersion || '') + ').</p></article></div>' +
      cta('<div class="btn-row"><button type="button" class="btn btn-line" data-act="print">' + icon('print') + '<span>' + t('counsel.print') + '</span></button><button type="button" class="btn btn-primary" data-act="back">' + t('counsel.back') + '</button></div>');
  }

  // ---------- 화면: 관할 노동청 · 도움받을 곳 ----------
  // data/offices.json: moel.go.kr · 노동포털 관할관서 안내에서 확인한 것만 (출처 · 확인일은 파일 안). 없는 곳은 노동포털 찾기 링크
  var OFF = null;
  function loadOffices() {
    if (loadOffices.on) return;
    loadOffices.on = true;
    fetch('data/offices.json').then(function (r) { return r.json(); }).then(function (d) { OFF = d; }, function () { OFF = { regions: [], offices: [], help: [], failed: true }; })
      .then(function () { if (S.route === 'help' || S.route === 'counselor' || S.route === 'guide') rerender(); });
  }
  function addrActions(address) {
    var q = encodeURIComponent(address.split('(')[0].trim());
    return '<div class="addr-actions"><button type="button" class="small-btn" data-act="addr-copy" data-v="' + esc(address) + '">' + icon('copy') + '<span>' + t('help.copyAddr') + '</span></button>' +
      '<a class="small-btn" href="https://map.naver.com/p/search/' + q + '" target="_blank" rel="noopener">' + icon('map') + '<span>' + t('help.naver') + '</span></a>' +
      '<a class="small-btn" href="https://map.kakao.com/link/search/' + q + '" target="_blank" rel="noopener">' + icon('map') + '<span>' + t('help.kakao') + '</span></a></div>' +
      '<p class="hint">' + t('help.mapHint') + '</p>';
  }
  function copyAddr(text) {
    var fail = function () { toastT('help.copyFail'); };
    try {
      navigator.clipboard.writeText(text).then(function () { toastT('help.copied'); }, fail);
    } catch (e) { fail(); }
  }
  function officeOf() {
    officeFromAddr();
    if (!OFF || !S.office.sido) return null;
    var r = OFF.regions.filter(function (x) { return x.sido === S.office.sido; })[0];
    if (!r) return null;
    var id = r.office || ((r.sigungu.filter(function (g) { return g.name === S.office.sgg; })[0] || {}).office);
    return OFF.offices.filter(function (o) { return o.id === id; })[0] || null;
  }
  function vHelp() {
    if (!OFF) {
      loadOffices();
      return '<div class="col stack-lg"><h1 tabindex="-1">' + t('help.title') + '</h1>' + skel(t('help.loading'), ['tall', 'tall', '', 'short']) + '</div>';
    }
    officeFromAddr();
    var o = S.office, r = OFF.regions.filter(function (x) { return x.sido === o.sido; })[0];
    var sidos = '<option value="">' + t('q.pick') + '</option>' + OFF.regions.map(function (x) { return '<option' + (x.sido === o.sido ? ' selected' : '') + '>' + esc(x.sido) + '</option>'; }).join('');
    var sgg = r && r.sigungu.length ? '<div class="field"><label for="sgg">' + t('help.sgg') + '</label><select id="sgg" class="input" data-act="sgg" lang="ko"><option value="">' + t('q.pick') + '</option>' +
      r.sigungu.map(function (g) { return '<option' + (g.name === o.sgg ? ' selected' : '') + '>' + esc(g.name) + '</option>'; }).join('') + '</select></div>' : '';
    var off = officeOf();
    var card = off ? '<section class="office stack" aria-live="polite"><p class="hint">' + t('help.yours') + '</p><h2 lang="ko">' + esc(off.name) + '</h2>' +
      '<p lang="ko">' + icon('pin') + ' ' + esc(off.address) + '</p>' + addrActions(off.address) +
      (off.phone ? '<a class="phone" href="tel:' + off.phone.replace(/[^0-9]/g, '') + '">' + icon('phone') + '<span><strong>' + t('help.call') + '</strong>' + (off.phoneNote ? '<small lang="ko">' + esc(off.phoneNote) + '</small>' : '') + '</span><b>' + esc(off.phone) + '</b></a>' : '<p class="hint">' + t('help.noPhone') + '</p>') +
      '<p class="hint">' + t('help.src', { d: esc(OFF.checkedAt || '') }) + ' <a href="' + esc(off.source) + '" target="_blank" rel="noopener">' + t('help.srcLink') + icon('out') + '</a></p></section>'
      : (o.sido && (!r || r.sigungu.length) && o.sgg ? '<p class="warnbox">' + t('help.notFound') + '</p>' : '');
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('help.title') + '</h1><p class="lead">' + t('help.lead') + ' ' + term('laborOffice') + '</p></div>' +
      '<div class="stack"><div class="field"><label for="sido">' + t('help.sido') + '</label><select id="sido" class="input" data-act="sido" lang="ko">' + sidos + '</select></div>' + sgg +
      (o.auto ? '<p class="note">' + icon('check') + ' ' + t('help.fromAddr') + '</p>' : '') +
      '<p class="hint">' + t('help.oldName') + '</p></div>' + card +
      '<div class="nav-list">' + navRow('href="' + esc(OFF.complaintUrl || 'https://labor.moel.go.kr') + '" target="_blank" rel="noopener"', t('guide.link1')) + navRow('href="' + esc(OFF.findUrl || 'https://labor.moel.go.kr') + '" target="_blank" rel="noopener"', t('help.find')) + '</div>' +
      '<section class="stack"><h2>' + t('help.phones') + '</h2>' + helpPhones('grid') + '<p class="hint">' + t('guide.phoneHint') + '</p></section></div>' +
      '<aside class="side">' +
      cta(primary(t('guide.callout'), 'data-go="counselor"', !!S.calc) + (S.calc ? '' : '<p class="hint">' + t('help.needCalc') + '</p>'), ' ') + '</aside></div>';
  }

  // ---------- 화면: 준비물 체크리스트 ----------
  var READY = ['contract', 'payslip', 'bank', 'time', 'msg', 'id'];
  function vReady() {
    var got = READY.filter(function (k) { return S.ready[k]; }).length;
    var list = READY.map(function (k) {
      return '<li><label class="check ready-item" for="rd-' + k + '"><input id="rd-' + k + '" type="checkbox" data-act="ready" data-v="' + k + '"' + (S.ready[k] ? ' checked' : '') + '><span><strong>' + t('ready.' + k) + '</strong><small>' + t('ready.' + k + '.h') + '</small></span></label></li>';
    }).join('');
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('title.ready') + '</h1><p class="lead">' + t('ready.lead') + '</p></div>' +
      '<div class="progress"><div class="bar" style="--n:' + READY.length + '" aria-hidden="true">' + READY.map(function (_, i) { return '<span' + (i < got ? ' class="on"' : '') + '></span>'; }).join('') + '</div><span class="hint" role="status">' + t('form.ready', { n: got, m: READY.length }) + '</span></div>' +
      '<ul class="plain ready">' + list + '</ul></div>' +
      '<aside class="side"><p class="note">' + t('ready.note') + '</p>' +
      cta(primary(t(S.calc ? 'ready.toForm' : 'ready.toStart'), S.calc ? 'data-go="form"' : 'data-go="review"'), ' ') + '</aside></div>';
  }

  // ---------- 화면: 대지급금 알아보기 (금액 판정은 하지 않는다) ----------
  // 출처 (2026-11-15 확인): 근로복지공단 간이대지급금 https://welfare.comwel.or.kr/default/page.do?mCode=H030010000 ,
  // 도산대지급금 https://welfare.comwel.or.kr/default/page.do?mCode=H020010000 , 임금채권보장법 제7조 · 제7조의2 (law.go.kr). 자세한 정리: _도구/조사_3단계.md
  var DJ = [['left', ['yes', 'no']], ['paper', ['yes', 'filing', 'no']], ['year', ['yes', 'no', 'unsure']], ['closed', ['yes', 'no', 'unsure']]];
  function vDaeji() {
    var a = S.dj;
    var qs = DJ.map(function (q, i) {
      return '<fieldset class="stack"><legend class="label">' + (i + 1) + '. ' + t('dj.' + q[0] + (q[0] === 'year' && a.left === 'no' ? 'Now' : '')) + '</legend><div class="choices">' + q[1].map(function (v) {
        return '<label class="choice"><input type="radio" name="dj-' + q[0] + '" value="' + v + '" data-act="dj" data-k="' + q[0] + '"' + (a[q[0]] === v ? ' checked' : '') + '><span>' + t('dj.a.' + v) + '</span></label>';
      }).join('') + '</div></fieldset>';
    }).join('');
    var done = DJ.every(function (q) { return a[q[0]]; });
    var out = [];
    if (done) {
      if (a.paper === 'yes') out.push(['ok', t('dj.r.simple')]);
      else out.push(['step', t(a.paper === 'filing' ? 'dj.r.filing' : 'dj.r.first')]);
      if (a.left === 'no') out.push(['info', t('dj.r.employed')]);
      if (a.year !== 'yes') out.push(['warn', t('dj.r.year')]);
      if (a.closed === 'yes' && a.left === 'yes') out.push(['ok', t('dj.r.closed')]);
      if (a.closed === 'yes' && a.left === 'no') out.push(['info', t('dj.r.closedEmployed')]);
      out.push(['info', t('dj.r.company')]);
    }
    var res = done ? '<section class="dj-result stack" aria-live="polite"><h2>' + t('dj.result') + '</h2><ul class="plain stack">' + out.map(function (o) { return '<li class="dj-' + o[0] + '">' + o[1] + '</li>'; }).join('') + '</ul>' +
      '<div class="nav-list">' + navRow('href="https://welfare.comwel.or.kr/default/page.do?mCode=H030010000" target="_blank" rel="noopener"', t('dj.link1')) + navRow('href="https://welfare.comwel.or.kr/default/page.do?mCode=H020010000" target="_blank" rel="noopener"', t('dj.link2')) + '</div>' +
      '<a class="phone" href="tel:15880075">' + icon('phone') + '<span><strong>' + t('dj.call') + '</strong><small>' + t('dj.callSub') + '</small></span><b>1588-0075</b></a></section>' : '';
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('dj.title') + '</h1><p class="lead">' + t('dj.lead') + ' ' + term('daeji') + '</p></div>' +
      (a.fromF ? '<p class="note">' + icon('check') + ' ' + t('dj.fromF') + '</p>' : '') + qs + res + '</div>' +
      '<aside class="side"><p class="note">' + t('dj.note') + '</p>' +
      cta(primary(t('guide.cta'), 'data-go="form"', !!S.calc), ' ') + '</aside></div>';
  }

  // ---------- 화면: 근로계약서 확인하기 (판단은 서버 POST /api/v1/contract-checks, 사진은 서류 인식 CONTRACT 재사용) ----------
  var CT_ITEMS = ['WAGE', 'HOURS', 'HOLIDAY', 'ANNUAL_LEAVE', 'PLACE', 'JOB'];
  // 앞 질문에서 받은 급여 · 시간 · 사업장 크기로 빈 칸만 채운다 (계약서 사진으로 읽은 값 · 직접 고른 값은 그대로)
  function ctFromF() {
    var c = S.ct, f = S.f, h = hours(), cnt = f.days.filter(Boolean).length, got = false;
    function put(k, v) { if (!c[k] && v) { c[k] = v; got = true; } }
    put('type', { hour: 'HOURLY', day: 'DAILY', month: 'MONTHLY' }[f.wageType]);
    if (c.type === { hour: 'HOURLY', day: 'DAILY', month: 'MONTHLY' }[f.wageType]) put('wage', f.wage);
    if (h > 0 && cnt) put('weekly', String(h * cnt));
    if (h > 0 && c.type === 'DAILY') put('daily', String(h));
    put('size', { five: 'FIVE_OR_MORE', under: 'UNDER_FIVE' }[f.size]);
    if (got) c.fromF = true;
  }
  function vContract() {
    var c = S.ct;
    var items = CT_ITEMS.map(function (k) {
      return '<label class="check" for="ct-' + k + '"><input id="ct-' + k + '" type="checkbox" data-act="ct-w" value="' + k + '"' + (c.written[k] ? ' checked' : '') + '><span>' + t('ct.w.' + k) + '</span></label>';
    }).join('');
    function radio(name, list) {
      return '<div class="choices">' + list.map(function (v) {
        return '<label class="choice"><input type="radio" name="ct-' + name + '" value="' + v + '" data-act="ct" data-k="' + name + '"' + (c[name] === v ? ' checked' : '') + '><span>' + t('ct.' + name + '.' + v) + '</span></label>';
      }).join('') + '</div>';
    }
    function num(id, label, unit) {
      return '<div class="field"><label for="ct-' + id + '">' + label + ' <span class="opt">(' + unit + ')</span></label><input id="ct-' + id + '" class="input" type="text" inputmode="numeric" autocomplete="off" data-act="ct-num" data-k="' + id + '" value="' + esc(c[id]) + '"></div>';
    }
    var r = c.res, res = '';
    if (r) {
      var lastLaw = '', missW = [], missLaw = ''; // 같은 근거가 이어지면 첫 줄에만 적는다
      var rows = r.findings.slice().sort(function (a, b) { return (b.level === 'PROBLEM') - (a.level === 'PROBLEM'); }).map(function (f) { // 문제(빨강) 먼저, 빠짐(노랑) 다음
        var law = f.basis.law + ' ' + f.basis.article, lawTag = law === lastLaw ? '' : ' <small lang="ko">' + esc(law) + '</small>';
        lastLaw = law;
        var bad = f.level === 'PROBLEM';
        if (!bad) { missW.push(t('ct.w.' + f.code)); if (!missLaw) missLaw = law; return ''; }
        return '<li class="' + (bad ? 'dj-bad' : 'dj-warn') + '">' + icon(bad ? 'x' : 'info') + '<span>' + (bad ? '<strong>' + t('ct.bad') + '</strong> ' : '') + // 빠진 줄은 문장이 이미 '없어요' 라서 꼬리표를 붙이지 않는다
          (f.code === 'MIN_WAGE' ? t('ct.r.MIN_WAGE', { h: Number(r.hourlyWage).toLocaleString('ko-KR'), y: r.minimumWageYear, m: Number(r.minimumWage).toLocaleString('ko-KR') }) : f.code === 'OVER_52' ? t('ct.r.OVER_52') : t('ct.r.miss', { w: t('ct.w.' + f.code) })) +
          lawTag + '</span></li>';
      }).concat(missW.length ? ['<li class="dj-warn">' + icon('info') + '<span>' + t('ct.r.missAll', { w: missW.map(function (w) { return "'" + w + "'"; }).join(', ') }) + ' <small lang="ko">' + esc(missLaw) + '</small></span></li>'] : []).concat(r.ok.map(function (k) { return '<li class="dj-ok">' + icon('check') + '<span>' + t('ct.r.ok', { w: t('ct.w.' + k) }) + '</span></li>'; })).join('');
      var skip = r.notChecked.length ? '<p class="hint" lang="ko">' + r.notChecked.map(esc).join('<br>') + '</p>' : '';
      res = '<section class="dj-result stack" aria-live="polite" id="ct-res"><h2 tabindex="-1">' + t(r.findings.some(function (f) { return f.level === 'PROBLEM'; }) ? 'ct.resBad' : r.findings.length ? 'ct.resMiss' : 'ct.resOk') + '</h2>' +
        '<ul class="plain stack ct-list">' + rows + '</ul>' + (r.hourlyWage ? '<p class="hint">' + t('ct.hourly', { f: esc(r.hourlyFormula), h: Number(r.hourlyWage).toLocaleString('ko-KR') }) + '</p>' : '') + skip +
        '<p class="hint">' + t('ct.next') + '</p><div class="nav-list">' + navRow('href="https://www.law.go.kr/법령/근로기준법/제17조" target="_blank" rel="noopener"', t('ct.law')) + '</div>' +
        '<a class="phone" href="tel:1350">' + icon('phone') + '<span><strong>' + t('ct.call') + '</strong><small>' + t('ct.callSub') + '</small></span><b>1350</b></a></section>';
    }
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('ct.title') + '</h1><p class="lead">' + t('ct.lead') + '</p></div>' +
      '<section class="stack"><label class="btn btn-line" for="ct-cam">' + icon(touch.matches ? 'camera' : 'file') + '<span>' + t(c.busy ? 'ct.reading' : touch.matches ? 'ct.photo' : 'ct.file') + '</span></label>' +
      '<input id="ct-cam" class="sr" type="file" accept="image/*,application/pdf" data-act="ct-file"' + (c.busy ? ' disabled' : '') + '>' +
      '<p class="hint" role="status">' + (c.read ? t('ct.readDone') : t('ct.photoHint')) + '</p>' + (c.fromF && !c.read ? '<p class="note">' + icon('check') + ' ' + t('ct.fromF') + '</p>' : '') + '</section>' +
      '<fieldset class="stack"><legend class="label">1. ' + t('ct.q1') + '</legend><p class="hint">' + t('ct.q1h') + '</p>' + items + '</fieldset>' +
      '<fieldset class="stack"><legend class="label">2. ' + t('ct.q2') + '</legend>' + radio('type', ['MONTHLY', 'HOURLY', 'DAILY']) +
      (c.type ? num('wage', t('ct.wage.' + c.type), t('unit')) : '') + (c.type === 'DAILY' ? num('daily', t('ct.daily'), t('ct.hourUnit')) : '') + '</fieldset>' +
      '<fieldset class="stack"><legend class="label">3. ' + t('ct.q3') + '</legend>' + num('weekly', t('ct.weekly'), t('ct.hourUnit')) + '</fieldset>' +
      '<fieldset class="stack"><legend class="label">4. ' + t('ct.q4') + '</legend>' + radio('size', ['FIVE_OR_MORE', 'UNDER_FIVE']) + '</fieldset>' +
      res + '</div>' +
      '<aside class="side"><p class="note">' + t('ct.note') + '</p>' + cta(primary(t('ct.btn'), 'data-act="ct-check"', !c.busy), ' ') + '</aside></div>';
  }
  function ctCheck() {
    var c = S.ct, w = CT_ITEMS.filter(function (k) { return c.written[k]; });
    var n = function (v) { return v === '' ? null : Number(v); };
    c.busy = true; render();
    api('/api/v1/contract-checks', { wageType: c.type || null, wage: c.type ? n(c.wage) : null, weeklyHours: n(c.weekly), dailyHours: n(c.daily), size: c.size || null, written: w }).then(function (res) {
      c.res = res; c.busy = false; render(); focusId('ct-res');
    }, function (e) { c.busy = false; render(); toast(e.message); });
  }
  // 계약서 사진 → 서류 인식(CONTRACT)에서 임금 · 시간만 채운다. 읽은 칸은 '적혀 있음'으로 표시
  function ctPhoto(file) {
    var c = S.ct, form = new FormData();
    if (file.size > MAX_SIZE) { toast(t('err.413')); return; }
    form.append('file', file, /pdf$/i.test(file.type) ? 'contract.pdf' : /png$/i.test(file.type) ? 'contract.png' : 'contract.jpg');
    form.append('docType', 'CONTRACT');
    c.busy = true; render();
    api('/api/v1/documents/extract', form).then(function (res) {
      var v = {};
      res.fields.forEach(function (f) { v[f.key] = f.value; fillP(f.key, f.value); });
      if (v.wageType) c.type = v.wageType;
      if (v.baseWage) c.wage = String(v.baseWage);
      if (v.weeklyContractHours) c.weekly = String(v.weeklyContractHours);
      if (v.dailyContractHours) c.daily = String(v.dailyContractHours);
      if (v.employeeCount) c.size = Number(v.employeeCount) < 5 ? 'UNDER_FIVE' : 'FIVE_OR_MORE';
      if (v.baseWage) c.written.WAGE = true;
      if (v.weeklyContractHours || v.dailyContractHours) c.written.HOURS = true;
      if (S.ready.contract === undefined) S.ready.contract = true;
      c.read = true; c.busy = false; c.res = null; render();
    }, function (e) { c.busy = false; render(); toast(e.message); });
  }

  // ---------- 화면: 놓치면 안 되는 기한 (data/deadlines.json, 날짜 계산만. 금액 없음) ----------
  var DEADLINES = null, DL_DAY = '';
  function loadDeadlines() {
    if (DEADLINES) return;
    DEADLINES = [];
    fetch('data/deadlines.json').then(function (r) { return r.ok ? r.json() : {}; }).then(function (d) {
      DEADLINES = d.deadlines || [];
      DL_DAY = d.checkedAt || '';
      if (S.route === 'deadline') render();
    }, function () {});
  }
  function addTo(ymd, p) {
    var d = new Date(ymd + 'T00:00:00');
    d.setFullYear(d.getFullYear() + (p.years || 0), d.getMonth() + (p.months || 0), d.getDate() + (p.days || 0));
    return d;
  }
  function ymdOf(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function today() { return ymdOf(new Date()); }
  function vDeadline() {
    loadDeadlines();
    var dl = S.dl, today = new Date(ymdOf(new Date()) + 'T00:00:00');
    var inputs = ['end', 'fired', 'unpaid'].map(function (k) {
      return '<div class="field"><label for="dl-' + k + '">' + t('dl.in.' + k) + '</label><p class="hint">' + t('dl.in.' + k + '.h') + '</p><input id="dl-' + k + '" class="input" type="date" data-act="dl" data-k="' + k + '" value="' + esc(dl[k]) + '"></div>';
    }).join('');
    var rows = DEADLINES.filter(function (x) { return dl[x.from]; }).map(function (x) {
      var due = addTo(dl[x.from], x.plus), left = Math.round((due - today) / 864e5);
      var cls = left < 0 ? 'dj-bad' : left <= 30 ? 'dj-warn' : 'dj-step';
      var src = (x.sources || []).map(function (s) { return '<a href="' + esc(s.url) + '" target="_blank" rel="noopener" lang="ko">' + esc(s.name) + '</a>'; }).join(', ');
      return '<li class="' + cls + '">' + icon(left < 0 ? 'x' : 'clock') + '<span><strong>' + esc(pick(x.title)) + '</strong><br>' +
        t('dl.due', { d: krOrLocal(ymdOf(due)) }) + ', <b>' + (left < 0 ? t('dl.past', { n: -left }) : left === 0 ? t('dl.today') : t('dl.left', { n: left })) + '</b>' +
        '<br>' + esc(pick(x.what)) + '<br><small>' + src + '</small></span></li>';
    }).join('');
    return '<div class="split">' +
      '<div class="stack-lg">' + langBanner() +
      '<div class="stack"><h1 tabindex="-1">' + t('dl.title') + '</h1><p class="lead">' + t('dl.lead') + '</p></div>' +
      (dl.fromF ? '<p class="note">' + icon('check') + ' ' + t('dl.fromF') + '</p>' : '') + inputs +
      (rows ? '<section class="dj-result stack" aria-live="polite"><h2>' + t('dl.result') + '</h2><ul class="plain stack ct-list">' + rows + '</ul><p class="hint">' + t('dl.count', { d: DL_DAY }) + '</p></section>' : '<p class="hint">' + t('dl.empty') + '</p>') + '</div>' +
      '<aside class="side"><p class="note">' + t('dl.note') + '</p><a class="phone" href="tel:1350">' + icon('phone') + '<span><strong>' + t('ct.call') + '</strong><small>' + t('dl.callSub') + '</small></span><b>1350</b></a>' +
      '<div class="nav-list">' + navRow('data-go="after"', t('title.after')) + '</div></aside></div>';
  }
  // 시작 · 결과 화면의 '더 확인할 수 있는 것' 세 줄
  function extraRows() {
    return navRow('data-go="contract"', t('title.contract'), t('ct.sub')) + navRow('data-go="deadline"', t('title.deadline'), t('dl.sub')) + navRow('data-go="after"', t('title.after'), t('after.sub'));
  }

  // ---------- 화면: 쉬운 말 사전 ----------
  function vWords() {
    var list = WORDS.map(function (id) {
      return '<li><details class="word"><summary>' + t('w.' + id) + (S.lang !== 'ko' ? ' <small lang="ko">' + esc(L.ko['w.' + id]) + '</small>' : '') + '</summary><p>' + t('w.' + id + '.d') + '</p>' +
        (L.ko['w.' + id + '.law'] ? '<p class="hint" lang="ko">' + esc(L.ko['w.' + id + '.law']) + '</p>' : '') + '</details></li>';
    }).join('');
    return '<div class="col stack-lg">' + langBanner() + '<div class="stack"><h1 tabindex="-1">' + t('words.title') + '</h1><p class="lead">' + t('words.lead') + '</p></div>' +
      '<ul class="plain words">' + list + '</ul><p class="hint">' + t('words.src') + '</p></div>' + (hasTabs() ? '' : cta(primary(t('counsel.back'), 'data-act="back"')));
  }
  // 말 하나 풀이 시트 (화면 곳곳의 밑줄 단추)
  function showWord(id) {
    openSheet(t('w.' + id) + (S.lang !== 'ko' ? ' <small lang="ko">' + esc(L.ko['w.' + id]) + '</small>' : ''), '<p>' + t('w.' + id + '.d') + '</p>' +
      (L.ko['w.' + id + '.law'] ? '<p class="hint" lang="ko">' + esc(L.ko['w.' + id + '.law']) + '</p>' : '') +
      navRow('data-go="words"', t('words.all')), true);
  }

  // 화면에 들어갈 때 앞에서 받은 값으로 빈 칸 채우기 (go 에서 한 번)
  var PREFILL = {
    contract: ctFromF,
    // 기한: 질문 3 의 마지막으로 일한 날, 해고 상황이면 해고된 날도 같은 날로
    deadline: function () {
      var dl = S.dl, f = S.f;
      if (f.employed !== 'no' || !f.end) return;
      if (!dl.end) { dl.end = f.end; dl.fromF = true; }
      if (!dl.fired && has('fired')) { dl.fired = f.end; dl.fromF = true; }
    },
    // 대지급금 1번 「그만두었나요」는 질문 3 답으로
    daeji: function () {
      if (!S.dj.left && S.f.employed) { S.dj.left = S.f.employed === 'yes' ? 'no' : 'yes'; S.dj.fromF = true; }
    }
  };
  // 도움받을 곳 전화 목록 (절차 · 노동청 화면 공통, data/offices.json 의 help). 못 불러왔으면 고용노동부 1350 하나
  function helpPhones(cls) {
    loadOffices();
    var list = OFF && OFF.help && OFF.help.length ? OFF.help : [{ id: 'moel', name: t('guide.p1'), what: t('guide.p1s'), phone: '1350' }];
    return '<ul class="plain phones' + (cls ? ' ' + cls : '') + '">' + list.map(function (h) {
      var what = (L[S.lang] && L[S.lang]['helpw.' + h.id]) || h.what;
      return '<li><a class="phone" href="tel:' + h.phone.replace(/[^0-9]/g, '') + '">' + icon('phone') + '<span><strong' + (S.lang === 'ko' || (L[S.lang] || {})['helpn.' + h.id] ? '' : ' lang="ko"') + '>' + esc((L[S.lang] && L[S.lang]['helpn.' + h.id]) || h.name) + '</strong><small>' + esc(what) + '</small></span><b>' + esc(h.phone) + '</b></a></li>';
    }).join('') + '</ul>';
  }
  // 관할 노동청: 진정서 칸의 사업장 주소 첫 낱말(시 · 도)과 시군구 이름으로 고른다. 못 찾으면 그대로 둔다 (사업장 주소가 바뀌면 다시 해 본다)
  var SIDO_ABBR = { '서울': '서울특별시', '경기': '경기도', '인천': '인천광역시', '강원': '강원특별자치도', '부산': '부산광역시', '울산': '울산광역시', '경남': '경상남도', '경상남도': '경상남도',
    '대구': '대구광역시', '경북': '경상북도', '경상북도': '경상북도', '광주': '전남광주통합특별시', '전남': '전남광주통합특별시', '전라남도': '전남광주통합특별시', '전북': '전북특별자치도', '전라북도': '전북특별자치도',
    '충북': '충청북도', '충청북도': '충청북도', '충남': '충청남도', '충청남도': '충청남도', '대전': '대전광역시', '세종': '세종특별자치시', '제주': '제주특별자치도' };
  // 해 본 주소는 localStorage 에 남기지 않게 이 페이지 메모리에만
  var officeTried = '';
  function officeFromAddr() {
    var addr = (S.p.waddr || '').trim(), o = S.office;
    if (!OFF || !addr || o.sido || officeTried === addr) return;
    officeTried = addr;
    var first = addr.split(/\s+/)[0], sido = SIDO_ABBR[first] || SIDO_ABBR[first.slice(0, 2)] || first;
    var r = OFF.regions.filter(function (x) { return x.sido === sido; })[0];
    if (!r) return;
    var g = r.sigungu.filter(function (x) { return addr.indexOf(' ' + x.name) > 0; }).sort(function (a, b) { return b.name.length - a.name.length; })[0];
    S.office = { sido: r.sido, sgg: g ? g.name : '', auto: !!g };
  }

  var VIEWS = { start: vStart, sit: vSit, upload: vUpload, q1: vQ1, q2: vQ2, q3: vQ3, q4: vQ4, q5: vQ5, q6: vQ6, qx: vQx, info: vInfo, review: vReview, result: vResult, form: vForm, complaint: vComplaint, guide: vGuide, counselor: vCounselor, cal: vCal, help: vHelp, ready: vReady, daeji: vDaeji, words: vWords, contract: vContract, deadline: vDeadline, letter: vLetter, after: function () { return vInfo(true); } };

  // transition: 화면을 옮길 때 전환 방향('from-r' · 'from-l' · 'fade'). 있으면 맨 위로 올리고 h1 에 포커스. 휴대폰은 주 버튼 · 탭 막대를 아래 고정 영역(dock)으로 옮겨
  // 본문만 스크롤되게 한다. 넓은 화면은 버튼이 옆 칸에 그대로 있다
  var dockH = -1;
  function render(transition) {
    document.documentElement.classList.toggle('big', !!S.big);
    // 쉬운 말: 휴대폰에서는 덜 중요한 설명을 접어 화면을 단순하게 (css .easy-m)
    document.documentElement.classList.toggle('easy-m', !!S.easy && S.lang === 'ko');
    document.documentElement.lang = S.lang;
    document.title = t('brand');
    app.innerHTML = topbar() + '<main id="main" class="wrap"><div' + (transition ? ' class="enter ' + transition + '"' : '') + '>' + VIEWS[S.route]() + '</div></main>' +
      '<div class="dock no-print">' + (hasTabs() ? tabs('tabbar') : '') + '</div>';
    var dock = app.querySelector('.dock'), c = app.querySelector('main .cta:not(.start-cta)'); // 시작 화면은 단추를 제목 바로 아래에 둔다
    if (mobile.matches && c) dock.insertBefore(c, dock.firstChild);
    var h0 = mobile.matches ? dock.offsetHeight : 0;
    if (h0 !== dockH) { dockH = h0; document.documentElement.style.setProperty('--dock-h', h0 + 'px'); }
    if (transition) {
      window.scrollTo(0, 0);
      var h = app.querySelector('h1');
      if (h) h.focus({ preventScroll: true });
    }
    countUp();
    save();
  }
  mobile.addEventListener('change', function () { render(); });
  touch.addEventListener('change', function () { render(); });
  // 키보드가 올라오면 아래 고정 단추를 키보드 위로 올린다 (아이폰은 키보드가 화면을 덮기만 해서 단추가 가려진다)
  var vv = window.visualViewport;
  if (vv) {
    var onKeyboard = function () {
      var gap = Math.round(window.innerHeight - vv.height - vv.offsetTop);
      document.documentElement.style.setProperty('--kb', (gap > 80 ? gap : 0) + 'px');
      document.documentElement.classList.toggle('kb', vv.height < 520);
    };
    vv.addEventListener('resize', onKeyboard);
    vv.addEventListener('scroll', onKeyboard);
  }
  // 합계는 계산 결과마다 한 번 0 에서 올라가며 나타난다 (움직임 줄이기면 바로 숫자). 보여 주기만, 값은 서버 그대로
  var counted = '';
  function countUp() {
    var b = app.querySelector('[data-count]');
    if (!b || counted === S.calc.id) return;
    counted = S.calc.id;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    var to = Number(b.dataset.count), t0 = 0;
    function step(ts) {
      t0 = t0 || ts;
      var k = Math.min(1, (ts - t0) / 700);
      b.textContent = Math.round(to * (1 - Math.pow(1 - k, 3))).toLocaleString('ko-KR');
      if (k < 1) window.requestAnimationFrame(step);
    }
    window.requestAnimationFrame(step);
  }

  // ---------- 서류 사진 ----------
  // 사진 · 미리보기는 이 페이지 메모리에만 둔다 (sessionStorage 에도 저장하지 않음)
  // state: prep(다듬는 중) · ready · reading · ok · fail(못 읽음) · bad(올릴 수 없는 파일)
  var MAX_PAGES = 6;
  var LONG_SIDE = 2000;
  // 바로 검사 기준: 선명도 · 밝기 평균(0~255) · 원래 긴 변(px)
  var BLUR_MIN = 60, DARK_MAX = 70, SMALL_MIN = 1000;
  var PAGES = [];
  var pageId = 1, retakeId = 0;

  function pageOf(id) { return PAGES.filter(function (p) { return p.id === Number(id); })[0]; }
  function addFiles(list) {
    Array.prototype.forEach.call(list, function (file) {
      if (PAGES.length >= MAX_PAGES) { toast(t('up.tooMany', { max: MAX_PAGES })); return; }
      var p = { id: pageId++, type: 'payslip' };
      PAGES.push(p);
      setFile(p, file);
    });
    if (S.up.status !== 'reading') S.up = { status: 'idle' };
    rerender();
  }
  function setFile(p, file) {
    var name = (file.name || '').toLowerCase();
    setUrl(p, null);
    p.file = file; p.rot = 0; p.warn = []; p.msg = '';
    p.pdf = /\.pdf$/.test(name) || file.type === 'application/pdf';
    if (/\.hwp$/.test(name)) bad(p, t('up.hwp'));
    else if (p.pdf) {
      if (file.size > MAX_SIZE) bad(p, t('err.413'));
      else { p.blob = file; p.state = 'ready'; }
    } else if (/^image\//.test(file.type) || /\.(jpe?g|png)$/.test(name)) prep(p);
    else bad(p, t('err.415'));
  }
  function bad(p, msg) { p.state = 'bad'; p.msg = msg; }
  function setUrl(p, blob) {
    if (p.url) URL.revokeObjectURL(p.url);
    p.blob = blob;
    p.url = blob ? URL.createObjectURL(blob) : '';
  }
  // 브라우저에서 다듬기: 회전(사진 정보는 브라우저가 반영, 돌리기 단추는 90도씩) · 긴 변 2000px · JPEG 로 10MB 아래
  function prep(p) {
    p.state = 'prep';
    var src = URL.createObjectURL(p.file);
    var im = new Image();
    im.onload = function () {
      URL.revokeObjectURL(src);
      var w = im.naturalWidth, h = im.naturalHeight, k = Math.min(1, LONG_SIDE / Math.max(w, h));
      var turn = p.rot % 180 !== 0;
      var c = document.createElement('canvas');
      c.width = Math.round((turn ? h : w) * k);
      c.height = Math.round((turn ? w : h) * k);
      var g = c.getContext('2d');
      g.translate(c.width / 2, c.height / 2);
      g.rotate(p.rot * Math.PI / 180);
      g.drawImage(im, -w * k / 2, -h * k / 2, w * k, h * k);
      p.warn = quality(c, Math.max(w, h));
      encode(c, 0.85, function (blob) {
        if (blob) { setUrl(p, blob); p.state = 'ready'; } else bad(p, t('err.413'));
        if (S.route === 'upload') rerender();
      });
    };
    im.onerror = function () {
      URL.revokeObjectURL(src);
      bad(p, t('err.415'));
      if (S.route === 'upload') rerender();
    };
    im.src = src;
  }
  function encode(c, q, done) {
    c.toBlob(function (b) {
      if (b && b.size > MAX_SIZE && q > 0.45) encode(c, q - 0.2, done);
      else done(b && b.size <= MAX_SIZE ? b : null);
    }, 'image/jpeg', q);
  }
  // 바로 검사: 선명도(라플라시안 분산) · 밝기 평균 · 원래 크기. 기준값은 시험 사진으로 맞춘 어림값
  function quality(c, origLong) {
    var k = Math.min(1, 800 / Math.max(c.width, c.height));
    var w = Math.round(c.width * k), h = Math.round(c.height * k);
    var s = document.createElement('canvas');
    s.width = w; s.height = h;
    var g = s.getContext('2d');
    g.drawImage(c, 0, 0, w, h);
    var d = g.getImageData(0, 0, w, h).data, gray = new Float32Array(w * h), sum = 0, i;
    for (i = 0; i < w * h; i++) { gray[i] = 0.299 * d[i * 4] + 0.587 * d[i * 4 + 1] + 0.114 * d[i * 4 + 2]; sum += gray[i]; }
    var n = 0, m = 0, m2 = 0;
    for (var y = 1; y < h - 1; y++) {
      for (var x = 1; x < w - 1; x++) {
        i = y * w + x;
        var v = gray[i - 1] + gray[i + 1] + gray[i - w] + gray[i + w] - 4 * gray[i];
        n++; m += v; m2 += v * v;
      }
    }
    var out = [];
    if (n && m2 / n - (m / n) * (m / n) < BLUR_MIN) out.push('blur');
    if (sum / (w * h) < DARK_MAX) out.push('dark');
    if (origLong < SMALL_MIN) out.push('small');
    return out;
  }
  // 모두 한 번에 보내고, 장별 결과를 카드에 · 합친 값을 확인 화면에
  function readAll() {
    var form = new FormData();
    PAGES.forEach(function (p, i) {
      form.append('files', p.blob, 'page' + (i + 1) + (p.pdf ? '.pdf' : '.jpg'));
      form.append('docTypes', p.type.toUpperCase());
      p.state = 'reading';
    });
    S.up = { status: 'reading', n: PAGES.length };
    render();
    var sent = PAGES.slice();
    api('/api/v1/documents/extract-batch', form).then(function (res) {
      res.pages.forEach(function (r) {
        var p = sent[r.page - 1]; p.state = r.ok ? 'ok' : 'fail'; p.n = r.fields.length;
        // 읽은 서류는 준비물 목록에 이미 있는 것으로 (사용자가 직접 끈 것은 그대로)
        var rk = r.ok && { PAYSLIP: 'payslip', CONTRACT: 'contract' }[r.docType];
        if (rk && S.ready[rk] === undefined) S.ready[rk] = true;
      });
      applyExtract(res);
      S.up.status = 'done';
      toast(t('up.toast'));
      if (S.route === 'upload') go('review'); else save();
    }, function (e) {
      sent.forEach(function (p, i) {
        if (e.field === 'files[' + i + ']') bad(p, e.message);
        else p.state = e.status === 422 ? 'fail' : 'ready';
      });
      S.up = { status: e.status === 422 ? 'fail' : 'error', msg: e.message };
      if (S.route === 'upload') render(); else save();
    });
  }
  // 다시 그려도 누르던 단추에 포커스를 둔다 (없어졌으면 목록 제목)
  function rerender() {
    var id = document.activeElement && document.activeElement.id;
    render();
    var el = (id && document.getElementById(id)) || document.getElementById('pages-title');
    if (el && id) el.focus({ preventScroll: true });
  }

  // ---------- 동작 ----------
  // POST /documents/extract-batch 응답 → 확인 화면 값. 0.8 미만 · 사진끼리 다른 값은 노란 줄, unreadable 은 빈칸
  // 다시 읽으면 앞에서 읽은 값에 덧붙인다
  function shown(v) {
    if (Array.isArray(v)) return won(v.reduce(function (s, a) { return s + Number(a.amount); }, 0));
    return typeof v === 'number' ? v.toLocaleString('ko-KR') : String(v);
  }
  // 서류에서 읽은 값 → 진정서 칸. 빈 칸에만 넣고 「서류에서 읽음」 표시(psrc). 사용자가 고치면 표시가 빠진다
  var P_OF = { workplaceAddress: 'waddr', workplacePhone: 'wphone', jobTitle: 'job', workerName: 'name', workerPhone: 'phone', workerAddress: 'addr', ownerName: 'owner', companyName: 'company' };
  function fillP(key, v) {
    var pk = P_OF[key];
    if (!pk || v == null || String(v).trim() === '' || S.p[pk]) return;
    S.p[pk] = String(v).trim();
    S.psrc = S.psrc || {};
    S.psrc[pk] = 'doc';
  }
  function applyExtract(res) {
    var f = S.f;
    var weekly = 0;
    res.fields.forEach(function (x) {
      var v = x.value, row = ROW_OF[x.key];
      // 진정서 칸은 진정서 화면에 미리 채운다 (이미 쓴 값은 안 덮음. 이름 · 연락처도 읽는 것은 정식 기능 — 대표 결정 11-21. 값은 sessionStorage 에만)
      fillP(x.key, v);
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
      else if (x.key === 'allowances') f.allowance = String(v.reduce(function (s, a) { return s + Number(a.amount); }, 0));
      else if (x.key === 'payMonth') f.month = v;
      else if (x.key === 'paidAmount') f.paid = String(v);
      else f[row] = String(v);
      S.src[row] = 'doc';
      S.from[row] = x.page || 0;
      S.low[row] = x.confidence < 0.8;
      S.ok[row] = false;
    });
    var cnt = f.days.filter(Boolean).length;
    if (!f.dayHours && weekly && cnt) f.dayHours = String(weekly / cnt);
    (res.unreadable || []).forEach(function (k) { if (ROW_OF[k]) S.unread[ROW_OF[k]] = true; });
    // 사진마다 값이 다르면 노란 줄 + 어느 사진에서 무엇을 읽었는지
    (res.conflicts || []).forEach(function (c) {
      var row = ROW_OF[c.key];
      if (!row) return;
      S.conf[row] = c.values.map(function (v) { return t('up.page', { n: v.page }) + ' ' + shown(v.value); }).join(', ');
      S.low[row] = true;
      S.ok[row] = false;
    });
    S.up.n = res.fields.length;
    S.up.unread = (res.unreadable || []).length;
    S.up.masked = res.maskedCount || 0;
  }
  function setField(el) {
    var key = el.dataset.f;
    var v = el.value;
    if (el.dataset.conv === 'time') {
      // 숫자만 남기고 세 자리부터 ':' 를 넣는다 (9 → 9시, 930 → 9:30, 0830 → 08:30, 2230 → 22:30)
      var dg = v.replace(/[^0-9]/g, '').slice(0, 4);
      var cut = dg.length === 4 || Number(dg.slice(0, 2)) <= 24 ? 2 : 1;
      var shown = dg.length > 2 ? dg.slice(0, cut) + ':' + dg.slice(cut) : dg;
      if (el.value !== shown && !(v.indexOf(':') > 0 && v.replace(':', '') === dg)) el.value = shown;
      v = hDec(dg.length > 2 ? shown : dg ? dg + ':00' : '');
    }
    else if (el.dataset.conv === 'min') {
      var m = v.replace(/[^0-9]/g, '').slice(0, 3);
      if (el.value !== m) el.value = m;
      v = m === '' ? '' : String(Math.round(Number(m) / 60 * 10000) / 10000);
    }
    else if (el.hasAttribute('data-live')) {
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
    var sum = app.querySelector('[data-q4sum]');
    if (sum) sum.innerHTML = q4Sum();
  }
  function doPrint() {
    if (!inFrame && typeof window.print === 'function') { window.print(); return; }
    toastT('print.proto');
  }
  function doCopy() {
    var text = S.route === 'letter' && S.lt ? letterText() : plainText();
    var fail = function () { S.copyFallback = true; render(); var b = document.getElementById('copybox'); if (b) { b.focus(); b.select(); } toastT('cp.copyFail'); };
    try {
      navigator.clipboard.writeText(text).then(function () { toastT('cp.copied'); }, fail);
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
      var first = app.querySelector('[data-miss]');
      if (first) first.focus();
      toast(t('form.missing', { n: missing.length }));
      return;
    }
    openSheet(t('confirm.title'), '<p>' + t('confirm.body') + '</p>' +
      '<ul class="hint confirm-list"><li>' + t('confirm.l1') + '</li><li>' + t('confirm.l2') + '</li></ul>' +
      primary(t('make.btn'), 'data-act="confirm-make"') +
      '<button type="button" class="btn btn-line" data-act="sheet-close">' + t('confirm.back') + '</button>');
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
  function noCompany() { return t('form.noCompany'); }
  // 상황 카드를 안 고르고 시작하면: 고르러 가기 · 그냥 시작
  function sitAsk(path) {
    openSheet(t('sit.askTitle'), '<p>' + t('sit.askBody') + '</p>' +
      '<button type="button" class="btn btn-primary" data-act="to-sit"><span>' + t('sit.askGo') + '</span></button>' +
      '<button type="button" class="btn btn-line" data-act="path" data-v="' + path + '" data-skip="1">' + t('sit.askSkip') + '</button>');
  }
  function disclaimSheet() {
    openSheet(t('start.noteTitle'), '<p>' + t('result.disclaimToast') + '</p>', true);
  }
  // 달력: 고른 날 기록 저장 (일한 시간이 0 이하면 저장하지 않음)
  function calSave() {
    var w = { s: document.getElementById('ce-s').value, e: document.getElementById('ce-e').value, b: Number(document.getElementById('ce-b').value), h: document.getElementById('ce-h').checked };
    if (!w.s || !w.e || workedMin(w) <= 0) { var er = document.getElementById('ce-err'); er.hidden = false; return; }
    var d = S.calSel;
    S.f.work[d] = w;
    S.src.work = 'self'; S.fieldErr.work = '';
    S.calSel = '';
    render();
    focusId('cd' + d);
  }
  // 평소 요일 · 시간(질문 4)으로 이 달의 빈 날을 채운다
  function calFill() {
    var f = S.f, ym = S.calM, n = new Date(Date.UTC(+ym.slice(0, 4), +ym.slice(5), 0)).getUTCDate(), added = 0;
    for (var d = 1; d <= n; d++) {
      var date = ym + '-' + pad(d), wd = (new Date(date + 'T00:00:00Z').getUTCDay() + 6) % 7;
      if (f.days[wd] && !f.work[date] && inPeriod(date)) {
        f.work[date] = { s: hh(f.tStart), e: hh(f.tEnd).replace('24:00', '00:00'), b: Number(f.brk || 0) * 60, h: false };
        added++;
      }
    }
    S.src.work = 'self';
    render();
    toast(t('cal.filled', { n: added }));
  }
  // 다시 그린 뒤 포커스만 돌려준다. 화면은 움직이지 않게 (preventScroll)
  function focusId(id) { var el = document.getElementById(id); if (el) el.focus({ preventScroll: true }); }

  document.addEventListener('click', function (e) {
    // 본문으로 건너뛰기: 주소(#main)를 바꾸면 화면 이동으로 읽혀 시작 화면으로 가 버리므로 초점만 옮긴다
    if (e.target.closest('.skip')) { e.preventDefault(); var m = document.getElementById('main'); if (m) { m.tabIndex = -1; m.focus(); } return; }
    var t = e.target.closest('[data-go],[data-act]');
    if (!t || t.disabled) return;
    if (t.dataset.go !== undefined && t.dataset.go !== '') { go(t.dataset.go); return; }
    var a = t.dataset.act;
    if (a === 'home') { e.preventDefault(); home(); }
    else if (a === 'back') back();
    else if (a === 'addr') findAddr(t.dataset.k);
    else if (a === 'place') findPlace();
    else if (a === 'pl-go') searchPlace();
    else if (a === 'pl-pick') pickPlace(Number(t.dataset.i));
    else if (a === 'big') { S.big = !S.big; render(); }
    // 더보기: 버튼 위쪽(합계 아래)에도 펼쳐지는 것이 있어 버튼이 밀린다 → 누른 버튼이 손가락 아래 그대로 있게 스크롤을 맞춘다
    else if (a === 'm-more') {
      var before = t.getBoundingClientRect().top;
      S.moreOpen = !S.moreOpen; rerender();
      var mb = document.getElementById('m-more-btn'); if (mb) window.scrollBy(0, mb.getBoundingClientRect().top - before);
    }
    else if (a === 'easy-tg') { S.easy = !S.easy; render(); focusId('easy-top'); }
    else if (a === 'lang') { var fromSheet = !!t.closest('dialog'); closeSheet(); setLang(t.dataset.v).then(function () { if (fromSheet) focusId('lang-top'); }); }
    else if (a === 'lang-sheet') langSheet();
    else if (a === 'sheet-close') closeSheet();
    else if (a === 'item') itemSheet(Number(t.dataset.i));
    else if (a === 'path') {
      if (!S.agreed) { toAgree(); toastT('start.agreeHint'); return; }
      // 상황 카드를 하나도 안 골랐으면 한 번 묻는다 (안 고르면 연차수당 · 해고예고수당 · 지연이자 같은 항목이 빠진다)
      if (!S.sit.length && !t.dataset.skip && S.route === 'sit') { sitAsk(t.dataset.v); return; }
      closeSheet();
      S.path = t.dataset.v;
      // 서류로 시작하거나 서류 없이 직접 입력하면 빈 칸부터
      if (S.path === 'doc' || S.up.status !== 'done') { S.f = blankFields(); S.src = {}; S.from = {}; S.conf = {}; S.low = {}; S.ok = {}; S.unread = {}; S.fieldErr = {}; }
      if (S.path === 'doc') { S.up = { status: 'idle' }; PAGES.forEach(function (p) { setUrl(p, null); }); PAGES = []; }
      // 시작 화면에서 그만둔 상황(퇴직금 · 해고 · 연차 · 14일)을 골랐으면 질문 3 「지금도 다니나요」를 미리 「그만뒀어요」로
      if (!S.f.employed && ['sev', 'fired', 'leave', 'over14'].some(has)) S.f.employed = 'no';
      resumeTo = '';
      go(S.path === 'manual' ? 'q1' : 'upload');
    }
    // 시작 화면의 두 단추: 서류로 · 직접 입력 중 하나를 기억하고 상황 고르기로
    else if (a === 'pick') { S.path = t.dataset.v; resumeTo = ''; go('sit'); }
    else if (a === 'resume') { var to = resumeTo; resumeTo = ''; go(to); }
    else if (a === 'restart') { var keep = { lang: S.lang, big: S.big }; S = Object.assign(fresh(), keep); resumeTo = ''; render(); toastT('resume.cleared'); }
    else if (a === 'wipe') wipeAsk();
    else if (a === 'wipe-yes') wipe();
    else if (a === 'term') showWord(t.dataset.v);
    else if (a === 'speak') speak(t.dataset.v);
    else if (a === 'day') { var i = Number(t.dataset.i); S.f.days[i] = !S.f.days[i]; S.src.days = 'self'; S.low.days = false; render(); var again = app.querySelector('[data-act="day"][data-i="' + i + '"]'); if (again) again.focus({ preventScroll: true }); }
    else if (a === 'month-add') { S.f.more.push({ month: '', paid: '' }); render(); focusId('mm' + (S.f.more.length - 1)); }
    // 그만둔 달이 며칠뿐일 때: 앞달을 못 받은 달로 더하고 그 달 받은 돈 칸으로
    else if (a === 'month-add-prev') { S.f.more.push({ month: t.dataset.v, paid: '' }); S.src.more = 'self'; render(); var rows = app.querySelectorAll('.opt-row-d'); if (rows[1]) rows[1].open = true; focusId('mp' + (S.f.more.length - 1)); }
    else if (a === 'month-del') { S.f.more.splice(Number(t.dataset.i), 1); render(); }
    else if (a === 'cal-day') { S.calSel = t.dataset.d; render(); var ce = document.getElementById('ce-title'); if (ce) { ce.focus({ preventScroll: true }); ce.closest('.cal-edit').scrollIntoView({ block: 'center', behavior: 'smooth' }); } }
    else if (a === 'cal-save') calSave();
    else if (a === 'cal-del') { var dd = S.calSel; delete S.f.work[dd]; S.calSel = ''; render(); focusId('cd' + dd); }
    else if (a === 'cal-close') { var dc = S.calSel; S.calSel = ''; render(); focusId('cd' + dc); }
    else if (a === 'cal-fill') calFill();
    else if (a === 'cal-clear') { Object.keys(S.f.work).forEach(function (d) { if (d.slice(0, 7) === S.calM) delete S.f.work[d]; }); S.calSel = ''; render(); }
    else if (a === 'ok') { S.ok[t.dataset.k] = true; render(); }
    else if (a === 'disclaim') disclaimSheet();
    else if (a === 'calculate') calculate(t);
    else if (a === 'read') readAll();
    else if (a === 'pg-rotate') { var rp = pageOf(t.dataset.id); rp.rot = (rp.rot + 90) % 360; prep(rp); rerender(); }
    else if (a === 'pg-retake') { retakeId = Number(t.dataset.id); document.getElementById('retake').click(); }
    else if (a === 'pg-remove') { var gone = pageOf(t.dataset.id); setUrl(gone, null); PAGES.splice(PAGES.indexOf(gone), 1); rerender(); }
    else if (a === 'print') doPrint();
    else if (a === 'copy') doCopy();
    else if (a === 'docx') saveDocx();
    else if (a === 'listen') listen();
    else if (a === 'to-agree') toAgree();
    // 결과의 '적지 않아 계산하지 않은 것' → 질문 6 의 연장 · 야간 · 휴일 칸을 펼친 채로
    else if (a === 'miss-go') { openOt = true; go('q6'); openOt = false; var ot = document.getElementById('ot'); if (ot) { ot.scrollIntoView({ block: 'center' }); ot.focus({ preventScroll: true }); } }
    else if (a === 'to-sit') { closeSheet(); var st = app.querySelector('.sit-title'); if (st) { st.scrollIntoView({ block: 'start', behavior: 'smooth' }); var first = document.getElementById('sit-' + SITS[0]); if (first) first.focus({ preventScroll: true }); } }
    else if (a === 'q-sheet') qSheet(Number(t.dataset.n));
    else if (a === 'addr-copy') copyAddr(t.dataset.v);
    else if (a === 'make') make();
    else if (a === 'confirm-make') makeDraft(t);
    else if (a === 'make-letter') makeLetter(t);
    else if (a === 'letter-reset') { S.lt = null; render(); focusId('lt-account'); }
    else if (a === 'tr') { S.tr = t.dataset.v === '1'; render(); }
    else if (a === 'facts-edit') { S.editing = true; render(); var ta = document.getElementById('facts-edit'); if (ta) ta.focus(); }
    else if (a === 'facts-cancel') { S.editing = false; render(); }
    else if (a === 'ct-check') ctCheck();
    else if (a === 'facts-save') { S.factsText = document.getElementById('facts-edit').value; S.editing = false; render(); toastT('cp.saved'); }
  });
  document.addEventListener('change', function (e) {
    var t = e.target;
    var a = t.dataset.act;
    if (a === 'sit') { S.sit = S.sit.filter(function (x) { return x !== t.value; }).concat(t.checked ? [t.value] : []); render(); focusId(t.id); }
    else if (a === 'agree') { S.agreed = t.checked; render(); var c = document.getElementById('agree'); if (c) c.focus({ preventScroll: true }); }
    else if (a === 'files') { addFiles(t.files); t.value = ''; }
    else if (a === 'retake-file') { var rp = pageOf(retakeId); if (rp && t.files[0]) { setFile(rp, t.files[0]); rerender(); } t.value = ''; }
    else if (a === 'pg-type') { var tp = pageOf(t.dataset.id); tp.type = t.value; if (tp.state === 'ok' || tp.state === 'fail') tp.state = 'ready'; rerender(); }
    else if (a === 'cal-month') { S.calM = t.value; S.calSel = ''; render(); focusId('cal-month'); }
    else if (a === 'sido') { S.office = { sido: t.value, sgg: '' }; render(); focusId('sido'); }
    else if (a === 'sgg') { S.office.sgg = t.value; S.office.auto = false; render(); focusId('sgg'); }
    else if (a === 'ready') { S.ready[t.dataset.v] = t.checked; render(); focusId(t.id); }
    else if (a === 'ct-w') { S.ct.written[t.value] = t.checked; S.ct.res = null; save(); }
    else if (a === 'ct') { S.ct[t.dataset.k] = t.value; S.ct.res = null; S.ct.fromF = false; render(); var cr = app.querySelector('[name="ct-' + t.dataset.k + '"][value="' + t.value + '"]'); if (cr) cr.focus({ preventScroll: true }); }
    else if (a === 'ct-file') { if (t.files[0]) ctPhoto(t.files[0]); t.value = ''; }
    else if (a === 'dl') { S.dl[t.dataset.k] = t.value; S.dl.fromF = false; render(); focusId(t.id); }
    else if (a === 'dj') { S.dj[t.dataset.k] = t.value; if (t.dataset.k === 'left') S.dj.fromF = false; render(); var r = app.querySelector('[name="dj-' + t.dataset.k + '"][value="' + t.value + '"]'); if (r) r.focus({ preventScroll: true }); }
    else if (t.dataset.m !== undefined && t.dataset.k === 'month') { S.f.more[Number(t.dataset.m)].month = t.value; S.src.more = 'self'; render(); focusId(t.id); }
    else if (t.dataset.f !== undefined && !t.hasAttribute('data-live')) {
      setField(t);
      var id = t.id, val = t.value;
      render();
      var back2 = id ? document.getElementById(id) : app.querySelector('[data-f="' + t.dataset.f + '"][value="' + val + '"]');
      if (back2) back2.focus({ preventScroll: true });
    }
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.id === 'pl-q' && !e.isComposing) { e.preventDefault(); searchPlace(); }
  });
  document.addEventListener('input', function (e) {
    var t = e.target;
    if (t.hasAttribute('data-live')) { setField(t); refreshNext(); }
    else if (t.dataset.m !== undefined && t.dataset.k === 'paid') {
      var v = t.value.replace(/[^0-9]/g, '').replace(/^0+(?=\d)/, '');
      if (t.value !== v) t.value = v;
      S.f.more[Number(t.dataset.m)].paid = v;
      S.src.more = 'self';
      var r = app.querySelector('[data-readable-m="' + t.dataset.m + '"]');
      if (r) r.textContent = readable(v);
      save();
      refreshNext();
    }
    else if (t.dataset.act === 'ct-num') { var cv = t.value.replace(/[^0-9.]/g, ''); if (t.value !== cv) t.value = cv; S.ct[t.dataset.k] = cv; S.ct.res = null; save(); }
    else if (t.dataset.lt !== undefined) { S.ltIn[t.dataset.lt] = t.value; save(); }
    else if (t.dataset.p !== undefined) {
      S.lt = null; S.p[t.dataset.p] = t.type === 'tel' ? setTel(t) : t.value; S.pErr[t.dataset.p] = ''; if (S.psrc) S.psrc[t.dataset.p] = ''; save();
      // 진정서 칸: 사업장 이름을 적는 대로 오른쪽 '자동으로 들어가는 내용'도 바뀐다
      if (t.dataset.p === 'company' && !S.f.company) { var nm = t.value.trim() || noCompany(); app.querySelectorAll('[data-auto-company]').forEach(function (el) { el.textContent = nm; }); }
    }
  });

  // 시트: 바깥(어두운 곳)을 누르면 닫힘. 안쪽은 .sheet-in 이 덮고 있어 dialog 자체를 누른 것은 바깥뿐
  document.addEventListener('click', function (e) { if (e.target.tagName === 'DIALOG') e.target.close(); });
  // 손잡이를 끌어 내리면 따라 내려오고, 80px 넘게 내리면 닫힘
  var drag = null;
  document.addEventListener('pointerdown', function (e) {
    var g = e.target.closest && e.target.closest('dialog .grab');
    if (!g) return;
    drag = { d: g.parentNode, y: e.clientY, dy: 0 };
    drag.d.style.transition = 'none';
    g.setPointerCapture(e.pointerId);
  });
  document.addEventListener('pointermove', function (e) {
    if (!drag) return;
    drag.dy = Math.max(0, e.clientY - drag.y);
    drag.d.style.transform = 'translateY(' + drag.dy + 'px)';
  });
  function dragEnd() {
    if (!drag) return;
    drag.d.style.transform = drag.d.style.transition = '';
    if (drag.dy > 80) drag.d.close();
    drag = null;
  }
  document.addEventListener('pointerup', dragEnd);
  document.addEventListener('pointercancel', dragEnd);

  // 데스크톱: 끌어다 놓기 · 붙여넣기(Ctrl+V)
  function dropZone(on) { var z = app.querySelector('.capture'); if (z) z.classList.toggle('drag', on); }
  document.addEventListener('dragover', function (e) { if (S.route !== 'upload' || S.up.status === 'reading') return; e.preventDefault(); dropZone(true); });
  document.addEventListener('dragleave', function (e) { if (!e.relatedTarget) dropZone(false); });
  document.addEventListener('drop', function (e) {
    if (S.route !== 'upload' || S.up.status === 'reading') return;
    e.preventDefault();
    dropZone(false);
    if (e.dataTransfer.files.length) addFiles(e.dataTransfer.files);
  });
  document.addEventListener('paste', function (e) {
    if (S.route !== 'upload' || S.up.status === 'reading' || !e.clipboardData) return;
    var files = Array.prototype.map.call(e.clipboardData.items, function (it) { return it.kind === 'file' ? it.getAsFile() : null; }).filter(Boolean);
    if (files.length) { e.preventDefault(); addFiles(files); }
  });

  var start = (location.hash || '').slice(1);
  // 주소에 화면 표시 없이 새로 열면 시작 화면에서 '이어서 하기 · 처음부터'를 고른다
  if (!start && S.saved && S.agreed && S.route !== 'start') resumeTo = S.route;
  if (!start || ALL.indexOf(start) < 0 || (!S.agreed && FREE.indexOf(start) < 0)) S.route = 'start';
  else S.route = start;
  if (NEED_CALC.indexOf(S.route) >= 0 && !S.calc) S.route = 'review';
  if (S.route === 'complaint' && !S.draft) S.route = 'form';
  // 사진은 메모리에만 있어 새로고침하면 사라진다 → 읽는 중 표시도 지운다
  if (S.up.status === 'reading') S.up = { status: 'idle' };
  S.ct.busy = false;
  // 언어 목록 → 한국어(기본 문구)와 고른 언어 문구를 받은 뒤 그린다
  fetch('i18n/languages.json').then(function (r) { return r.json(); }).then(function (l) { LANGS = l; }, function () {}).then(function () {
    if (!S.saved) S.lang = detectLang();
    if (!LANGS.some(function (x) { return x.code === S.lang; })) S.lang = 'ko';
    return Promise.all([loadLang('ko'), loadLang(S.lang)]);
  }).then(function () { render(false); });
  // 설치형 웹앱: 화면 파일만 오프라인 캐시 (sw.js). API 응답 · 입력값은 캐시하지 않는다
  if ('serviceWorker' in navigator && (location.protocol === 'https:' || /^(localhost|127\.0\.0\.1)$/.test(location.hostname))) {
    navigator.serviceWorker.register('sw.js').catch(function () {});
  }
})();
