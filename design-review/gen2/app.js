/* ==========================================================================
   MilkNote Gen 2 · 交互逻辑（自然无冗余版）
   ========================================================================== */
(() => {
  'use strict';

  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

  let toastTid = null;
  function toast(msg, ms = 1900) {
    const el = $('#toast');
    if (!el) return;
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTid);
    toastTid = setTimeout(() => el.classList.remove('show'), ms);
  }

  function esc(str) {
    return String(str).replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
  }

  /* ---- Modern Web Guidance: Top-Layer Exit + Light Dismiss Fallback ---- */
  let canDisplayTrans;
  function canTransitionDisplay() {
    if (canDisplayTrans !== undefined) return canDisplayTrans;
    if (!window.CSS?.supports?.('transition-behavior', 'allow-discrete') || !document.body) return false;
    const p = document.createElement('div');
    p.style.cssText = 'transition: display 1s allow-discrete !important; display: block;';
    document.body.appendChild(p);
    getComputedStyle(p).display;
    p.style.display = 'none';
    canDisplayTrans = getComputedStyle(p).display === 'block';
    p.remove();
    return canDisplayTrans;
  }

  function supportsTopLayerExit() {
    return Boolean(window.CSS?.supports?.('overlay', 'auto') && canTransitionDisplay());
  }

  async function closeModal(dlg) {
    if (!dlg || !dlg.open) return;
    if (!supportsTopLayerExit()) {
      dlg.setAttribute('data-closing', '');
      const anims = dlg.getAnimations ? dlg.getAnimations({ subtree: true }) : [];
      if (anims.length) {
        await Promise.race([
          Promise.allSettled(anims.map((a) => a.finished)),
          new Promise((r) => setTimeout(r, 350)),
        ]);
      }
      if (!dlg.hasAttribute('data-closing')) return;
      dlg.removeAttribute('data-closing');
    }
    dlg.close();
  }

  function wireModals() {
    $$('dialog.modal').forEach((dlg) => {
      dlg.addEventListener('cancel', (ev) => { ev.preventDefault(); closeModal(dlg); });
      if (!('closedBy' in HTMLDialogElement.prototype)) {
        dlg.addEventListener('click', (ev) => {
          if (ev.target !== dlg) return;
          const r = dlg.getBoundingClientRect();
          if (ev.clientX < r.left || ev.clientX > r.right || ev.clientY < r.top || ev.clientY > r.bottom) {
            closeModal(dlg);
          }
        });
      }
    });
    $$('[data-x]').forEach((b) => b.addEventListener('click', () => closeModal(document.getElementById(b.dataset.x))));
  }

  /* ---- Modern Web Guidance: Color Scheme Toggle ---- */
  const metaScheme = $('meta[name="color-scheme"]');
  const osDark = window.matchMedia('(prefers-color-scheme: dark)');

  function storedScheme() {
    try { const v = localStorage.getItem('color-scheme'); return v === 'light' || v === 'dark' ? v : null; }
    catch { return null; }
  }
  function sysScheme() { return osDark.matches ? 'dark' : 'light'; }
  function curScheme() { return storedScheme() ?? sysScheme(); }

  function setScheme(mode) {
    try {
      if (mode === 'system') localStorage.removeItem('color-scheme');
      else localStorage.setItem('color-scheme', mode);
    } catch {}
    if (metaScheme) metaScheme.content = mode === 'system' ? 'light dark' : mode;
    if (state.screen === 'settings') renderSettings();
  }

  function toggleScheme() {
    const target = curScheme() === 'dark' ? 'light' : 'dark';
    setScheme(target === sysScheme() ? 'system' : target);
  }

  window.addEventListener('storage', (e) => {
    if (e.key === 'color-scheme' && metaScheme) {
      metaScheme.content = e.newValue === 'light' || e.newValue === 'dark' ? e.newValue : 'light dark';
      if (state.screen === 'settings') renderSettings();
    }
  });

  /* ---- Modern Web Guidance: Toolbar focusgroup Fallback + Anchor Popover Fallback ---- */
  function wireToolbarFallback(bar) {
    if (!bar || 'focusGroup' in HTMLElement.prototype) return;
    const btns = () => $$('button', bar).filter((b) => b.offsetParent !== null);
    btns().forEach((b, i) => { b.tabIndex = i === 0 ? 0 : -1; });
    bar.addEventListener('focusin', ({ target }) => {
      const list = btns();
      if (list.includes(target)) list.forEach((b) => { b.tabIndex = b === target ? 0 : -1; });
    });
    bar.addEventListener('keydown', (e) => {
      const list = btns();
      const idx = list.indexOf(document.activeElement);
      if (idx < 0) return;
      const vert = bar.getAttribute('aria-orientation') === 'vertical';
      const rtl = getComputedStyle(bar).direction === 'rtl';
      const steps = vert ? { ArrowDown: 1, ArrowUp: -1 } : { ArrowRight: rtl ? -1 : 1, ArrowLeft: rtl ? 1 : -1 };
      let next = null;
      if (e.key in steps) next = (idx + steps[e.key] + list.length) % list.length;
      else if (e.key === 'Home') next = 0;
      else if (e.key === 'End') next = list.length - 1;
      if (next !== null) { e.preventDefault(); list[next].focus(); }
    });
  }

  let lastAnchor = null;
  function wirePopovers() {
    const nativeAnchor = CSS.supports && CSS.supports('position-area', 'block-end');
    document.addEventListener('click', (e) => {
      const t = e.target.closest('[popovertarget]');
      if (t) lastAnchor = t;
    }, true);
    $$('[popover].anchored').forEach((pop) => {
      pop.addEventListener('toggle', (ev) => {
        if (ev.newState !== 'open' || nativeAnchor) return;
        const a = pop._anchor || lastAnchor;
        if (!a) return;
        const r = a.getBoundingClientRect();
        const m = pop.getBoundingClientRect();
        const below = r.bottom + m.height + 8 <= window.innerHeight;
        pop.style.top = `${below ? r.bottom + 6 : Math.max(8, r.top - m.height - 6)}px`;
        pop.style.left = `${Math.max(8, Math.min(r.left, window.innerWidth - m.width - 8))}px`;
      });
    });
  }

  function hidePop(id) {
    const el = document.getElementById(id);
    if (el && typeof el.hidePopover === 'function') { try { el.hidePopover(); } catch {} }
  }

  /* ---- App State ---- */
  const state = {
    screen: 'library',
    lastAppScreen: 'library',
    nav: 'all',
    listView: false,
    sort: 'updated',
    activeNbId: 'nb-math',
    openTabs: ['nb-math', 'nb-linalg'],
    split: false,
    focus: false,
    dock: 'top',
    leftOpen: false,
    leftTab: 'pages',
    studioOpen: false, // Closed by default so pure paper leads!
    studioTab: 'ai',
    page: 1,
    totalPages: 4,
    pattern: 'dot',
    sheetBg: '#FFFFFF',
    margin: false,
    tool: 'fountain',
    ink: '#18191B',
    width: 2.6,
    stab: 30,
    rec: false,
    recSec: 0,
    recTid: null,
    draftAtts: [],
    snipIntent: '讲解',
    snipRect: null,
    mdEdit: false,
    setTab: 'appearance',
    setQ: '',
    prefs: {
      reducedMotion: false,
      invertPdf: true,
      invertPdfKeepImg: true,
      focusHideBar: true,
      gamma: 'std',
      tiltInk: true,
      frontBuffer: true,
      motionPredict: true,
      autoShape: true,
      autoShapePressure: true,
      shapeFill: false,
      eraserRevert: true,
      eraserTargets: { ink: true, hl: true, shape: true },
      swipeNewPage: true,
      defaultCanvas: 'paged-b5',
      penOnly: true,
      hoverCursor: true,
      palmDelay: 500,
      btAutoSub: true,
      takeoverActive: true,
      penMap: {
        pTap: '套索', pDbl: '笔 ⇄ 橡皮', pLong: '框选提问', pHold: '套索',
        sTap: '切换上一支笔', sHold: '临时橡皮', proPinch: '展开笔盒', proSlide: '调节笔宽',
      },
      gestMap: { twoTap: '撤销', threeTap: '重做', dblBlank: '无' },
      palmReject: true,
      longPressInsert: true,
      stylusSelectText: true,
      ocrLang: 'zh-en',
      ocrBgIndex: true,
      ocrCjkGrid: false,
      ocrLatex: true,
      recQuality: '128',
      recSyncStroke: true,
      mdAutoBind: true,
      mdAutoLink: true,
      activeProvider: 'glm',
      providers: [
        { id: 'glm', name: '智谱 GLM', model: 'glm-5.3-flash', proto: 'OpenAI 兼容', ctx: '1M', ok: true },
        { id: 'ds', name: 'DeepSeek', model: 'deepseek-flash', proto: 'OpenAI 兼容', ctx: '1M', ok: true },
        { id: 'kimi', name: 'Kimi', model: 'kimi-k3.1-pro', proto: 'OpenAI 兼容', ctx: '1M', ok: true },
        { id: 'claude', name: 'Anthropic Claude', model: 'claude-sonnet-5-5', proto: '原生 Messages', ctx: '1M', ok: true },
        { id: 'or', name: 'OpenRouter', model: 'z-ai/glm-5.3-flash', proto: 'OpenAI 兼容', ctx: '多厂商', ok: false },
      ],
      visionOnly: true,
      thinkLevel: '中',
      explicitCtx: true,
      redactOn: true,
      redactRules: { phone: true, name: true, id: true, qr: false },
      autosaveMs: '300',
      trashDays: '30',
      exportDpi: '300',
      cacheMb: '42.6 MB',
      lanUrl: 'http://192.168.31.18:8765',
    },
    ctxNbId: null,
    expFmt: 'pdf',
    expPicked: new Set([1, 2, 3, 4]),
    penBox: [
      { name: '钢笔 · 墨', tool: 'fountain', color: '#18191B', width: 2.6 },
      { name: '钢笔 · 蓝', tool: 'fountain', color: '#2563EB', width: 2.6 },
      { name: '批注 · 红', tool: 'fountain', color: '#D94436', width: 2.0 },
      { name: '荧光 · 黄', tool: 'highlighter', color: '#FDE047', width: 12 },
    ],
    folders: ['高数与线代', '英语考研', '设计草稿'],
    notebooks: [
      { id: 'nb-math', name: '高等数学 · 第三章', cover: '#2563EB', paper: 'dot', sheetBg: '#FFFFFF', mode: 'paged', folder: '高数与线代', pages: 4, pinned: true, isPdf: false, trashed: false, updated: '12 分钟前', uTs: 500, oTs: 500, art: 'math' },
      { id: 'nb-linalg', name: '线性代数讲义.pdf', cover: '#D94436', paper: 'blank', sheetBg: '#FFFFFF', mode: 'paged', folder: '高数与线代', pages: 48, pinned: true, isPdf: true, trashed: false, updated: '1 小时前', uTs: 460, oTs: 480, art: 'pdf' },
      { id: 'nb-eng', name: '考研英语错题本', cover: '#C98412', paper: 'cornell', sheetBg: '#FAF8F2', mode: 'paged', folder: '英语考研', pages: 9, pinned: false, isPdf: false, trashed: false, updated: '昨天', uTs: 380, oTs: 390, art: 'cornell' },
      { id: 'nb-paper', name: 'Multimodal Agents', cover: '#5C52C7', paper: 'line', sheetBg: '#FFFFFF', mode: 'paged', folder: '设计草稿', pages: 6, pinned: false, isPdf: false, trashed: false, updated: '3 天前', uTs: 280, oTs: 260, art: 'paper' },
      { id: 'nb-draft', name: '第二代构思草稿', cover: '#23865F', paper: 'grid', sheetBg: '#FFFFFF', mode: 'infinite', folder: '设计草稿', pages: 1, pinned: false, isPdf: false, trashed: false, updated: '5 天前', uTs: 200, oTs: 210, art: 'canvas' },
    ],
    cards: [
      { id: 'c1', title: '实对称矩阵正交对角化', formula: 'A = Aᵀ ⇒ Qᵀ A Q = diag(λ₁, …, λₙ)', body: '不同特征值对应的特征向量天然正交，重特征值在特征子空间内施密特正交化即可。', color: '#2563EB', source: '高等数学 · 第 1 页' },
      { id: 'c2', title: '矩阵可对角化的充要条件', formula: 'dim V(λᵢ) = n − rank(A − λᵢI) = mᵢ', body: 'n 阶矩阵有 n 个线性无关的特征向量 ⇔ 每个重根的几何重数等于代数重数。', color: '#D94436', source: '线性代数讲义 · 第 14 页' },
      { id: 'c3', title: '态度题转折信号', formula: null, body: '让步段（While / Granted）后借 however / yet 亮出真实立场，极端副词选项多为干扰。', color: '#C98412', source: '考研英语错题本 · 第 3 页' },
      { id: 'c4', title: '显式上下文原则', formula: null, body: '仅上传显式框选的切片或手动附加的页面，保持隐私边界清晰。', color: '#5C52C7', source: '第二代构思草稿' },
    ],
    mdByPage: {
      1: `### §3.1 特征值与特征向量\n\n- 特征方程 $|A - \\lambda I| = 0$\n- 解出 $\\lambda_i$ 后求 $(A - \\lambda_i I)x = 0$ 基础解系\n\n[[线性代数讲义.pdf#p14]] · [[实对称矩阵正交对角化]]\n\n#线性代数 #特征值`,
      2: `### §3.2 相似对角化\n\n检验重特征值的几何重数 $n - r(A - \\lambda I)$。\n\n[[矩阵可对角化的充要条件]] #线性代数`,
      3: `### §3.3 实对称矩阵\n\n实对称矩阵特征值全为实数，不同特征值特征向量正交。\n\n#线性代数`,
      4: `### 错题复盘\n\n行列式行变换优先提取公因式 $(\\lambda - c)$。\n\n#错题`,
    },
    aiMsgs: [],
    pins: { 1: [], 2: [], 3: [], 4: [] },
  };

  /* ---- Minimal Portrait Cover Artwork (Form Speaks!) ---- */
  function coverSvg(nb) {
    const bg = nb.sheetBg || '#FFFFFF';
    if (nb.art === 'math') {
      return `<svg class="book-svg" viewBox="0 0 180 236" xmlns="http://www.w3.org/2000/svg">
        <rect width="180" height="236" fill="${bg}"/>
        <g fill="#18191b" opacity="0.14">
          <circle cx="28" cy="24" r="1"/><circle cx="52" cy="24" r="1"/><circle cx="76" cy="24" r="1"/><circle cx="100" cy="24" r="1"/><circle cx="124" cy="24" r="1"/><circle cx="148" cy="24" r="1"/>
          <circle cx="28" cy="52" r="1"/><circle cx="52" cy="52" r="1"/><circle cx="76" cy="52" r="1"/><circle cx="100" cy="52" r="1"/><circle cx="124" cy="52" r="1"/><circle cx="148" cy="52" r="1"/>
          <circle cx="28" cy="80" r="1"/><circle cx="52" cy="80" r="1"/><circle cx="76" cy="80" r="1"/><circle cx="100" cy="80" r="1"/><circle cx="124" cy="80" r="1"/><circle cx="148" cy="80" r="1"/>
          <circle cx="28" cy="108" r="1"/><circle cx="52" cy="108" r="1"/><circle cx="76" cy="108" r="1"/><circle cx="100" cy="108" r="1"/><circle cx="124" cy="108" r="1"/><circle cx="148" cy="108" r="1"/>
          <circle cx="28" cy="136" r="1"/><circle cx="52" cy="136" r="1"/><circle cx="76" cy="136" r="1"/><circle cx="100" cy="136" r="1"/><circle cx="124" cy="136" r="1"/><circle cx="148" cy="136" r="1"/>
          <circle cx="28" cy="164" r="1"/><circle cx="52" cy="164" r="1"/><circle cx="76" cy="164" r="1"/><circle cx="100" cy="164" r="1"/><circle cx="124" cy="164" r="1"/><circle cx="148" cy="164" r="1"/>
          <circle cx="28" cy="192" r="1"/><circle cx="52" cy="192" r="1"/><circle cx="76" cy="192" r="1"/><circle cx="100" cy="192" r="1"/><circle cx="124" cy="192" r="1"/><circle cx="148" cy="192" r="1"/>
        </g>
        <text x="24" y="42" font-family="Georgia,serif" font-size="11.5" font-weight="700" fill="#18191b">§3.1 特征值与特征向量</text>
        <rect x="24" y="54" width="90" height="19" rx="3" fill="#FDE047" opacity="0.45"/>
        <text x="28" y="68" font-family="Georgia,serif" font-size="13.5" font-style="italic" fill="#2563eb">A x = λ x</text>
        <text x="24" y="96" font-family="monospace" font-size="9.5" fill="#4b5563">det(A − λI) = 0</text>
        <path d="M24 114 C56 106, 86 122, 128 112" fill="none" stroke="#2563eb" stroke-width="1.5" stroke-linecap="round" opacity="0.7"/>
        <path d="M98 156 L154 156 M126 132 L126 184" stroke="#9ca3af" stroke-width="1"/>
        <path d="M126 156 L148 136" stroke="#2563eb" stroke-width="1.8" stroke-linecap="round"/>
        <path d="M126 156 L144 174" stroke="#d94436" stroke-width="1.8" stroke-linecap="round"/>
        <path d="M24 202 L86 202" stroke="#d94436" stroke-width="1.5" stroke-linecap="round" opacity="0.65"/>
      </svg>`;
    }
    if (nb.art === 'pdf') {
      return `<svg class="book-svg" viewBox="0 0 180 236" xmlns="http://www.w3.org/2000/svg">
        <rect width="180" height="236" fill="#FFFFFF"/>
        <rect x="22" y="28" width="118" height="22" rx="4" fill="#fef2f2"/>
        <text x="28" y="43" font-family="monospace" font-size="9" font-weight="700" fill="#b91c1c">CHAPTER 3 · SPECTRAL</text>
        <rect x="22" y="64" width="134" height="7" rx="3" fill="#e5e7eb"/>
        <rect x="22" y="78" width="108" height="7" rx="3" fill="#e5e7eb"/>
        <rect x="22" y="98" width="136" height="32" rx="5" fill="#fefce8" stroke="#fde047"/>
        <text x="30" y="118" font-family="Georgia,serif" font-size="11" fill="#422006">Qᵀ A Q = Λ</text>
        <path d="M30 122 L126 121" stroke="#d94436" stroke-width="1.8" stroke-linecap="round" opacity="0.65"/>
        <rect x="22" y="146" width="128" height="7" rx="3" fill="#f1f2f5"/>
        <rect x="22" y="160" width="96" height="7" rx="3" fill="#f1f2f5"/>
        <text x="136" y="218" font-family="monospace" font-size="10" font-weight="700" fill="#d94436" opacity="0.72">PDF</text>
      </svg>`;
    }
    if (nb.art === 'cornell') {
      return `<svg class="book-svg" viewBox="0 0 180 236" xmlns="http://www.w3.org/2000/svg">
        <rect width="180" height="236" fill="${bg}"/>
        <line x1="60" y1="36" x2="60" y2="216" stroke="#d94436" stroke-width="1" opacity="0.45"/>
        <line x1="16" y1="36" x2="168" y2="36" stroke="#2563eb" stroke-width="1" opacity="0.35"/>
        <text x="24" y="26" font-family="sans-serif" font-size="10" font-weight="600" fill="#374151">Text 2 · 长难句</text>
        <rect x="68" y="48" width="92" height="6" rx="3" fill="#d6d0c0"/>
        <rect x="68" y="62" width="74" height="6" rx="3" fill="#d6d0c0"/>
        <rect x="68" y="82" width="94" height="7" rx="3" fill="#fde047" opacity="0.65"/>
        <rect x="68" y="98" width="80" height="6" rx="3" fill="#d6d0c0"/>
        <path d="M68 134 Q102 122 132 138 T160 128" fill="none" stroke="#b45309" stroke-width="1.6" stroke-linecap="round"/>
      </svg>`;
    }
    if (nb.art === 'canvas') {
      return `<svg class="book-svg" viewBox="0 0 180 236" xmlns="http://www.w3.org/2000/svg">
        <rect width="180" height="236" fill="#FFFFFF"/>
        <g stroke="#18191b" stroke-opacity="0.07" stroke-width="1">
          <path d="M0 30H180M0 60H180M0 90H180M0 120H180M0 150H180M0 180H180M0 210H180M36 0V236M72 0V236M108 0V236M144 0V236"/>
        </g>
        <rect x="26" y="44" width="60" height="28" rx="6" fill="#ffffff" stroke="#23865f" stroke-width="1.2"/>
        <rect x="104" y="56" width="56" height="28" rx="6" fill="#ffffff" stroke="#2563eb" stroke-width="1.2"/>
        <path d="M86 58 C95 58, 95 70, 104 70" fill="none" stroke="#23865f" stroke-width="1.3"/>
        <rect x="52" y="122" width="84" height="32" rx="6" fill="#ffffff" stroke="#d94436" stroke-width="1.1" stroke-dasharray="3 2"/>
        <path d="M56 72 C58 98, 72 106, 86 122" fill="none" stroke="#23865f" stroke-width="1.2"/>
        <text x="144" y="218" font-family="sans-serif" font-size="16" fill="#23865f" opacity="0.65">∞</text>
      </svg>`;
    }
    return `<svg class="book-svg" viewBox="0 0 180 236" xmlns="http://www.w3.org/2000/svg">
      <rect width="180" height="236" fill="${bg}"/>
      <g stroke="#2563eb" stroke-opacity="0.12" stroke-width="1">
        <line x1="16" y1="44" x2="168" y2="44"/><line x1="16" y1="70" x2="168" y2="70"/><line x1="16" y1="96" x2="168" y2="96"/><line x1="16" y1="122" x2="168" y2="122"/><line x1="16" y1="148" x2="168" y2="148"/><line x1="16" y1="174" x2="168" y2="174"/>
      </g>
      <text x="24" y="38" font-family="Georgia,serif" font-size="10.5" font-weight="700" fill="#18191b">${esc(nb.name.slice(0, 18))}</text>
      <path d="M24 64 C52 58, 86 70, 146 62" fill="none" stroke="#18191b" stroke-width="1.5" stroke-linecap="round" opacity="0.55"/>
      <path d="M24 90 C62 84, 98 96, 152 88" fill="none" stroke="${nb.cover}" stroke-width="1.6" stroke-linecap="round" opacity="0.72"/>
      <path d="M24 116 C56 112, 82 120, 118 114" fill="none" stroke="#18191b" stroke-width="1.4" stroke-linecap="round" opacity="0.45"/>
    </svg>`;
  }

  /* ---- Navigation ---- */
  function go(scr, opts = {}) {
    if (scr !== 'icons') state.lastAppScreen = scr;
    state.screen = scr;
    document.body.classList.toggle('studio-shown', scr === 'writer' && state.studioOpen);
    $$('.screen').forEach((el) => el.classList.toggle('active', el.id === `scr-${scr}`));
    $('#btn-mode-app').setAttribute('aria-pressed', String(scr !== 'icons'));
    $('#btn-mode-icons').setAttribute('aria-pressed', String(scr === 'icons'));
    if (scr === 'library') renderLibrary();
    if (scr === 'writer') {
      renderTabs();
      renderLeft();
      renderMd();
      renderLinks();
      renderAi();
      renderPins();
      requestAnimationFrame(() => { sizeCanvases(); drawMain(); drawSplit(); });
    }
    if (scr === 'settings') {
      if (opts.tab) state.setTab = opts.tab;
      renderSettings();
    }
    if (scr === 'export') renderExport();
  }

  /* ---- Library ---- */
  function curNb() { return state.notebooks.find((n) => n.id === state.activeNbId) || state.notebooks[0]; }

  function renderLibrary() {
    const live = state.notebooks.filter((n) => !n.trashed);
    $('#cnt-all').textContent = live.length;
    $('#cnt-pinned').textContent = live.filter((n) => n.pinned).length;
    $('#cnt-cards').textContent = state.cards.length;
    $('#cnt-trash').textContent = state.notebooks.filter((n) => n.trashed).length;

    $$('.nav-row[data-nav]').forEach((b) => {
      if (b.dataset.nav === state.nav) b.setAttribute('aria-current', 'page');
      else b.removeAttribute('aria-current');
    });

    $('#folder-list').innerHTML = state.folders.map((f) => {
      const cnt = live.filter((n) => n.folder === f).length;
      const cur = state.nav === `folder:${f}` ? 'aria-current="page"' : '';
      return `<button type="button" class="nav-row" data-nav="folder:${esc(f)}" ${cur}>
        <svg class="ico" aria-hidden="true"><use href="#i-folder"/></svg>
        <span style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(f)}</span>
        <span class="nav-num">${cnt}</span>
      </button>`;
    }).join('');

    const isCards = state.nav === 'cards';
    const bGrid = $('#books-grid');
    const cGrid = $('#cards-grid');
    $('#new-main-lbl').textContent = isCards ? '新建卡片' : '新建';

    if (isCards) {
      $('#shelf-title').textContent = '卡片';
      bGrid.style.display = 'none';
      cGrid.style.display = 'grid';
      cGrid.innerHTML = state.cards.map((c) => `
        <article class="index-card" style="--card-accent:${c.color}">
          <div class="index-card-top">
            <strong>${esc(c.title)}</strong>
            <button type="button" class="icon-btn" data-ins-c="${c.id}" aria-label="插入" style="min-inline-size:28px;min-block-size:28px;padding:0;color:var(--accent)"><svg class="ico" style="width:15px;height:15px" aria-hidden="true"><use href="#i-plus"/></svg></button>
            <button type="button" class="icon-btn" data-del-c="${c.id}" aria-label="删除" style="min-inline-size:28px;min-block-size:28px;padding:0"><svg class="ico" style="width:14px;height:14px" aria-hidden="true"><use href="#i-trash"/></svg></button>
          </div>
          ${c.formula ? `<div class="index-card-formula">${esc(c.formula)}</div>` : ''}
          <div class="index-card-body">${esc(c.body)}</div>
          <div class="index-card-foot"><span>${esc(c.source)}</span></div>
        </article>
      `).join('');
      return;
    }

    bGrid.style.display = 'grid';
    cGrid.style.display = 'none';
    bGrid.classList.toggle('as-list', state.listView);

    let list = state.nav === 'trash' ? state.notebooks.filter((n) => n.trashed) : live;
    if (state.nav === 'pinned') { $('#shelf-title').textContent = '星标'; list = list.filter((n) => n.pinned); }
    else if (state.nav === 'trash') { $('#shelf-title').textContent = '回收站'; }
    else if (state.nav.startsWith('folder:')) { const f = state.nav.slice(7); $('#shelf-title').textContent = f; list = list.filter((n) => n.folder === f); }
    else { $('#shelf-title').textContent = '全部'; }

    list = [...list].sort((a, b) => {
      if (state.sort !== 'name' && a.pinned !== b.pinned) return a.pinned ? -1 : 1;
      if (state.sort === 'name') return a.name.localeCompare(b.name, 'zh-CN');
      if (state.sort === 'opened') return b.oTs - a.oTs;
      return b.uTs - a.uTs;
    });

    const html = list.map((nb) => `
      <article class="book-item">
        <button type="button" class="book-open" data-open="${nb.id}">
          <div class="book-cover surface-always-light">
            <div class="book-spine" style="background:${nb.cover}"></div>
            ${nb.pinned ? '<span class="book-ribbon" aria-label="星标"></span>' : ''}
            ${nb.isPdf ? '<span class="book-dogear" aria-hidden="true"></span>' : ''}
            ${coverSvg(nb)}
          </div>
          <div class="book-caption">
            <div class="book-name">${esc(nb.name)}</div>
            <div class="book-meta mono">${nb.mode === 'infinite' ? '无限画布' : `${nb.pages} 页`} · ${esc(nb.updated)}</div>
          </div>
        </button>
        <button type="button" class="book-more" data-bmenu="${nb.id}" aria-label="更多操作">
          <svg class="ico" style="width:15px;height:15px" aria-hidden="true"><use href="#i-more"/></svg>
        </button>
      </article>
    `).join('');

    const plusSlot = state.nav === 'trash'
      ? (list.length ? '' : `<div style="grid-column:1/-1;padding:64px 0;text-align:center;color:var(--ink-3);font-size:13.5px">回收站为空</div>`)
      : `<button type="button" class="book-new" id="btn-slot-new" aria-label="新建笔记本"><svg class="ico" style="width:24px;height:24px" aria-hidden="true"><use href="#i-plus"/></svg></button>`;

    bGrid.innerHTML = html + plusSlot;
  }

  /* ---- Writer ---- */
  function openNb(id, split = false) {
    const nb = state.notebooks.find((n) => n.id === id);
    if (!nb || nb.trashed) { toast('请先恢复该笔记本'); return; }
    state.activeNbId = id;
    if (!state.openTabs.includes(id)) state.openTabs.push(id);
    state.pattern = nb.paper || 'dot';
    state.sheetBg = nb.sheetBg || '#FFFFFF';
    state.page = 1;
    state.totalPages = Math.max(1, Math.min(nb.pages, 4));
    if (split) state.split = true;
    applySheet();
    go('writer');
  }

  function renderTabs() {
    $('#w-tabs').innerHTML = state.openTabs.map((id) => {
      const nb = state.notebooks.find((n) => n.id === id);
      if (!nb) return '';
      const sel = id === state.activeNbId;
      return `<div class="w-tab" role="tab" aria-selected="${sel}" data-tab="${id}" tabindex="0">
        <span class="w-tab-dot" style="background:${nb.cover}"></span>
        <span class="w-tab-label">${esc(nb.name)}</span>
        ${state.openTabs.length > 1 ? `<button type="button" class="w-tab-x" data-xtab="${id}" aria-label="关闭"><svg class="ico" style="width:11px;height:11px" aria-hidden="true"><use href="#i-x"/></svg></button>` : ''}
      </div>`;
    }).join('') + `<button type="button" class="icon-btn" id="btn-tab-plus" aria-label="新建标签" style="min-inline-size:28px;min-block-size:28px;padding:0"><svg class="ico" style="width:14px;height:14px" aria-hidden="true"><use href="#i-plus"/></svg></button>`;

    $('#cap-main').textContent = curNb().name;
    $('#cap-main-p').textContent = `${state.page} / ${state.totalPages}`;
    $('#page-num').textContent = `${state.page} / ${state.totalPages}`;
    $('#btn-w-split').setAttribute('aria-pressed', String(state.split));
    $('#canvas-stage').classList.toggle('is-split', state.split);
  }

  function applySheet() {
    const s = $('#sheet-main');
    s.dataset.pattern = state.pattern;
    s.style.setProperty('--sheet-bg', state.sheetBg);
    s.classList.toggle('has-margin', state.margin);
    s.classList.toggle('is-hand', state.tool === 'hand');
    $('#m-margin-st').textContent = state.margin ? '开' : '关';
    $$('[data-pat]').forEach((b) => b.classList.toggle('active', b.dataset.pat === state.pattern));
    $$('[data-sbg]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.sbg === state.sheetBg)));
  }

  function renderLeft() {
    $('#left-drawer').classList.toggle('open', state.leftOpen);
    $('#btn-w-left').setAttribute('aria-pressed', String(state.leftOpen));
    $$('[data-ltab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.ltab === state.leftTab)));
    const box = $('#left-drawer-body');
    if (state.leftTab === 'pages') {
      const titles = ['§3.1 特征值', '§3.2 对角化', '§3.3 实对称', '错题复盘'];
      let h = '<div class="thumb-grid">';
      for (let i = 1; i <= state.totalPages; i++) {
        h += `<button type="button" class="thumb-item ${i === state.page ? 'active' : ''}" data-gop="${i}">
          <div class="thumb-paper surface-always-light" style="background:${state.sheetBg}">
            <div style="font-size:9.5px;font-weight:600;color:#2563eb">${titles[i - 1] || `第 ${i} 页`}</div>
            <svg viewBox="0 0 80 60" style="width:100%;margin-top:6px"><path d="M4 12 L68 12 M4 26 L58 26 M4 40 L44 40" stroke="#9ca3af" stroke-width="1.5" stroke-linecap="round"/></svg>
          </div>
          <span class="thumb-num mono">${i}</span>
        </button>`;
      }
      h += '</div><button type="button" class="btn-flat" id="btn-add-pg" style="width:100%;justify-content:center;margin-top:12px;min-block-size:34px;font-size:12.5px">+ 新页面</button>';
      box.innerHTML = h;
    } else if (state.leftTab === 'cards') {
      box.innerHTML = state.cards.map((c) => `
        <button type="button" class="link-row" data-dropc="${c.id}" style="margin-bottom:4px">
          <strong style="color:${c.color}">${esc(c.title)}</strong>
          <span>${esc(c.body.slice(0, 44))}…</span>
        </button>
      `).join('');
    } else {
      const items = [['§3.1 特征值与特征向量', 1], ['§3.2 矩阵相似对角化', 2], ['§3.3 实对称矩阵', 3], ['错题复盘', 4]];
      box.innerHTML = items.map(([t, p]) => `
        <button type="button" class="nav-row" data-gop="${p}" ${p === state.page ? 'aria-current="page"' : ''}>
          <span>${t}</span><span class="nav-num mono">${p}</span>
        </button>
      `).join('');
    }
  }

  /* ---- Live Canvas Drawing ---- */
  const strokes = { 1: [], 2: [], 3: [], 4: [] };
  const redos = { 1: [], 2: [], 3: [], 4: [] };
  const splitStrokes = [];
  let lasers = [];

  function sizeCanvases() {
    [$('#cv-main'), $('#cv-split')].forEach((cv) => {
      if (!cv) return;
      const r = cv.getBoundingClientRect();
      if (!r.width || !r.height) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      cv.width = Math.round(r.width * dpr);
      cv.height = Math.round(r.height * dpr);
      cv.getContext('2d').setTransform(dpr, 0, 0, dpr, 0, 0);
    });
    drawMain();
    drawSplit();
  }

  function drawPaperInk(ctx, w, page) {
    ctx.save();
    if (page === 1) {
      ctx.fillStyle = '#18191b';
      ctx.font = '600 21px "Songti SC", Georgia, serif';
      ctx.fillText('§3.1 特征值与特征向量', 44, 64);

      ctx.fillStyle = 'rgba(253, 224, 71, 0.44)';
      ctx.beginPath();
      ctx.roundRect(44, 92, 224, 32, 5);
      ctx.fill();

      ctx.fillStyle = '#2563eb';
      ctx.font = 'italic 600 24px Georgia, serif';
      ctx.fillText('A x  =  λ x   ( x ≠ 0 )', 52, 116);

      ctx.fillStyle = '#333740';
      ctx.font = '14.5px "MiSans", "PingFang SC", sans-serif';
      ctx.fillText('① 特征方程：det(A − λI) = 0', 46, 160);
      ctx.fillText('② 求全部特征值 λ₁, λ₂, …, λₙ', 46, 188);
      ctx.fillText('③ 解 (A − λᵢI) x = 0 得对应特征向量', 46, 216);

      ctx.strokeStyle = '#d94436';
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.moveTo(46, 250); ctx.quadraticCurveTo(136, 242, 224, 252);
      ctx.stroke();

      ctx.fillStyle = '#d94436';
      ctx.font = '600 14px "MiSans", "PingFang SC", sans-serif';
      ctx.fillText('例 2：验证实对称矩阵 A = [[2, 1], [1, 2]] 的正交对角化', 46, 278);

      ctx.strokeStyle = '#2563eb';
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.moveTo(52, 304); ctx.lineTo(52, 368);
      ctx.moveTo(192, 304); ctx.lineTo(192, 368);
      ctx.stroke();

      ctx.fillStyle = '#18191b';
      ctx.font = '16.5px "JetBrains Mono", monospace';
      ctx.fillText('2 − λ    1', 64, 330);
      ctx.fillText('  1    2 − λ', 64, 358);
      ctx.fillText(w > 540 ? '= (2 − λ)² − 1 = 0  ⇒  λ₁ = 3, λ₂ = 1' : '= (2−λ)² − 1 = 0', 206, 344);

      const cx = Math.min(w - 120, 580);
      const cy = 186;
      if (cx > 390) {
        ctx.strokeStyle = 'rgba(24,25,27,0.22)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(cx - 68, cy); ctx.lineTo(cx + 72, cy);
        ctx.moveTo(cx, cy + 62); ctx.lineTo(cx, cy - 68);
        ctx.stroke();
        ctx.strokeStyle = '#2563eb'; ctx.lineWidth = 2.2;
        ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + 52, cy - 52); ctx.stroke();
        ctx.strokeStyle = '#d94436';
        ctx.beginPath(); ctx.moveTo(cx, cy); ctx.lineTo(cx + 44, cy + 44); ctx.stroke();
        ctx.font = '11.5px monospace';
        ctx.fillStyle = '#2563eb'; ctx.fillText('v₁=(1,1)ᵀ', cx + 10, cy - 56);
        ctx.fillStyle = '#d94436'; ctx.fillText('v₂=(1,-1)ᵀ', cx + 10, cy + 56);
      }
    } else {
      const titles = { 2: '§3.2 矩阵相似对角化', 3: '§3.3 实对称矩阵与二次型', 4: '课后错题复盘' };
      ctx.fillStyle = '#18191b';
      ctx.font = '600 20px "Songti SC", Georgia, serif';
      ctx.fillText(titles[page] || `第 ${page} 页`, 44, 64);
    }
    ctx.restore();
  }

  function renderStrokes(ctx, list) {
    list.forEach((s) => {
      if (!s.pts || s.pts.length < 2) return;
      ctx.save();
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      if (s.tool === 'highlighter') {
        ctx.globalCompositeOperation = 'multiply';
        ctx.globalAlpha = 0.36;
        ctx.strokeStyle = s.color;
        ctx.lineWidth = Math.max(12, s.width * 4.2);
        ctx.beginPath();
        ctx.moveTo(s.pts[0].x, s.pts[0].y);
        for (let i = 1; i < s.pts.length; i++) ctx.lineTo(s.pts[i].x, s.pts[i].y);
        ctx.stroke();
      } else if (s.shape) {
        ctx.strokeStyle = s.color;
        ctx.lineWidth = s.width;
        ctx.beginPath();
        if (s.shape.t === 'line') { ctx.moveTo(s.shape.x1, s.shape.y1); ctx.lineTo(s.shape.x2, s.shape.y2); }
        else if (s.shape.t === 'circle') ctx.ellipse(s.shape.cx, s.shape.cy, s.shape.rx, s.shape.ry, 0, 0, Math.PI * 2);
        else ctx.roundRect(s.shape.x, s.shape.y, s.shape.w, s.shape.h, 5);
        ctx.stroke();
      } else {
        ctx.strokeStyle = s.color;
        for (let i = 1; i < s.pts.length; i++) {
          ctx.lineWidth = s.pts[i].w || s.width;
          ctx.beginPath();
          ctx.moveTo(s.pts[i - 1].x, s.pts[i - 1].y);
          ctx.lineTo(s.pts[i].x, s.pts[i].y);
          ctx.stroke();
        }
      }
      ctx.restore();
    });
  }

  function drawMain() {
    const cv = $('#cv-main');
    if (!cv) return;
    const r = cv.getBoundingClientRect();
    const ctx = cv.getContext('2d');
    ctx.clearRect(0, 0, r.width, r.height);
    drawPaperInk(ctx, r.width, state.page);
    renderStrokes(ctx, strokes[state.page] || []);

    const now = performance.now();
    lasers = lasers.filter((l) => now - l.ts < 1000);
    if (lasers.length) {
      lasers.forEach((l) => {
        ctx.save();
        ctx.strokeStyle = `rgba(217, 68, 54, ${Math.max(0, 1 - (now - l.ts) / 1000)})`;
        ctx.shadowColor = '#ef4444'; ctx.shadowBlur = 8; ctx.lineWidth = 3.5; ctx.lineCap = 'round';
        ctx.beginPath();
        l.pts.forEach((p, i) => (i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)));
        ctx.stroke();
        ctx.restore();
      });
      requestAnimationFrame(drawMain);
    }

    $('#btn-undo').setAttribute('aria-disabled', String((strokes[state.page] || []).length === 0));
    $('#btn-redo').setAttribute('aria-disabled', String((redos[state.page] || []).length === 0));
  }

  function drawSplit() {
    const cv = $('#cv-split');
    if (!cv || !state.split) return;
    const r = cv.getBoundingClientRect();
    const ctx = cv.getContext('2d');
    ctx.clearRect(0, 0, r.width, r.height);
    ctx.save();
    ctx.fillStyle = '#fef2f2';
    ctx.beginPath(); ctx.roundRect(24, 24, r.width - 48, 38, 6); ctx.fill();
    ctx.fillStyle = '#b91c1c';
    ctx.font = '700 12px monospace';
    ctx.fillText('CHAPTER 3 · SPECTRAL THEOREM (P.14)', 36, 48);
    ctx.fillStyle = '#18191b';
    ctx.font = '600 17px "Songti SC", Georgia, serif';
    ctx.fillText('定理 3.4（实对称矩阵谱分解）', 28, 96);
    ctx.fillStyle = '#4b5563';
    ctx.font = '13.5px "MiSans", sans-serif';
    ctx.fillText('任意 n 阶实对称矩阵 A 均可正交相似于对角矩阵 Λ：', 28, 126);
    ctx.fillStyle = '#fefce8'; ctx.strokeStyle = '#fde047';
    ctx.beginPath(); ctx.roundRect(28, 142, r.width - 56, 46, 6); ctx.fill(); ctx.stroke();
    ctx.fillStyle = '#422006';
    ctx.font = '600 15px monospace';
    ctx.fillText('A = Q Λ Qᵀ = ∑ λᵢ qᵢ qᵢᵀ', 42, 170);
    ctx.fillStyle = '#6b7280';
    ctx.font = '13px "MiSans", sans-serif';
    ctx.fillText('推论：不同特征值对应的特征向量必然相互正交。', 28, 220);
    ctx.restore();
    renderStrokes(ctx, splitStrokes);
  }

  function snapShape(pts) {
    if (pts.length < 6) return null;
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    pts.forEach((p) => { x0 = Math.min(x0, p.x); y0 = Math.min(y0, p.y); x1 = Math.max(x1, p.x); y1 = Math.max(y1, p.y); });
    const w = x1 - x0, h = y1 - y0, d = Math.hypot(w, h);
    if (d < 18) return null;
    const close = Math.hypot(pts[pts.length - 1].x - pts[0].x, pts[pts.length - 1].y - pts[0].y);
    if (close < Math.max(34, d * 0.34)) {
      const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
      const hits = pts.filter((p) => Math.abs((p.x - cx) / Math.max(1, w / 2)) > 0.72 && Math.abs((p.y - cy) / Math.max(1, h / 2)) > 0.72).length;
      return hits > pts.length * 0.14 ? { t: 'rect', x: x0, y: y0, w, h } : { t: 'circle', cx, cy, rx: w / 2, ry: h / 2 };
    }
    return { t: 'line', x1: pts[0].x, y1: pts[0].y, x2: pts[pts.length - 1].x, y2: pts[pts.length - 1].y };
  }

  function wireCanvas(cv, isSplit) {
    let down = false, cur = null, last = null;
    const pos = (e) => { const r = cv.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top, t: performance.now(), p: e.pressure && e.pressure !== 0.5 ? e.pressure : null }; };
    const erase = (pt) => {
      const arr = isSplit ? splitStrokes : strokes[state.page];
      const kept = arr.filter((s) => !s.pts.some((p) => Math.hypot(p.x - pt.x, p.y - pt.y) < 16));
      if (kept.length !== arr.length) {
        if (isSplit) { splitStrokes.length = 0; splitStrokes.push(...kept); drawSplit(); }
        else { strokes[state.page] = kept; drawMain(); }
      }
    };
    cv.addEventListener('pointerdown', (e) => {
      if (state.tool === 'hand' || e.button === 2) return;
      cv.setPointerCapture(e.pointerId);
      down = true;
      const pt = pos(e);
      last = pt;
      if (state.tool === 'eraser') { erase(pt); return; }
      if (state.tool === 'laser') { lasers.push({ pts: [pt], ts: performance.now() }); return; }
      cur = { tool: state.tool, color: state.tool === 'highlighter' ? '#FDE047' : state.ink, width: state.width, pts: [{ x: pt.x, y: pt.y, w: state.width }] };
      if (isSplit) splitStrokes.push(cur);
      else { strokes[state.page].push(cur); redos[state.page] = []; }
    });
    cv.addEventListener('pointermove', (e) => {
      if (!down) return;
      const pt = pos(e);
      if (state.tool === 'eraser') { erase(pt); return; }
      if (state.tool === 'laser') { const l = lasers[lasers.length - 1]; if (l) { l.pts.push(pt); l.ts = performance.now(); drawMain(); } return; }
      if (!cur || !last) return;
      const dist = Math.hypot(pt.x - last.x, pt.y - last.y);
      if (dist < 1.5) return;
      const vel = dist / Math.max(1, pt.t - last.t);
      const rawP = pt.p !== null ? pt.p : Math.max(0.3, Math.min(1, 1 - vel * 0.3));
      const gExp = state.prefs.gamma === 'soft' ? 1.4 : state.prefs.gamma === 'sharp' ? 0.7 : 1.0;
      const w = state.tool === 'fountain' ? state.width * (0.45 + 0.95 * Math.pow(rawP, gExp)) : state.width;
      const a = 1 - (state.stab / 100) * 0.52;
      const nx = last.x + (pt.x - last.x) * a, ny = last.y + (pt.y - last.y) * a;
      cur.pts.push({ x: nx, y: ny, w });
      last = { x: nx, y: ny, t: pt.t, p: pt.p };
      if (isSplit) drawSplit(); else drawMain();
    });
    const end = () => {
      if (!down) return;
      down = false;
      if (cur && state.tool === 'shape') { const sh = snapShape(cur.pts); if (sh) cur.shape = sh; }
      cur = null; last = null;
      if (isSplit) drawSplit(); else drawMain();
    };
    cv.addEventListener('pointerup', end);
    cv.addEventListener('pointercancel', end);
  }

  /* ---- Pins, Snip & Right Studio ---- */
  function addPin(title, body, color = '#2563EB') {
    const arr = state.pins[state.page] || (state.pins[state.page] = []);
    const off = (arr.length % 4) * 22;
    arr.push({ id: `p-${Date.now()}`, title, body, color, x: 48 + off, y: 390 + off });
    renderPins();
  }

  function renderPins() {
    const el = $('#pins-layer');
    el.innerHTML = (state.pins[state.page] || []).map((p) => `
      <div class="pin-card" data-pid="${p.id}" style="left:${p.x}px;top:${p.y}px;--pin-color:${p.color}">
        <div class="pin-card-head" data-dragp="${p.id}" style="color:${p.color}">
          <span>${esc(p.title)}</span>
          <button type="button" data-rmp="${p.id}" aria-label="移除">×</button>
        </div>
        <div>${esc(p.body)}</div>
      </div>
    `).join('');
  }

  function openStudio(open, tab) {
    state.studioOpen = open;
    if (tab) state.studioTab = tab;
    $('#right-studio').classList.toggle('open', open);
    document.body.classList.toggle('studio-shown', open && state.screen === 'writer');
    $('#btn-w-studio').setAttribute('aria-pressed', String(open));
    $$('[data-rtab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.rtab === state.studioTab)));
    $$('.studio-pane').forEach((p) => p.classList.toggle('active', p.id === `rp-${state.studioTab}`));
    requestAnimationFrame(sizeCanvases);
  }

  function askAi(text, intent = '讲解', snip = null) {
    const map = {
      '讲解': {
        think: '识别 A=[[2,1],[1,2]] 实对称矩阵及特征值 λ₁=3, λ₂=1，给出单位正交矩阵 Q。',
        body: '特征值计算正确。因 A 为实对称矩阵，不同特征值的特征向量天然正交，只需单位化：',
        math: 'q₁ = (1/√2, 1/√2)ᵀ   (λ₁ = 3)\nq₂ = (1/√2, −1/√2)ᵀ  (λ₂ = 1)\nQᵀ A Q = diag(3, 1)',
        pin: '实对称矩阵正交化：q₁=(1,1)ᵀ/√2，q₂=(1,−1)ᵀ/√2，Q=[q₁, q₂]。',
      },
      '归纳': {
        think: '提炼本页核心考点与快速验算公式。',
        body: '1. 优先行变换提公因式解特征方程\n2. 实对称矩阵必可正交对角化\n3. 考场快速自检：tr(A)=∑λᵢ 且 det(A)=∏λᵢ',
        math: 'tr(A) = 2 + 2 = 3 + 1 = 4,  det(A) = 3',
        pin: '快速自检：矩阵迹等于特征值之和，行列式等于特征值之积。',
      },
      '转 LaTeX': {
        think: '转换手写行列式为标准 LaTeX。',
        body: '可直接粘贴至论文或右侧 Markdown：',
        math: '\\det(A-\\lambda I)=\\begin{vmatrix}2-\\lambda&1\\\\1&2-\\lambda\\end{vmatrix}=(2-\\lambda)^2-1=0',
        pin: '\\det(A-\\lambda I)=(2-\\lambda)^2-1=0',
      },
    };
    const r = map[intent] || map['讲解'];
    state.aiMsgs.push({ id: `m-${Date.now()}`, q: text, snip, atts: [...state.draftAtts], r });
    state.draftAtts = [];
    renderDrafts();
    renderAi();
    const sc = $('#ai-msgs');
    sc.scrollTop = sc.scrollHeight;
  }

  function renderAi() {
    $('#ai-quick-row').style.display = state.aiMsgs.length ? 'none' : 'flex';
    $('#ai-msgs').innerHTML = state.aiMsgs.map((m) => `
      <div class="msg-me">
        ${m.snip ? `<div class="mono" style="font-size:11px;color:var(--accent);margin-bottom:4px">${esc(m.snip)}</div>` : ''}
        ${m.atts.length ? `<div class="mono" style="font-size:11px;color:var(--ink-2);margin-bottom:4px">${esc(m.atts.join(' · '))}</div>` : ''}
        ${esc(m.q)}
      </div>
      <div class="msg-ai">
        <details class="msg-think"><summary>思考过程</summary><div style="margin-top:4px">${esc(m.r.think)}</div></details>
        <div style="white-space:pre-line">${esc(m.r.body)}</div>
        <pre>${esc(m.r.math)}</pre>
        <div class="msg-bar">
          <button type="button" class="micro-btn primary" data-pinm="${m.id}">贴回纸面</button>
          <button type="button" class="micro-btn" data-cardm="${m.id}">存卡片</button>
          <button type="button" class="micro-btn" data-copym="${m.id}">复制</button>
        </div>
      </div>
    `).join('');
  }

  function renderDrafts() {
    $('#ai-drafts').innerHTML = state.draftAtts.map((a, i) => `
      <span class="micro-btn">${esc(a)} <button type="button" data-rma="${i}" style="border:0;background:transparent;padding:0 0 0 3px">×</button></span>
    `).join('');
    $('#btn-ai-send').disabled = !$('#ai-inp').value.trim() && !state.draftAtts.length;
  }

  function renderMd() {
    const raw = state.mdByPage[state.page] || `### 第 ${state.page} 页\n`;
    $('#md-scope').textContent = `第 ${state.page} 页`;
    $('#md-raw').value = raw;
    const wls = [];
    const safe = esc(raw).replace(/\[\[([^\]]+)\]\]/g, (_, t) => `@@W${wls.push(t) - 1}@@`);
    $('#md-view').innerHTML = safe
      .replace(/^### (.+)$/gm, '<h3>$1</h3>')
      .replace(/\$([^$]+)\$/g, (_, e) => `<code class="md-math">${e.replace(/\\lambda_i/g, 'λᵢ').replace(/\\lambda/g, 'λ')}</code>`)
      .replace(/(^|[\s·])#([^\s#<]+)/g, '$1<span class="md-tag">#$2</span>')
      .replace(/@@W(\d+)@@/g, (_, i) => `<span class="wikilink" data-wl="${wls[i]}">${wls[i]}</span>`)
      .replace(/\n/g, '<br>');
    $('#md-view').style.display = state.mdEdit ? 'none' : 'block';
    $('#md-raw').style.display = state.mdEdit ? 'block' : 'none';
    $('#btn-md-toggle').textContent = state.mdEdit ? '预览' : '源码';
  }

  function renderLinks() {
    $('#links-body').innerHTML = `
      <div style="font-size:11.5px;color:var(--ink-3);padding:2px 10px">反向链接</div>
      <button type="button" class="link-row" data-lsplit="nb-linalg"><strong>线性代数讲义.pdf · p.14</strong><span>定理 3.4 谱分解</span></button>
      <div style="font-size:11.5px;color:var(--ink-3);padding:10px 10px 2px">卡片</div>
      ${state.cards.slice(0, 3).map((c) => `<button type="button" class="link-row" data-dropc="${c.id}"><strong>${esc(c.title)}</strong><span>点击贴回当前页</span></button>`).join('')}
    `;
  }

  function renderPenPop() {
    $('#pen-w-txt').textContent = `${(state.width * 0.25).toFixed(2)} mm`;
    $('#pen-s-txt').textContent = `${state.stab}%`;
    const col = state.tool === 'highlighter' ? '#FDE047' : state.ink;
    const sw = state.tool === 'highlighter' ? 14 : Math.max(1.5, state.width * 1.4);
    $('#pen-prev-svg').innerHTML = `<path d="M16 30 C52 6, 86 38, 124 20 C160 6, 192 34, 220 16" fill="none" stroke="${col}" stroke-width="${sw}" stroke-opacity="${state.tool === 'highlighter' ? 0.5 : 1}" stroke-linecap="round"/>`;
    $('#penbox-list').innerHTML = state.penBox.map((p, i) => `
      <button type="button" class="micro-btn" data-pbox="${i}"><span style="width:8px;height:8px;border-radius:50%;background:${p.color}"></span>${esc(p.name)}</button>
    `).join('');
  }

  /* ---- Settings & Export ---- */
  function renderSettings() {
    $$('[data-stab]').forEach((b) => {
      if (b.dataset.stab === state.setTab) b.setAttribute('aria-current', 'page');
      else b.removeAttribute('aria-current');
    });
    const st = storedScheme();
    const p = state.prefs;
    const sw = (key, checked) => `<input type="checkbox" class="switch" data-sw="${key}" ${checked ? 'checked' : ''}>`;
    const sel = (key, opts, val) => `<select class="set-select" data-sel="${key}">${opts.map(([v, l]) => `<option value="${esc(v)}" ${v === val ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`;
    const row = (label, sub, ctrl, kw = '') => ({
      kw: `${label} ${sub || ''} ${kw}`.toLowerCase(),
      html: `<div class="set-row"><div><strong>${label}</strong>${sub ? `<small>${sub}</small>` : ''}</div>${ctrl}</div>`,
    });

    const penActions = [
      ['笔 ⇄ 橡皮', '笔 ⇄ 橡皮'], ['框选提问', '框选提问'], ['套索', '套索'],
      ['临时橡皮', '临时橡皮'], ['切换上一支笔', '切换上一支笔'], ['展开笔盒', '展开笔盒'],
      ['调节笔宽', '调节笔宽'], ['撤销', '撤销'], ['新建页', '新建页'], ['激光笔', '激光笔'], ['无', '无'],
    ];

    const groupsByTab = {
      appearance: {
        title: '外观',
        topHtml: `
          <div class="theme-3">
            <button type="button" class="theme-tile" data-th="system" aria-pressed="${st === null}"><div class="theme-chip-box"><div style="flex:1;background:#fff"></div><div style="flex:1;background:#16171a"></div></div><span>跟随系统</span></button>
            <button type="button" class="theme-tile" data-th="light" aria-pressed="${st === 'light'}"><div class="theme-chip-box" style="background:#fff"></div><span>浅色</span></button>
            <button type="button" class="theme-tile" data-th="dark" aria-pressed="${st === 'dark'}"><div class="theme-chip-box" style="background:#16171a"></div><span>深色</span></button>
          </div>`,
        groups: [
          {
            sec: '默认纸张',
            rows: [
              row('默认纸纹', '', sel('pattern', [['dot', '点阵'], ['line', '横线'], ['grid', '方格'], ['cornell', '康奈尔'], ['tian', '田字格'], ['blank', '空白']], state.pattern), '模板 点阵 横线 方格'),
              row('默认纸色', '', `<div class="set-dot-row">${[['#FFFFFF', '纯白'], ['#FAF8F2', '奶白'], ['#F4F0DE', '米黄'], ['#EEF4EE', '浅绿']].map(([c, n]) => `<button type="button" class="color-dot" data-def-bg="${c}" style="--dot-c:${c}" aria-pressed="${state.sheetBg === c}" aria-label="${n}"></button>`).join('')}</div>`, '颜色 背景'),
              row('暗色反转 PDF', '', sw('invertPdf', p.invertPdf), '夜间 深色'),
              row('反转时保留图片原色', '', sw('invertPdfKeepImg', p.invertPdfKeepImg), '图片 夜间'),
            ],
          },
          {
            sec: '界面',
            rows: [
              row('书架默认视图', '', sel('listView', [['grid', '网格'], ['list', '列表']], state.listView ? 'list' : 'grid'), '列表 封面'),
              row('减弱动效', '', sw('reducedMotion', p.reducedMotion), '动画 过渡'),
              row('专注模式隐藏状态栏', '', sw('focusHideBar', p.focusHideBar), '全屏 沉浸'),
            ],
          },
        ],
      },
      writing: {
        title: '书写',
        topHtml: `
          <div class="set-group">
            <span class="set-sec">压感曲线</span>
            <div class="theme-3">
              ${[['soft', '柔和', 'γ 1.4', 'M8 28 C44 27, 76 24, 112 18'], ['std', '标准', 'γ 1.0', 'M8 28 C42 24, 74 16, 112 10'], ['sharp', '灵敏', 'γ 0.7', 'M8 28 C30 16, 62 11, 112 8']].map(([id, l, g, d]) => `
                <button type="button" class="theme-tile" data-gamma="${id}" aria-pressed="${p.gamma === id}">
                  <svg viewBox="0 0 120 36" style="width:100%;height:36px"><path d="${d}" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round"/></svg>
                  <span>${l} <span class="mono" style="font-size:11px;color:var(--ink-3)">${g}</span></span>
                </button>`).join('')}
            </div>
          </div>`,
        groups: [
          {
            sec: '笔迹与出墨',
            rows: [
              row('默认笔画稳定', '', `<div class="set-range"><input type="range" min="0" max="100" step="5" value="${state.stab}" data-rng="stab"><span class="mono" id="set-stab-v">${state.stab}%</span></div>`, '防抖 平滑'),
              row('倾斜笔锋', '', sw('tiltInk', p.tiltInk), '钢笔 荧光笔 倾角'),
              row('GL 前置缓冲出墨', '低延迟渲染路径', sw('frontBuffer', p.frontBuffer), 'FrontBufferedRenderer'),
              row('轨迹预测', 'Android 14 MotionPredictor', sw('motionPredict', p.motionPredict), '预测 低延迟'),
            ],
          },
          {
            sec: '图形与橡皮',
            rows: [
              row('一笔成型', '停顿自动校准直线与图形', sw('autoShape', p.autoShape), '形状 直线 圆 矩形'),
              row('一笔成型跟随压感粗细', '', sw('autoShapePressure', p.autoShapePressure), '形状 粗细'),
              row('封闭图形自动填充', '', sw('shapeFill', p.shapeFill), '填充'),
              row('橡皮擦除后切回画笔', '', sw('eraserRevert', p.eraserRevert), '自动取消'),
              row('橡皮擦除对象', '', `<div class="set-chips">${[['ink', '笔迹'], ['hl', '荧光笔'], ['shape', '图形']].map(([k, l]) => `<button type="button" class="chip-opt ${p.eraserTargets[k] ? 'active' : ''}" data-et="${k}">${l}</button>`).join('')}</div>`, '过滤'),
            ],
          },
          {
            sec: '画布与笔匣',
            rows: [
              row('默认新建画布', '', sel('defaultCanvas', [['paged-b5', '分页 · B5 横向'], ['paged-a4', '分页 · A4 竖向'], ['infinite', '无边画布']], p.defaultCanvas), '尺寸 方向'),
              row('笔匣默认停靠', '', sel('dock', [['top', '顶部'], ['bottom', '底部'], ['left', '左侧'], ['right', '右侧']], state.dock), '工具栏 位置'),
              row('末页右滑自动新建页', '', sw('swipeNewPage', p.swipeNewPage), '翻页'),
            ],
          },
        ],
      },
      pen: {
        title: '触控笔',
        topHtml: '',
        groups: [
          {
            sec: '设备连接',
            rows: [
              row('小米焦点触控笔', '电量 84% · 240Hz', `<button type="button" class="btn-flat" id="btn-s-diag" style="min-block-size:32px;font-size:12.5px">诊断</button>`, '焦点笔 硬件 电量'),
              row('长按接管', p.takeoverActive ? '书写内不触发系统截屏' : '未授权 · 需 adb 授予 WRITE_SECURE_SETTINGS', `<button type="button" class="chip-opt ${p.takeoverActive ? 'active' : ''}" id="btn-takeover-sim">${p.takeoverActive ? '已激活' : '未激活'}</button>`, 'gb_boosting 截屏'),
              row('蓝牙电量自动订阅', 'GATT 0x180F', sw('btAutoSub', p.btAutoSub), '磁吸 充电'),
            ],
          },
          {
            sec: '按键与笔身手势',
            rows: [
              row('主键单击', '', sel('penMap.pTap', penActions, p.penMap.pTap), '按键 映射'),
              row('主键双击', '', sel('penMap.pDbl', penActions, p.penMap.pDbl), '橡皮'),
              row('主键长按', '', sel('penMap.pLong', penActions, p.penMap.pLong), '框选 AI'),
              row('主键按住 + 书写', '', sel('penMap.pHold', penActions, p.penMap.pHold), '修饰'),
              row('副键单击', '', sel('penMap.sTap', penActions, p.penMap.sTap), '副键'),
              row('副键按住 + 书写', '', sel('penMap.sHold', penActions, p.penMap.sHold), '临时橡皮'),
              row('轻捏', '焦点笔 Pro', sel('penMap.proPinch', penActions, p.penMap.proPinch), 'Pro 触控'),
              row('上滑 / 下滑', '焦点笔 Pro', sel('penMap.proSlide', penActions, p.penMap.proSlide), 'Pro 触控'),
            ],
          },
          {
            sec: '悬停与防误触',
            rows: [
              row('仅触控笔书写', '手指仅平移与缩放', sw('penOnly', p.penOnly), '防误触'),
              row('悬停笔尖光标', '', sw('hoverCursor', p.hoverCursor), '预览'),
              row('悬停防误触恢复延迟', '', `<div class="set-range"><input type="range" min="100" max="1000" step="50" value="${p.palmDelay}" data-rng="palmDelay"><span class="mono" id="set-palm-v">${p.palmDelay} ms</span></div>`, '延迟'),
              row('按键映射', '', `<button type="button" class="btn-flat" id="btn-pen-reset" style="min-block-size:32px;font-size:12.5px">恢复默认</button>`, '重置'),
            ],
          },
        ],
      },
      gestures: {
        title: '手势与快捷键',
        topHtml: '',
        groups: [
          {
            sec: '触控手势',
            rows: [
              row('双指轻点', '', sel('gestMap.twoTap', [['撤销', '撤销'], ['重做', '重做'], ['专注', '切换专注'], ['无', '无']], p.gestMap.twoTap), '手势'),
              row('三指轻点', '', sel('gestMap.threeTap', [['重做', '重做'], ['清空选区', '清空选区'], ['框选提问', '框选提问'], ['无', '无']], p.gestMap.threeTap), '手势'),
              row('单指双击空白', '', sel('gestMap.dblBlank', [['无', '无'], ['适配宽度', '适配纸宽'], ['专注', '切换专注']], p.gestMap.dblBlank), '缩放'),
              row('手掌贴屏防误触', '', sw('palmReject', p.palmReject), '防误触'),
              row('长按页面唤起插入菜单', '', sw('longPressInsert', p.longPressInsert), '粘贴 新建'),
              row('笔尖长按选中文本', '', sw('stylusSelectText', p.stylusSelectText), 'PDF 文字'),
            ],
          },
          {
            sec: '外接键盘',
            rows: [
              row('搜索与命令面板', '', '<span class="set-kbd">⌘ K</span>', '快捷键'),
              row('框选提问', '', '<span class="set-kbd">⌥ S</span>', '快捷键'),
              row('右侧面板', '', '<span class="set-kbd">⌥ A</span>', '快捷键 AI'),
              row('双开分屏', '', '<span class="set-kbd">⌘ \\</span>', '快捷键'),
              row('撤销 / 重做', '', '<span class="set-kbd">⌘ Z · ⇧⌘ Z</span>', '快捷键'),
              row('专注模式', '', '<span class="set-kbd">⌘ .</span>', '快捷键'),
            ],
          },
        ],
      },
      ocr: {
        title: '识别与工具',
        topHtml: '',
        groups: [
          {
            sec: '手写识别',
            rows: [
              row('识别语言', '', sel('ocrLang', [['zh-en', '中文 + 英文'], ['en', '英文'], ['ja', '日文'], ['ko', '韩文']], p.ocrLang), 'OCR 语言'),
              row('后台自动索引手写文本', '', sw('ocrBgIndex', p.ocrBgIndex), '搜索 OCR'),
              row('中日韩网格逐格识别', '', sw('ocrCjkGrid', p.ocrCjkGrid), '方块字 网格'),
              row('公式自动转 LaTeX', '', sw('ocrLatex', p.ocrLatex), '数学 公式'),
            ],
          },
          {
            sec: '录音与伴随笔记',
            rows: [
              row('录音品质', '', sel('recQuality', [['64', '标准 64 kbps'], ['128', '清晰 128 kbps']], p.recQuality), '音频 麦克风'),
              row('录音时间轴联动笔迹', '', sw('recSyncStroke', p.recSyncStroke), '回放'),
              row('伴随 Markdown 绑定当前页', '', sw('mdAutoBind', p.mdAutoBind), 'Markdown'),
              row('自动建议双向链接', '', sw('mdAutoLink', p.mdAutoLink), '双链 关联'),
            ],
          },
        ],
      },
      ai: {
        title: '模型与隐私',
        topHtml: '',
        groups: [
          {
            sec: 'Provider 与视觉模型',
            rows: [
              ...p.providers.map((pr) => row(
                `${pr.name}`,
                `<span class="mono">${esc(pr.model)}</span> · ${esc(pr.proto)} · ${esc(pr.ctx)}`,
                `<div style="display:flex;gap:6px;align-items:center">
                  <button type="button" class="micro-btn" data-test-prov="${pr.id}">测试</button>
                  <button type="button" class="chip-opt ${p.activeProvider === pr.id ? 'active' : ''}" data-use-prov="${pr.id}">${p.activeProvider === pr.id ? '当前' : '选用'}</button>
                </div>`,
                `${pr.name} ${pr.model} AI 视觉`
              )),
              row('自定义端点', '', `<button type="button" class="btn-flat" id="btn-add-prov" style="min-block-size:32px;font-size:12.5px">+ 添加 Provider</button>`, '新增 OpenAI Anthropic'),
            ],
          },
          {
            sec: '推理与默认行为',
            rows: [
              row('仅允许视觉模型', '', sw('visionOnly', p.visionOnly), '多模态'),
              row('默认思考深度', '', `<div class="set-chips">${['关', '中', '高'].map((lv) => `<button type="button" class="chip-opt ${p.thinkLevel === lv ? 'active' : ''}" data-tlv="${lv}">${lv}</button>`).join('')}</div>`, '思考 推理'),
              row('默认框选意图', '', sel('snipIntent', [['讲解', '讲解'], ['归纳', '归纳'], ['翻译', '翻译'], ['出题', '出题'], ['转 LaTeX', '转 LaTeX']], state.snipIntent), '框选'),
            ],
          },
          {
            sec: '隐私',
            rows: [
              row('严格显式上下文', '', sw('explicitCtx', p.explicitCtx), '上传 附件'),
              row('上传前端内脱敏涂黑', '', sw('redactOn', p.redactOn), '隐私 涂黑'),
              row('脱敏范围', '', `<div class="set-chips">${[['phone', '手机号'], ['name', '姓名'], ['id', '学号'], ['qr', '二维码']].map(([k, l]) => `<button type="button" class="chip-opt ${p.redactRules[k] ? 'active' : ''}" data-rr="${k}">${l}</button>`).join('')}</div>`, '脱敏'),
              row('API Key 存储', 'Android Keystore 加密 · 不随备份导出', '<span class="mono" style="font-size:12px;color:var(--green)">仅本机</span>', '密钥 安全'),
            ],
          },
        ],
      },
      storage: {
        title: '存储与更新',
        topHtml: '',
        groups: [
          {
            sec: '本地数据',
            rows: [
              row('存储目录', '<span class="mono">/Documents/MilkNote/*.milk</span>', '<span class="mono" style="font-size:12px;color:var(--ink-3)">本地</span>', '目录 路径'),
              row('自动保存间隔', '', sel('autosaveMs', [['300', '300 ms'], ['1000', '1 秒'], ['3000', '3 秒']], p.autosaveMs), '防抖 落盘'),
              row('回收站保留时长', '', sel('trashDays', [['7', '7 天'], ['30', '30 天'], ['0', '永久保留']], p.trashDays), '删除 恢复'),
              row('回收站操作', '', `<button type="button" class="btn-text-danger" id="btn-empty-trash" style="min-block-size:32px;font-size:12.5px">清空回收站</button>`, '删除'),
            ],
          },
          {
            sec: '导出与缓存',
            rows: [
              row('默认导出格式', '', sel('expFmt', [['pdf', 'PDF'], ['png', 'PNG'], ['milk', '.milk 备份']], state.expFmt), '导出'),
              row('导出分辨率', '', sel('exportDpi', [['150', '150 DPI'], ['300', '300 DPI']], p.exportDpi), '清晰度 打印'),
              row('缩略图缓存', `<span class="mono" id="set-cache-sz">${p.cacheMb}</span>`, `<button type="button" class="btn-flat" id="btn-clr-cache" style="min-block-size:32px;font-size:12.5px">清理</button>`, '缓存 空间'),
            ],
          },
          {
            sec: '更新与关于',
            rows: [
              row('局域网更新服务', '', `<div style="display:flex;gap:8px;align-items:center"><input type="text" class="field-inp mono" id="inp-lan-url" value="${esc(p.lanUrl)}" style="width:196px;min-block-size:32px;font-size:12px"><button type="button" class="btn-flat" id="btn-s-upd" style="min-block-size:32px;font-size:12.5px">检查</button></div>`, 'serveUpdates 更新'),
              row('版本', '<span class="mono">MilkNote 2.0.0-preview</span>', '<span class="mono" style="font-size:12px;color:var(--ink-3)">本地构建</span>', '关于'),
            ],
          },
        ],
      },
    };

    const root = $('#set-content');
    const q = state.setQ.trim().toLowerCase();
    if (q) {
      const matchedGroups = [];
      Object.values(groupsByTab).forEach((tab) => {
        tab.groups.forEach((g) => {
          const hits = g.rows.filter((r) => r.kw.includes(q) || tab.title.toLowerCase().includes(q) || g.sec.toLowerCase().includes(q));
          if (hits.length) matchedGroups.push({ sec: `${tab.title} · ${g.sec}`, rows: hits });
        });
      });
      root.innerHTML = matchedGroups.length
        ? matchedGroups.map((g) => `<div class="set-group"><span class="set-sec">${g.sec}</span><div class="set-list">${g.rows.map((r) => r.html).join('')}</div></div>`).join('')
        : `<div style="color:var(--ink-3);padding:40px 0;text-align:center;font-size:13.5px">无匹配设置项</div>`;
    } else {
      const cur = groupsByTab[state.setTab];
      root.innerHTML = `<h2>${cur.title}</h2>${cur.topHtml}` +
        cur.groups.map((g) => `<div class="set-group"><span class="set-sec">${g.sec}</span><div class="set-list">${g.rows.map((r) => r.html).join('')}</div></div>`).join('');
    }

    // Bind live interactive setting controls
    $$('[data-th]', root).forEach((b) => b.addEventListener('click', () => setScheme(b.dataset.th)));
    $$('[data-def-bg]', root).forEach((b) => b.addEventListener('click', () => {
      state.sheetBg = b.dataset.defBg;
      applySheet();
      renderSettings();
    }));
    $$('[data-gamma]', root).forEach((b) => b.addEventListener('click', () => {
      p.gamma = b.dataset.gamma;
      renderSettings();
    }));
    $$('[data-sw]', root).forEach((chk) => chk.addEventListener('change', () => {
      const k = chk.dataset.sw;
      p[k] = chk.checked;
      if (k === 'reducedMotion') document.body.dataset.motion = p.reducedMotion ? 'reduced' : 'normal';
    }));
    $$('[data-sel]', root).forEach((s) => s.addEventListener('change', () => {
      const k = s.dataset.sel;
      if (k === 'pattern') { state.pattern = s.value; applySheet(); }
      else if (k === 'listView') { state.listView = s.value === 'list'; }
      else if (k === 'dock') { state.dock = s.value; $('#writer-shell').dataset.dock = s.value; }
      else if (k === 'snipIntent') { state.snipIntent = s.value; }
      else if (k === 'expFmt') { state.expFmt = s.value; }
      else if (k.includes('.')) { const [obj, sub] = k.split('.'); p[obj][sub] = s.value; }
      else { p[k] = s.value; }
    }));
    $$('[data-rng]', root).forEach((rng) => rng.addEventListener('input', () => {
      if (rng.dataset.rng === 'stab') {
        state.stab = Number(rng.value);
        $('#set-stab-v', root).textContent = `${state.stab}%`;
        renderPenPop();
      } else if (rng.dataset.rng === 'palmDelay') {
        p.palmDelay = Number(rng.value);
        $('#set-palm-v', root).textContent = `${p.palmDelay} ms`;
      }
    }));
    $$('[data-et]', root).forEach((b) => b.addEventListener('click', () => {
      p.eraserTargets[b.dataset.et] = !p.eraserTargets[b.dataset.et];
      b.classList.toggle('active', p.eraserTargets[b.dataset.et]);
    }));
    $$('[data-rr]', root).forEach((b) => b.addEventListener('click', () => {
      p.redactRules[b.dataset.rr] = !p.redactRules[b.dataset.rr];
      b.classList.toggle('active', p.redactRules[b.dataset.rr]);
    }));
    $$('[data-tlv]', root).forEach((b) => b.addEventListener('click', () => {
      p.thinkLevel = b.dataset.tlv;
      renderSettings();
    }));
    $$('[data-use-prov]', root).forEach((b) => b.addEventListener('click', () => {
      p.activeProvider = b.dataset.useProv;
      renderSettings();
      toast('已切换默认视觉模型');
    }));
    $$('[data-test-prov]', root).forEach((b) => b.addEventListener('click', () => {
      const pr = p.providers.find((x) => x.id === b.dataset.testProv);
      b.textContent = '…';
      setTimeout(() => { b.textContent = '连通'; toast(`${pr.name} 鉴权与视觉能力正常`); }, 300);
    }));
    $('#btn-add-prov', root)?.addEventListener('click', () => {
      $('#inp-pv-name').value = ''; $('#inp-pv-url').value = ''; $('#inp-pv-model').value = '';
      $('#dlg-provider').showModal();
    });
    $('#btn-takeover-sim', root)?.addEventListener('click', () => {
      p.takeoverActive = !p.takeoverActive;
      renderSettings();
    });
    $('#btn-pen-reset', root)?.addEventListener('click', () => {
      p.penMap = { pTap: '套索', pDbl: '笔 ⇄ 橡皮', pLong: '框选提问', pHold: '套索', sTap: '切换上一支笔', sHold: '临时橡皮', proPinch: '展开笔盒', proSlide: '调节笔宽' };
      renderSettings();
      toast('已恢复默认按键映射');
    });
    $('#btn-empty-trash', root)?.addEventListener('click', () => {
      state.notebooks = state.notebooks.filter((n) => !n.trashed);
      toast('回收站已清空');
    });
    $('#btn-clr-cache', root)?.addEventListener('click', () => {
      p.cacheMb = '0 MB';
      $('#set-cache-sz', root).textContent = '0 MB';
      toast('缩略图缓存已清理');
    });
    $('#inp-lan-url', root)?.addEventListener('input', (e) => { p.lanUrl = e.target.value; });
    $('#btn-s-diag', root)?.addEventListener('click', () => $('#dlg-diag').showModal());
    $('#btn-s-upd', root)?.addEventListener('click', () => toast('已是最新版本'));
  }

  function renderExport() {
    const base = $('#exp-name').value.trim() || curNb().name;
    const ext = state.expFmt === 'milk' ? 'milk.zip' : state.expFmt;
    $('#exp-path').textContent = `${state.expFmt === 'png' ? '图片' : '文档'}/MilkNote/${base}.${ext}`;
    $$('[data-fmt-m]').forEach((m) => { m.textContent = m.dataset.fmtM === state.expFmt ? '✓' : ''; });
    $('#exp-thumbs').innerHTML = [1, 2, 3, 4].map((i) => `
      <button type="button" class="thumb-item ${state.expPicked.has(i) ? 'active' : ''}" data-ep="${i}">
        <div class="thumb-paper surface-always-light" style="background:#fff"><span class="mono" style="font-size:10px;color:#2563eb">p.${i}</span></div>
        <span class="thumb-num mono">${i}</span>
      </button>
    `).join('');
  }

  /* ---- Command Palette ---- */
  let palIdx = -1;
  function palItems(q) {
    const s = q.trim().toLowerCase();
    const all = [
      { l: '框选提问', h: 'Alt+S', f: () => { go('writer'); startSnip(); } },
      { l: state.split ? '关闭双开分屏' : '双开分屏', h: '书写', f: () => { state.split = !state.split; go('writer'); } },
      { l: '新建笔记本', h: '书架', f: openNewDlg },
      { l: '知识卡片', h: `${state.cards.length}`, f: () => { state.nav = 'cards'; go('library'); } },
      { l: '导出', h: 'PDF / PNG', f: () => go('export') },
      { l: '设置', h: '', f: () => go('settings') },
      ...state.notebooks.filter((n) => !n.trashed).map((n) => ({ l: n.name, h: n.folder || '', f: () => openNb(n.id) })),
    ];
    return s ? all.filter((x) => x.l.toLowerCase().includes(s) || x.h.toLowerCase().includes(s)) : all;
  }

  function renderPal(kbd = false) {
    const items = palItems($('#pal-inp').value);
    if (!kbd) palIdx = -1;
    else if (palIdx >= items.length) palIdx = items.length - 1;
    $('#pal-list').innerHTML = items.length ? items.map((x, i) => `
      <button type="button" class="pal-row ${i === palIdx ? 'sel' : ''}" data-pi="${i}"><span>${esc(x.l)}</span><span class="mono" style="font-size:11px;color:var(--ink-3)">${esc(x.h)}</span></button>
    `).join('') : `<div style="padding:20px;text-align:center;color:var(--ink-3);font-size:13px">无匹配结果</div>`;
    $$('[data-pi]').forEach((b) => b.addEventListener('click', () => { const c = items[Number(b.dataset.pi)]; closeModal($('#dlg-pal')); c?.f(); }));
  }

  function openPal(kbd = false) {
    $('#pal-inp').value = '';
    palIdx = kbd ? 0 : -1;
    $('#dlg-pal').showModal();
    renderPal(kbd);
    $('#pal-inp').focus();
  }

  /* ---- Snip Overlay ---- */
  function startSnip() {
    $('#snip-layer').classList.add('active');
    $('#snip-rect').style.display = 'none';
    $('#snip-bar').classList.remove('visible');
    state.snipRect = null;
  }
  function stopSnip() { $('#snip-layer').classList.remove('active'); }

  /* ---- New Notebook Dialog ---- */
  const newDraft = { mode: 'paged', cover: '#2563EB', paper: 'dot' };
  function updateNewPrev() {
    $('#new-name-prev').textContent = $('#inp-new-name').value.trim() || '未命名';
    $('#new-spine').style.background = newDraft.cover;
    const bg = {
      dot: 'radial-gradient(rgba(24,25,27,0.18) 1px,transparent 1px)',
      line: 'repeating-linear-gradient(to bottom,transparent 0,transparent 15px,rgba(37,99,235,0.16) 15px,rgba(37,99,235,0.16) 16px)',
      grid: 'linear-gradient(to right,rgba(24,25,27,0.08) 1px,transparent 1px),linear-gradient(to bottom,rgba(24,25,27,0.08) 1px,transparent 1px)',
      cornell: 'linear-gradient(to right,transparent 25%,rgba(217,68,54,0.3) 25%,rgba(217,68,54,0.3) 26%,transparent 26%)',
      tian: 'linear-gradient(to right,rgba(35,134,95,0.22) 1px,transparent 1px),linear-gradient(to bottom,rgba(35,134,95,0.22) 1px,transparent 1px)',
      blank: 'none',
    };
    const p = $('#new-pat-prev');
    p.style.backgroundImage = bg[newDraft.paper] || 'none';
    p.style.backgroundSize = newDraft.paper === 'dot' ? '14px 14px' : newDraft.paper === 'grid' ? '16px 16px' : newDraft.paper === 'tian' ? '26px 26px' : 'auto';
  }
  function openNewDlg() { $('#inp-new-name').value = ''; updateNewPrev(); $('#dlg-new').showModal(); $('#inp-new-name').focus(); }

  /* ---- Event Wiring ---- */
  function wire() {
    wireModals();
    wirePopovers();
    wireToolbarFallback($('#ink-dock'));
    wireCanvas($('#cv-main'), false);
    wireCanvas($('#cv-split'), true);

    // Corner switcher
    $('#btn-mode-app').addEventListener('click', () => go(state.lastAppScreen));
    $('#btn-mode-icons').addEventListener('click', () => go('icons'));
    $('#btn-theme').addEventListener('click', toggleScheme);

    // Library
    $('#btn-rail-hide').addEventListener('click', () => { $('#lib-shell').classList.add('rail-hidden'); $('#btn-rail-show').style.display = 'inline-grid'; });
    $('#btn-rail-show').addEventListener('click', () => { $('#lib-shell').classList.remove('rail-hidden'); $('#btn-rail-show').style.display = 'none'; });
    $('#btn-shelf-search').addEventListener('click', () => openPal(false));
    $('#btn-view-mode').addEventListener('click', (e) => { state.listView = !state.listView; e.currentTarget.setAttribute('aria-pressed', String(state.listView)); renderLibrary(); });
    $('#btn-foot-pen').addEventListener('click', () => go('settings', { tab: 'pen' }));
    $('#btn-foot-settings').addEventListener('click', () => go('settings'));
    $('#btn-add-folder').addEventListener('click', () => { const f = `文件夹 ${state.folders.length + 1}`; state.folders.push(f); state.nav = `folder:${f}`; renderLibrary(); });
    $('#btn-new-main').addEventListener('click', () => {
      if (state.nav === 'cards') { $('#inp-c-title').value = ''; $('#inp-c-body').value = ''; $('#dlg-card').showModal(); }
      else openNewDlg();
    });

    $('#scr-library').addEventListener('click', (e) => {
      const nav = e.target.closest('[data-nav]');
      if (nav) { state.nav = nav.dataset.nav; renderLibrary(); return; }
      const op = e.target.closest('[data-open]');
      if (op) { openNb(op.dataset.open); return; }
      const bm = e.target.closest('[data-bmenu]');
      if (bm) {
        e.stopPropagation();
        state.ctxNbId = bm.dataset.bmenu;
        const nb = state.notebooks.find((n) => n.id === state.ctxNbId);
        if (nb) { $('#bact-pin').textContent = nb.pinned ? '取消星标' : '星标'; $('#bact-trash').textContent = nb.trashed ? '恢复' : '移入回收站'; }
        const pop = $('#pop-book'); pop._anchor = bm; lastAnchor = bm;
        if (typeof pop.showPopover === 'function') pop.showPopover({ source: bm });
        return;
      }
      if (e.target.closest('#btn-slot-new')) { openNewDlg(); return; }
      const ic = e.target.closest('[data-ins-c]');
      if (ic) { const c = state.cards.find((x) => x.id === ic.dataset.insC); if (c) { go('writer'); addPin(c.title, c.body, c.color); } return; }
      const dc = e.target.closest('[data-del-c]');
      if (dc) { state.cards = state.cards.filter((x) => x.id !== dc.dataset.delC); renderLibrary(); }
    });

    $$('[data-sort]').forEach((b) => b.addEventListener('click', () => {
      state.sort = b.dataset.sort;
      $$('[data-sm]').forEach((m) => { m.textContent = m.dataset.sm === state.sort ? '✓' : ''; });
      hidePop('pop-sort'); renderLibrary();
    }));

    $$('[data-bact]').forEach((b) => b.addEventListener('click', () => {
      hidePop('pop-book');
      const nb = state.notebooks.find((n) => n.id === state.ctxNbId);
      if (!nb) return;
      const a = b.dataset.bact;
      if (a === 'split') openNb(nb.id, true);
      else if (a === 'pin') { nb.pinned = !nb.pinned; renderLibrary(); }
      else if (a === 'rename') { $('#inp-ren-name').value = nb.name; $('#inp-ren-folder').value = nb.folder; $('#dlg-rename').showModal(); }
      else if (a === 'export') { state.activeNbId = nb.id; go('export'); }
      else if (a === 'trash') { nb.trashed = !nb.trashed; renderLibrary(); toast(nb.trashed ? '已移入回收站' : '已恢复'); }
    }));

    // Writer bar
    $('#btn-w-back').addEventListener('click', () => go('library'));
    $('#btn-w-left').addEventListener('click', () => { state.leftOpen = !state.leftOpen; renderLeft(); requestAnimationFrame(sizeCanvases); });
    $('#w-tabs').addEventListener('click', (e) => {
      const xt = e.target.closest('[data-xtab]');
      if (xt) {
        e.stopPropagation();
        state.openTabs = state.openTabs.filter((x) => x !== xt.dataset.xtab);
        if (state.activeNbId === xt.dataset.xtab && state.openTabs.length) state.activeNbId = state.openTabs[0];
        renderTabs(); return;
      }
      if (e.target.closest('#btn-tab-plus')) { openNewDlg(); return; }
      const tb = e.target.closest('[data-tab]');
      if (tb) openNb(tb.dataset.tab);
    });
    $('#btn-w-split').addEventListener('click', () => { state.split = !state.split; renderTabs(); requestAnimationFrame(sizeCanvases); });
    $('#btn-w-rec').addEventListener('click', () => {
      state.rec = !state.rec;
      const b = $('#btn-w-rec');
      b.classList.toggle('on', state.rec);
      b.setAttribute('aria-pressed', String(state.rec));
      if (state.rec) {
        state.recSec = 0; $('#rec-time').textContent = '00:00';
        state.recTid = setInterval(() => {
          state.recSec++;
          $('#rec-time').textContent = `${String(Math.floor(state.recSec / 60)).padStart(2, '0')}:${String(state.recSec % 60).padStart(2, '0')}`;
        }, 1000);
      } else { clearInterval(state.recTid); $('#rec-time').textContent = ''; toast('录音已保存'); }
    });
    $('#btn-w-focus').addEventListener('click', () => { state.focus = true; $('#writer-shell').classList.add('is-focus'); document.body.classList.add('focus-hidden'); });
    $('#btn-exit-focus').addEventListener('click', () => { state.focus = false; $('#writer-shell').classList.remove('is-focus'); document.body.classList.remove('focus-hidden'); });
    $('#btn-w-studio').addEventListener('click', () => openStudio(!state.studioOpen));
    $('#btn-studio-x').addEventListener('click', () => openStudio(false));

    // Left drawer
    $$('[data-ltab]').forEach((b) => b.addEventListener('click', () => { state.leftTab = b.dataset.ltab; renderLeft(); }));
    $('#left-drawer-body').addEventListener('click', (e) => {
      const p = e.target.closest('[data-gop]');
      if (p) { state.page = Number(p.dataset.gop); renderTabs(); renderLeft(); renderMd(); renderPins(); drawMain(); return; }
      if (e.target.closest('#btn-add-pg')) { state.totalPages++; strokes[state.totalPages] = []; redos[state.totalPages] = []; state.pins[state.totalPages] = []; state.page = state.totalPages; renderTabs(); renderLeft(); renderMd(); drawMain(); return; }
      const d = e.target.closest('[data-dropc]');
      if (d) { const c = state.cards.find((x) => x.id === d.dataset.dropc); if (c) addPin(c.title, c.body, c.color); }
    });

    $('#btn-p-prev').addEventListener('click', () => { if (state.page > 1) { state.page--; renderTabs(); renderLeft(); renderMd(); renderPins(); drawMain(); } });
    $('#btn-p-next').addEventListener('click', () => { if (state.page < state.totalPages) { state.page++; renderTabs(); renderLeft(); renderMd(); renderPins(); drawMain(); } });

    // Floating Ink Bar
    const dockSeq = ['top', 'bottom', 'left', 'right'];
    $('#btn-dock-move').addEventListener('click', () => {
      state.dock = dockSeq[(dockSeq.indexOf(state.dock) + 1) % 4];
      $('#writer-shell').dataset.dock = state.dock;
      const vert = state.dock === 'left' || state.dock === 'right';
      const dk = $('#ink-dock');
      dk.setAttribute('aria-orientation', vert ? 'vertical' : 'horizontal');
      dk.setAttribute('focusgroup', vert ? 'toolbar wrap block' : 'toolbar wrap');
      $$('.dock-div', dk).forEach((s) => s.setAttribute('aria-orientation', vert ? 'horizontal' : 'vertical'));
      requestAnimationFrame(sizeCanvases);
    });
    $('#btn-undo').addEventListener('click', (e) => { if (e.currentTarget.getAttribute('aria-disabled') === 'true') return; const a = strokes[state.page]; if (a.length) { redos[state.page].push(a.pop()); drawMain(); } });
    $('#btn-redo').addEventListener('click', (e) => { if (e.currentTarget.getAttribute('aria-disabled') === 'true') return; const a = redos[state.page]; if (a.length) { strokes[state.page].push(a.pop()); drawMain(); } });
    $$('.dock-tool[data-tool]').forEach((b) => b.addEventListener('click', () => {
      state.tool = b.dataset.tool;
      $$('.dock-tool[data-tool]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      applySheet(); renderPenPop();
    }));
    $$('.color-dot[data-ink]').forEach((b) => b.addEventListener('click', () => {
      state.ink = b.dataset.ink;
      $$('.color-dot[data-ink]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      renderPenPop();
    }));
    $$('.stroke-bar[data-w]').forEach((b) => b.addEventListener('click', () => {
      state.width = Number(b.dataset.w);
      $$('.stroke-bar[data-w]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      renderPenPop();
    }));
    $('#btn-pen-pop').addEventListener('click', renderPenPop);
    $('#rng-w').addEventListener('input', (e) => { state.width = Number(e.target.value); renderPenPop(); });
    $('#rng-s').addEventListener('input', (e) => { state.stab = Number(e.target.value); renderPenPop(); });
    $('#btn-add-penbox').addEventListener('click', () => { state.penBox.push({ name: `预设 ${state.penBox.length + 1}`, tool: state.tool, color: state.ink, width: state.width }); renderPenPop(); });
    $('#penbox-list').addEventListener('click', (e) => {
      const p = e.target.closest('[data-pbox]');
      if (!p) return;
      const item = state.penBox[Number(p.dataset.pbox)];
      state.tool = item.tool; state.ink = item.color; state.width = item.width;
      $$('.dock-tool[data-tool]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.tool === item.tool)));
      renderPenPop(); hidePop('pop-pen');
    });
    $('#btn-snip').addEventListener('click', startSnip);

    // Writer More
    $$('[data-pat]').forEach((b) => b.addEventListener('click', () => { state.pattern = b.dataset.pat; applySheet(); }));
    $$('[data-sbg]').forEach((b) => b.addEventListener('click', () => { state.sheetBg = b.dataset.sbg; applySheet(); }));
    $('#btn-m-margin').addEventListener('click', () => { state.margin = !state.margin; applySheet(); });
    $('#btn-m-clear').addEventListener('click', () => { strokes[state.page] = []; redos[state.page] = []; drawMain(); hidePop('pop-w-more'); });
    $('#btn-m-export').addEventListener('click', () => { hidePop('pop-w-more'); go('export'); });
    $('#btn-m-diag').addEventListener('click', () => { hidePop('pop-w-more'); $('#dlg-diag').showModal(); });

    // Snip drag
    const sl = $('#snip-layer'), sr = $('#snip-rect'), sd = $('#snip-dim'), sb = $('#snip-bar');
    let sdg = false, sx = 0, sy = 0;
    sl.addEventListener('pointerdown', (e) => {
      if (e.target.closest('button')) return;
      sdg = true; const r = sl.getBoundingClientRect(); sx = e.clientX - r.left; sy = e.clientY - r.top;
      sb.classList.remove('visible'); sr.style.display = 'block';
      sr.style.left = `${sx}px`; sr.style.top = `${sy}px`; sr.style.width = '0'; sr.style.height = '0';
    });
    sl.addEventListener('pointermove', (e) => {
      if (!sdg) return;
      const r = sl.getBoundingClientRect();
      const cx = Math.max(0, Math.min(r.width, e.clientX - r.left)), cy = Math.max(0, Math.min(r.height, e.clientY - r.top));
      const x = Math.min(sx, cx), y = Math.min(sy, cy), w = Math.abs(cx - sx), h = Math.abs(cy - sy);
      sr.style.left = `${x}px`; sr.style.top = `${y}px`; sr.style.width = `${w}px`; sr.style.height = `${h}px`;
      sd.style.top = y < 40 ? '8px' : '-30px'; sd.textContent = `${Math.round(w * 2)} × ${Math.round(h * 2)}`;
      state.snipRect = { w: Math.round(w * 2), h: Math.round(h * 2) };
    });
    sl.addEventListener('pointerup', () => {
      if (!sdg) return; sdg = false;
      if (!state.snipRect || state.snipRect.w < 40) { sr.style.left = '14%'; sr.style.top = '24%'; sr.style.width = '46%'; sr.style.height = '32%'; state.snipRect = { w: 640, h: 300 }; sd.textContent = '640 × 300'; }
      sb.classList.add('visible');
    });
    $$('.snip-opt[data-intent]').forEach((b) => b.addEventListener('click', () => {
      state.snipIntent = b.dataset.intent;
      $$('.snip-opt[data-intent]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    }));
    $('#btn-snip-x').addEventListener('click', stopSnip);
    $('#btn-snip-card').addEventListener('click', () => {
      state.cards.unshift({ id: `c-${Date.now()}`, title: `框选摘录 · p.${state.page}`, formula: 'det(A − λI) = 0 ⇒ λ₁ = 3, λ₂ = 1', body: `${curNb().name} 第 ${state.page} 页手写选区`, color: '#2563EB', source: `${curNb().name} · p.${state.page}` });
      stopSnip(); renderLeft(); toast('已存入卡片');
    });
    $('#btn-snip-go').addEventListener('click', () => {
      const sz = state.snipRect ? `${state.snipRect.w}×${state.snipRect.h}` : '640×300';
      stopSnip(); openStudio(true, 'ai');
      askAi(`${state.snipIntent}这段选区`, state.snipIntent, `snip_${sz}.png`);
    });

    // Right studio & resize
    $$('[data-rtab]').forEach((b) => b.addEventListener('click', () => openStudio(true, b.dataset.rtab)));
    let rsz = false, rx = 0, rw = 348;
    $('#resize-edge').addEventListener('pointerdown', (e) => { rsz = true; rx = e.clientX; rw = $('#right-studio').getBoundingClientRect().width; e.currentTarget.setPointerCapture(e.pointerId); });
    $('#resize-edge').addEventListener('pointermove', (e) => { if (!rsz) return; $('#right-studio').style.width = `${Math.max(280, Math.min(460, rw - (e.clientX - rx)))}px`; sizeCanvases(); });
    $('#resize-edge').addEventListener('pointerup', () => { rsz = false; });

    $$('[data-ai-q]').forEach((b) => b.addEventListener('click', () => {
      const q = b.dataset.aiQ;
      if (q === 'snip') startSnip();
      else if (q === 'explain') askAi('讲解本页例 2', '讲解', `p.${state.page}`);
      else askAi('归纳本章要点', '归纳', '4 页');
    }));
    $('#ai-inp').addEventListener('input', renderDrafts);
    $('#ai-inp').addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); if (!$('#btn-ai-send').disabled) $('#btn-ai-send').click(); } });
    $('#btn-ai-send').addEventListener('click', () => {
      const v = $('#ai-inp').value.trim() || '讲解附件';
      $('#ai-inp').value = '';
      askAi(v, v.includes('LaTeX') ? '转 LaTeX' : v.includes('归纳') ? '归纳' : '讲解');
    });
    $$('[data-att]').forEach((b) => b.addEventListener('click', () => {
      if (!state.draftAtts.includes(b.dataset.att)) { state.draftAtts.push(b.dataset.att); renderDrafts(); }
      hidePop('pop-attach');
    }));
    $('#ai-drafts').addEventListener('click', (e) => { const rm = e.target.closest('[data-rma]'); if (rm) { state.draftAtts.splice(Number(rm.dataset.rma), 1); renderDrafts(); } });
    $('#ai-msgs').addEventListener('click', (e) => {
      const p = e.target.closest('[data-pinm]');
      if (p) { const m = state.aiMsgs.find((x) => x.id === p.dataset.pinm); if (m) { addPin('AI 批注', m.r.pin); toast('已贴回纸面'); } return; }
      const c = e.target.closest('[data-cardm]');
      if (c) { const m = state.aiMsgs.find((x) => x.id === c.dataset.cardm); if (m) { state.cards.unshift({ id: `c-${Date.now()}`, title: 'AI 笔记摘录', formula: m.r.math.split('\n')[0], body: m.r.pin, color: '#2563EB', source: `${curNb().name} · p.${state.page}` }); renderLeft(); toast('已存为卡片'); } return; }
      const cp = e.target.closest('[data-copym]');
      if (cp) { const m = state.aiMsgs.find((x) => x.id === cp.dataset.copym); if (m) navigator.clipboard?.writeText(m.r.body + '\n' + m.r.math).then(() => toast('已复制')).catch(() => {}); }
    });

    // Pins drag & remove
    let dp = null;
    $('#pins-layer').addEventListener('pointerdown', (e) => {
      const rm = e.target.closest('[data-rmp]');
      if (rm) { state.pins[state.page] = state.pins[state.page].filter((x) => x.id !== rm.dataset.rmp); renderPins(); return; }
      const h = e.target.closest('[data-dragp]');
      if (!h) return;
      const item = state.pins[state.page].find((x) => x.id === h.dataset.dragp);
      if (item) { dp = { item, sx: e.clientX, sy: e.clientY, ox: item.x, oy: item.y }; h.setPointerCapture(e.pointerId); }
    });
    $('#pins-layer').addEventListener('pointermove', (e) => {
      if (!dp) return;
      dp.item.x = Math.max(8, dp.ox + e.clientX - dp.sx);
      dp.item.y = Math.max(8, dp.oy + e.clientY - dp.sy);
      const el = $(`[data-pid="${dp.item.id}"]`);
      if (el) { el.style.left = `${dp.item.x}px`; el.style.top = `${dp.item.y}px`; }
    });
    $('#pins-layer').addEventListener('pointerup', () => { dp = null; });

    // Markdown & Links
    $('#btn-md-toggle').addEventListener('click', () => { if (state.mdEdit) state.mdByPage[state.page] = $('#md-raw').value; state.mdEdit = !state.mdEdit; renderMd(); });
    $('#md-raw').addEventListener('input', (e) => { state.mdByPage[state.page] = e.target.value; });
    $('#btn-md-card').addEventListener('click', () => { state.cards.unshift({ id: `c-${Date.now()}`, title: `Markdown · p.${state.page}`, formula: null, body: (state.mdByPage[state.page] || '').slice(0, 90), color: '#23865F', source: `${curNb().name} · p.${state.page}` }); toast('已存入卡片'); });
    $('#md-view').addEventListener('click', (e) => { if (e.target.closest('[data-wl]')) { state.split = true; renderTabs(); requestAnimationFrame(sizeCanvases); } });
    $('#links-body').addEventListener('click', (e) => {
      if (e.target.closest('[data-lsplit]')) { state.split = true; renderTabs(); requestAnimationFrame(sizeCanvases); return; }
      const dc = e.target.closest('[data-dropc]');
      if (dc) { const c = state.cards.find((x) => x.id === dc.dataset.dropc); if (c) addPin(c.title, c.body, c.color); }
    });

    // Settings & Export
    $('#btn-set-back').addEventListener('click', () => go('library'));
    $$('[data-stab]').forEach((b) => b.addEventListener('click', () => { state.setTab = b.dataset.stab; state.setQ = ''; $('#set-search').value = ''; renderSettings(); }));
    $('#set-search').addEventListener('input', (e) => { state.setQ = e.target.value; renderSettings(); });
    $('#btn-exp-back').addEventListener('click', () => go('writer'));
    $$('[data-fmt]').forEach((r) => r.addEventListener('click', () => { state.expFmt = r.dataset.fmt; renderExport(); }));
    $('#exp-name').addEventListener('input', renderExport);
    $('#exp-thumbs').addEventListener('click', (e) => { const b = e.target.closest('[data-ep]'); if (!b) return; const n = Number(b.dataset.ep); if (state.expPicked.has(n)) state.expPicked.delete(n); else state.expPicked.add(n); renderExport(); });
    $('#btn-exp-go').addEventListener('click', () => { $('#exp-msg').textContent = `已导出至 ${$('#exp-path').textContent}`; });

    // Dialogs: New / Rename / Card / Diag / Palette
    $('#inp-new-name').addEventListener('input', updateNewPrev);
    $$('[data-nmode]').forEach((b) => b.addEventListener('click', () => { newDraft.mode = b.dataset.nmode; $$('[data-nmode]').forEach((x) => x.classList.toggle('active', x === b)); updateNewPrev(); }));
    $$('[data-ncov]').forEach((b) => b.addEventListener('click', () => { newDraft.cover = b.dataset.ncov; $$('[data-ncov]').forEach((x) => x.setAttribute('aria-pressed', String(x === b))); updateNewPrev(); }));
    $$('[data-npap]').forEach((b) => b.addEventListener('click', () => { newDraft.paper = b.dataset.npap; $$('[data-npap]').forEach((x) => x.classList.toggle('active', x === b)); updateNewPrev(); }));
    $('#btn-create-go').addEventListener('click', () => {
      const name = $('#inp-new-name').value.trim() || '未命名';
      const id = `nb-${Date.now()}`;
      state.notebooks.unshift({ id, name, cover: newDraft.cover, paper: newDraft.paper, sheetBg: '#FFFFFF', mode: newDraft.mode, folder: state.nav.startsWith('folder:') ? state.nav.slice(7) : '', pages: 1, pinned: false, isPdf: false, trashed: false, updated: '刚刚', uTs: Date.now(), oTs: Date.now(), art: newDraft.mode === 'infinite' ? 'canvas' : newDraft.paper === 'cornell' ? 'cornell' : 'paper' });
      closeModal($('#dlg-new')); openNb(id);
    });
    $('#btn-ren-save').addEventListener('click', () => {
      const nb = state.notebooks.find((n) => n.id === state.ctxNbId);
      if (nb) { nb.name = $('#inp-ren-name').value.trim() || nb.name; nb.folder = $('#inp-ren-folder').value.trim(); if (nb.folder && !state.folders.includes(nb.folder)) state.folders.push(nb.folder); renderLibrary(); }
      closeModal($('#dlg-rename'));
    });
    $('#btn-ren-trash').addEventListener('click', () => { const nb = state.notebooks.find((n) => n.id === state.ctxNbId); if (nb) { nb.trashed = true; renderLibrary(); } closeModal($('#dlg-rename')); });
    $('#btn-c-save').addEventListener('click', () => {
      state.cards.unshift({ id: `c-${Date.now()}`, title: $('#inp-c-title').value.trim() || '卡片', formula: null, body: $('#inp-c-body').value.trim(), color: '#2563EB', source: '刚刚' });
      closeModal($('#dlg-card')); renderLibrary();
    });
    $('#btn-pv-save').addEventListener('click', () => {
      const name = $('#inp-pv-name').value.trim() || '自定义端点';
      const model = $('#inp-pv-model').value.trim() || 'vision-model';
      const proto = $('#inp-pv-proto').value;
      const id = `pv-${Date.now()}`;
      state.prefs.providers.push({ id, name, model, proto, ctx: '自定义', ok: true });
      state.prefs.activeProvider = id;
      closeModal($('#dlg-provider'));
      renderSettings();
      toast(`已添加 ${name}`);
    });
    $('#diag-area').addEventListener('pointerdown', (e) => {
      const r = e.currentTarget.getBoundingClientRect();
      const d = document.createElement('div');
      d.innerHTML = `<span style="color:#5b92ff">[DOWN]</span> ${e.pointerType} (${Math.round(e.clientX - r.left)},${Math.round(e.clientY - r.top)}) p=${(e.pressure || 0.5).toFixed(2)}`;
      $('#diag-lines').appendChild(d); $('#diag-lines').scrollTop = 9999;
    });
    $('#btn-diag-clr').addEventListener('click', () => { $('#diag-lines').innerHTML = ''; });

    $('#pal-inp').addEventListener('input', () => renderPal(true));
    $('#pal-inp').addEventListener('keydown', (e) => {
      const items = palItems($('#pal-inp').value);
      if (e.key === 'ArrowDown' && items.length) { e.preventDefault(); palIdx = (palIdx + 1) % items.length; renderPal(true); }
      else if (e.key === 'ArrowUp' && items.length) { e.preventDefault(); palIdx = (palIdx - 1 + items.length) % items.length; renderPal(true); }
      else if (e.key === 'Enter' && items.length) { e.preventDefault(); const c = items[Math.max(0, palIdx)]; closeModal($('#dlg-pal')); c?.f(); }
    });

    // Icon Preview
    $$('[data-pick-icon]').forEach((b) => b.addEventListener('click', () => {
      $$('[data-pick-icon]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
      const src = b.dataset.pickIcon;
      if (src === 'old') {
        const svgUrl = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" fill="#3B5BEC"/><path fill="#FAF9F5" d="M24,13 C24,13 31,22 31,28 A7,7 0 0,1 17,28 C17,22 24,13 24,13 Z"/></svg>');
        $('#dock-live-icon').src = svgUrl; $('#brand-icon-img').src = svgUrl;
      } else {
        $('#dock-live-icon').src = src; $('#brand-icon-img').src = src;
      }
    }));
    $$('[data-mask-set]').forEach((b) => b.addEventListener('click', () => {
      $$('[data-mask-set]').forEach((x) => x.classList.toggle('active', x === b));
      $$('.icon-frame').forEach((f) => { f.dataset.mask = b.dataset.maskSet; });
    }));
    $$('[data-wall-set]').forEach((b) => b.addEventListener('click', () => {
      $$('[data-wall-set]').forEach((x) => x.classList.toggle('active', x === b));
      $('#pad-wall').dataset.wall = b.dataset.wallSet;
    }));

    document.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); openPal(true); }
      else if (e.altKey && e.key.toLowerCase() === 's') { e.preventDefault(); go('writer'); startSnip(); }
      else if (e.key === 'Escape' && $('#snip-layer').classList.contains('active')) stopSnip();
    });
    window.addEventListener('resize', () => { if (state.screen === 'writer') sizeCanvases(); });
  }

  wire();
  renderLibrary();
  renderPenPop();
  applySheet();
})();
