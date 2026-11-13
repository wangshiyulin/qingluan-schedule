(function () {
  'use strict';
  if (location.protocol !== 'https:' || window.__qingluanAcademic) return;
  const MAX_ROWS = 10000, MAX_BYTES = 4 * 1024 * 1024, MAX_NODES = 30000;
  const denied = /password|passwd|pwd|token|secret|privatekey|apikey|cookie|authorization|csrf|session|phone|mobile|email|credential|密码|身份证|sfzh|studentid|studentno|^xh$|^mm$/i;
  const names = ['coursename','kcmc','课程名称','课程名','name','title'];
  const days = ['weekday','day','dayofweek','xqj','xq','星期'];
  const starts = ['startperiod','startnode','startunit','startsection','ksjc','start'];
  const sections = ['sections','nodes','jcs','jcor','skjc','jc','section','节次'];
  const metadata = ['teacher','teachername','teachernames','jsxm','jsmc','xm','skjs','location','room','classroom','classroomname','cdmc','jxdd','position','教师','老师','教室','地点'];
  const norm = x => String(x).toLowerCase().replace(/[\s_\-：:]/g, '');
  const frameID = Math.random().toString(36).slice(2);
  let sent = 0;
  const state = { records: [], documents: [], warnings: [], bytes: 0, keys: new Set(), request: '', frames: new Set(), expected: 0 };
  function warn(s) { if (!state.warnings.includes(s)) state.warnings.push(s); }
  function field(obj, list) { for (const k of Object.keys(obj || {})) if (list.includes(norm(k)) && obj[k] !== null && obj[k] !== '') return obj[k]; return ''; }
  function scalar(v) { return v === null || ['string','number','boolean'].includes(typeof v); }
  function safeValue(v, depth) {
    if (scalar(v)) return typeof v === 'string' ? v.slice(0, 4000) : v;
    if (depth > 1) return null;
    if (Array.isArray(v)) return v.slice(0, 200).map(x => scalar(x) ? x : safeValue(x, depth + 1)).filter(x => x !== null);
    if (v && typeof v === 'object') return field(v, ['name','xm','teachername','roomname','classroomname','node','index','section','week','zc']) || null;
    return null;
  }
  function add(row, inherited) {
    const clean = {};
    for (const k of Object.keys(inherited || {})) {
      if (denied.test(k)) continue;
      const v = safeValue(inherited[k], 0); if (v !== null && v !== undefined) clean[k] = v;
    }
    const nested = row.course || row.lesson || row.subject;
    if (nested && typeof nested === 'object' && !Array.isArray(nested)) {
      const label = field(nested, names); if (label) clean.courseName = label;
      if (nested.id !== undefined) clean.courseId = nested.id;
      for (const k of Object.keys(nested)) if (metadata.includes(norm(k)) && !denied.test(k)) {
        const v = safeValue(nested[k], 0); if (v !== null) clean[k] = v;
      }
    }
    for (const key of Object.keys(row).slice(0, 100)) {
      if (denied.test(key) || key.length > 80 || /^(meetings|children|students|users)$/i.test(key)) continue;
      const v = safeValue(row[key], 0); if (v !== null && v !== undefined) clean[key] = v;
    }
    const signature = JSON.stringify(Object.keys(clean).sort().map(k => [k, clean[k]]));
    if (state.keys.has(signature)) return;
    if (state.records.length >= MAX_ROWS || state.bytes + signature.length * 2 > MAX_BYTES) { warn('课表数据达到读取上限，结果可能不完整'); return; }
    state.keys.add(signature); state.bytes += signature.length * 2; state.records.push(clean);
  }
  function collect(root) {
    let visited = 0; const seen = new WeakSet();
    function walk(obj, inherited, depth, dictionary) {
      if (!obj || typeof obj !== 'object' || depth > 12 || ++visited > MAX_NODES || seen.has(obj)) return;
      seen.add(obj);
      if (Array.isArray(obj)) { for (const x of obj.slice(0, MAX_ROWS)) walk(x, inherited, depth + 1, dictionary); return; }
      let keys; try { keys = Object.keys(obj).slice(0, 120); } catch (_) { return; }
      if (dictionary || obj.__academicDictionary) {
        const role = dictionary || obj.__academicDictionary;
        const label = field(obj, ['name','xm','teachername','roomname','classroomname','cdmc']);
        if ((role === 'teacher' || role === 'room') && obj.id !== undefined && label) {
          add({ __academicDictionary: role, id: obj.id, name: label }); return;
        }
      }
      const name = field(obj, names), day = field(obj, days), units = field(obj, starts) || field(obj, sections);
      let context = inherited;
      if (name && (obj.meetings || obj.courseId || obj.courseName || obj.kcmc)) {
        context = Object.assign({}, inherited, { courseName: name });
        const id = obj.courseId ?? obj.id; if (id !== undefined) context.courseId = id;
        const table = obj.tableId ?? obj.termId ?? obj.semesterId; if (table !== undefined) context.tableId = table;
        for (const k of keys) if (metadata.includes(norm(k))) context[k] = obj[k];
      }
      if (day !== '' && units !== '') add(obj, context);
      // Explicit course bases can be linked to independent detail arrays by the Kotlin adapter.
      else if ((obj.courseName || obj.kcmc) && (obj.id !== undefined || obj.courseId !== undefined)) add(obj);
      else if ((obj.startTime && obj.endTime && (obj.node || obj.index)) || obj.semesterStartDate || ((obj.tableName || obj.termName) && (obj.startDate || obj.totalWeeks))) add(obj);
      for (const k of keys) {
        if (denied.test(k) || /^(students|users|profiles|accounts|credentials)$/i.test(k)) continue;
        try { if (obj[k] && typeof obj[k] === 'object') walk(obj[k], context, depth + 1,
          /^(teachers|teacherList|teacherMap|instructors|jsList)$/i.test(k) ? 'teacher' : /^(rooms|roomList|roomMap|classrooms|classroomList|venues)$/i.test(k) ? 'room' : dictionary); } catch (_) {}
      }
    }
    try { walk(root, {}, 0); } catch (_) { warn('部分页面数据无法读取'); }
    if (visited > MAX_NODES) warn('页面数据达到遍历上限，结果可能不完整');
  }
  function publish() {
    if (window.parent !== window && state.records.length > sent) {
      const rows = state.records.slice(sent); sent = state.records.length;
      window.parent.postMessage({ qingluanAcademic: 1, type: 'records', rows }, '*');
    }
  }
  function observe(text) {
    if (typeof text !== 'string' || text.length * 2 > MAX_BYTES || !/^[\s\uFEFF]*[\[{]/.test(text)) return;
    try { collect(JSON.parse(text)); publish(); } catch (_) {}
  }
  async function inspectResponse(response) {
    try {
      const type = response.headers.get('content-type') || '';
      if (!/json|text/i.test(type) || +(response.headers.get('content-length') || 0) > MAX_BYTES) return;
      const copy = response.clone(); if (!copy.body || !copy.body.getReader) return;
      const reader = copy.body.getReader(), decoder = new TextDecoder(); let total = 0, chunks = [];
      while (true) {
        const item = await reader.read(); if (item.done) break;
        total += item.value.byteLength;
        if (total > MAX_BYTES) { reader.cancel().catch(() => {}); warn('课表响应过大，结果可能不完整'); return; }
        chunks.push(decoder.decode(item.value, { stream: true }));
      }
      chunks.push(decoder.decode()); observe(chunks.join(''));
    } catch (_) {}
  }
  const originalFetch = window.fetch;
  if (typeof originalFetch === 'function') window.fetch = function () {
    const result = originalFetch.apply(this, arguments);
    result.then(inspectResponse).catch(() => {});
    return result;
  };
  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('load', () => {
      try {
        if (this.responseType === 'json') { collect(this.response); publish(); }
        else if (!this.responseType || this.responseType === 'text') observe(this.responseText);
      } catch (_) {}
    });
    return originalSend.apply(this, arguments);
  };
  function sanitizedHTML() {
    const body = document.body; if (!body || body.outerHTML.length * 2 > MAX_BYTES) { warn('网页过大，请打开独立课表页面'); return ''; }
    const clone = body.cloneNode(true);
    clone.querySelectorAll('script,style,input,textarea,button,nav,header,footer,noscript').forEach(x => x.remove());
    for (const node of [clone, ...clone.querySelectorAll('*')]) for (const attr of Array.from(node.attributes || []))
      if (denied.test(attr.name) || /^on|^(src|href|action)$/i.test(attr.name)) node.removeAttribute(attr.name);
    return clone.outerHTML;
  }
  function frameworkData() {
    const roots = [], seen = new WeakSet();
    function addRoot(obj) { if (obj && typeof obj === 'object' && !seen.has(obj) && roots.length < 2000) { seen.add(obj); roots.push(obj); } }
    for (const node of Array.from(document.querySelectorAll('*')).slice(0, 10000)) {
      try {
        const vm = node.__vue__; if (vm) addRoot(vm.$data);
        const vue = node.__vueParentComponent;
        if (vue) { addRoot(vue.props); addRoot(vue.setupState); }
        const react = Object.keys(node).find(k => k.startsWith('__reactProps$'));
        if (react) addRoot(node[react]);
        if (node.dataset && (node.dataset.courseName || node.dataset.kcmc)) addRoot(node.dataset);
      } catch (_) {}
    }
    collect(roots);
  }
  function children() { return Array.from(document.querySelectorAll('iframe,frame')).filter(f => !f.src || /^(https:|about:)/.test(f.src)); }
  function request(id) {
    state.request = id; state.documents = []; state.frames.clear(); state.expected = children().length;
    frameworkData();
    const html = sanitizedHTML(); if (html) state.documents.push(html);
    for (const child of children()) try { child.contentWindow.postMessage({ qingluanAcademic: 1, type: 'request', id }, '*'); } catch (_) {}
    publish();
  }
  window.addEventListener('message', event => {
    const data = event.data;
    if (!data || data.qingluanAcademic !== 1 || !/^https:\/\//.test(event.origin)) return;
    if (data.type === 'request' && event.source === window.parent && window.parent !== window) {
      request(String(data.id).slice(0, 80));
      window.parent.postMessage({ qingluanAcademic: 1, type: 'document', id: state.request, frame: frameID, html: state.documents[0] || '', pending: children().length, warnings: state.warnings }, '*');
      return;
    }
    const direct = children().some(f => f.contentWindow === event.source);
    if (!direct) return;
    if (data.type === 'records' && Array.isArray(data.rows)) { for (const row of data.rows.slice(0, MAX_ROWS)) collect(row); publish(); }
    if (data.type === 'document' && data.id === state.request && typeof data.html === 'string' && typeof data.frame === 'string' && data.frame.length < 80 && !state.frames.has(data.frame) && state.frames.size < 64) {
      if (Array.isArray(data.warnings)) for (const warning of data.warnings.slice(0, 20)) if (typeof warning === 'string') warn(warning.slice(0, 150));
      state.frames.add(data.frame); state.expected = Math.min(64, state.expected + Math.max(0, Math.min(30, +data.pending || 0)));
      if (data.html.length * 2 <= MAX_BYTES && state.documents.reduce((n,s) => n+s.length*2,0) + data.html.length*2 <= MAX_BYTES) state.documents.push(data.html);
      else warn('内嵌课表达到读取上限');
      if (window.parent !== window) window.parent.postMessage(data, '*');
    }
  });
  Object.defineProperty(window, '__qingluanAcademic', { value: {
    request: () => { const id = Math.random().toString(36).slice(2); request(id); return id; },
    snapshot: () => ({ format: 'QingluanWebCapture', records: state.records, documents: state.documents,
      warnings: state.warnings, incomplete: state.frames.size < state.expected, requestId: state.request })
  }});
})();
