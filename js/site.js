import { categories, scenarios, products, siteStats, filterProducts, getProductById, wrapIndex } from './data.js?v=featured-360';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[char]));
const query = new URLSearchParams(window.location.search);

function renderSharedChrome() {
  const header = $('[data-site-header]');
  const footer = $('[data-site-footer]');
  const page = document.body.dataset.page || '';
  const links = [
    ['首页', 'index.html', 'home'], ['产品中心', 'products.html', 'products'], ['应用场景', 'solutions.html', 'solutions'],
    ['渠道合作', 'partners.html', 'partners'], ['品牌实力', 'about.html', 'about'], ['资讯中心', 'news.html', 'news'], ['服务支持', 'support.html', 'support'],
  ];
  if (header) {
    header.innerHTML = `<div class="container nav"><a class="brand" href="index.html" aria-label="沃尔森光电首页"><img src="assets/logo-mark.svg" alt="" width="28" height="28"><span>WARSUN</span></a><nav class="nav-links" aria-label="主导航">${links.map(([label, href, key]) => `<a href="${href}" ${page === key ? 'aria-current="page"' : ''}>${label}</a>`).join('')}</nav><div class="nav-actions"><a class="nav-cta" href="contact.html?interest=经销商合作">成为经销商</a><button class="menu-toggle" type="button" aria-expanded="false" aria-controls="main-nav" aria-label="打开菜单">☰</button></div></div>`;
    const nav = $('.nav-links', header); nav.id = 'main-nav';
    const toggle = $('.menu-toggle', header);
    toggle.addEventListener('click', () => { const open = nav.classList.toggle('is-open'); toggle.setAttribute('aria-expanded', String(open)); toggle.textContent = open ? '×' : '☰'; });
    $$('a', nav).forEach((link) => link.addEventListener('click', () => { nav.classList.remove('is-open'); toggle.setAttribute('aria-expanded', 'false'); toggle.textContent = '☰'; }));
    window.addEventListener('scroll', () => header.classList.toggle('is-scrolled', window.scrollY > 16), { passive: true });
  }
  if (footer) footer.innerHTML = `<div class="container"><div class="footer-grid"><div><a class="brand" href="index.html"><img src="assets/logo-mark.svg" alt="" width="28" height="28"><span>WARSUN</span></a><p style="max-width:320px;margin-top:1rem">照亮世界每个角落。沃尔森专注移动照明产品，为户外、工业与应急场景提供可靠光源。</p></div><div><h3>探索沃尔森</h3><div class="footer-links"><a href="products.html">产品中心</a><a href="solutions.html">应用场景</a><a href="about.html">品牌实力</a></div></div><div><h3>合作与支持</h3><div class="footer-links"><a href="partners.html">渠道合作</a><a href="support.html">服务支持</a><a href="contact.html">联系我们</a></div></div><div><h3>快速联系</h3><div class="footer-links"><a href="contact.html?interest=获取产品目录">获取产品目录</a><a href="contact.html?interest=获取报价">获取报价</a><a href="contact.html?interest=经销商合作">成为经销商</a></div></div></div><div class="footer-bottom"><span>© 2024 沃尔森光电科技有限公司</span><span>移动照明 · 研发 · 制造 · 服务</span></div></div>`;
}

function productCard(product) {
  return `<article class="product-card reveal"><a href="product.html?id=${encodeURIComponent(product.id)}"><div class="product-image"><img src="${product.image}" alt="${escapeHtml(product.name)}"></div></a><div class="product-body"><div class="product-meta"><span>${escapeHtml(product.category)}</span><span>${product.featured ? '精选' : '系列'}</span></div><h3><a href="product.html?id=${encodeURIComponent(product.id)}">${escapeHtml(product.name)}</a></h3><p>${escapeHtml(product.tagline)}</p><div class="product-actions"><a class="text-link" href="product.html?id=${encodeURIComponent(product.id)}">查看详情 →</a><a class="btn btn-ghost" href="contact.html?product=${encodeURIComponent(product.id)}&interest=获取报价">询价</a></div></div></article>`;
}

function renderHome() {
  const categoryGrid = $('#category-grid');
  if (categoryGrid) categoryGrid.innerHTML = categories.map((item) => `<a class="category-card" href="products.html?category=${encodeURIComponent(item.name)}"><span class="card-icon">${item.icon}</span><h3>${item.name}</h3><p>${item.description}</p><span class="card-arrow">↗</span></a>`).join('');
  const featured = $('#featured-products');
  if (featured) featured.innerHTML = products.filter((item) => item.featured).map(productCard).join('');
  const rail = $('#product-rail');
  if (rail) {
    rail.innerHTML = products.map((product) => `<article class="rail-card"><a href="product.html?id=${encodeURIComponent(product.id)}"><div class="rail-image"><img src="${product.image}" alt="${escapeHtml(product.name)}"></div><div class="rail-copy"><span>${escapeHtml(product.category)}</span><h3>${escapeHtml(product.name)}</h3><p>${escapeHtml(product.tagline)}</p><strong>探索产品 ↗</strong></div></a></article>`).join('');
    const step = () => Math.min(rail.clientWidth * .82, 520);
    $('[data-rail-prev]')?.addEventListener('click', () => rail.scrollBy({ left: -step(), behavior: 'smooth' }));
    $('[data-rail-next]')?.addEventListener('click', () => rail.scrollBy({ left: step(), behavior: 'smooth' }));
  }
  initFeaturedShowcase();
  const scenarioGrid = $('#scenario-grid');
  if (scenarioGrid) scenarioGrid.innerHTML = scenarios.slice(0, 6).map((item) => `<a class="scenario-card" href="solutions.html#${encodeURIComponent(item.name)}"><span class="card-icon">${item.icon}</span><h3>${item.name}</h3><p>${item.description}</p><span class="card-arrow">↗</span></a>`).join('');
  const stats = $('#stats');
  if (stats) stats.innerHTML = siteStats.map((item) => `<div class="stat"><strong>${item.value}</strong><span>${item.label}</span></div>`).join('');
}

function initFeaturedShowcase() {
  const viewer = $('#featured-viewer');
  const thumbs = $('#featured-thumbs');
  if (!viewer || !thumbs) return;
  const featuredProducts = products.filter((product) => product.featured).slice(0, 3);
  let activeIndex = 0;
  let rotation = 0;
  let tilt = 0;
  let dragStart = null;
  let dragStartY = null;
  let dragDistance = 0;

  const draw = () => {
    const product = featuredProducts[activeIndex];
    viewer.classList.remove('is-immersive');
    viewer.innerHTML = `<div class="featured-product-copy"><span>${escapeHtml(product.category)} / FEATURED</span><h3>${escapeHtml(product.name)}</h3><p>${escapeHtml(product.description)}</p><ul>${product.features.map((feature) => `<li>${escapeHtml(feature)}</li>`).join('')}</ul><div class="hero-actions"><a class="btn btn-primary" href="product.html?id=${encodeURIComponent(product.id)}">查看产品 ↗</a><a class="btn btn-ghost" href="products.html">全部产品</a></div></div><div class="featured-model" data-model-slot="${escapeHtml(product.id)}" role="button" tabindex="0" aria-label="放大并旋转查看 ${escapeHtml(product.name)}" aria-expanded="false"><div class="model-orbit"></div><img src="${product.image}" alt="${escapeHtml(product.name)} 360度展示" draggable="false"><span class="model-expand-label">点击沉浸查看 ＋</span><span class="model-hint">点击放大 · 拖动模拟 360° 旋转</span></div>`;
    thumbs.innerHTML = featuredProducts.map((item, index) => `<button type="button" class="featured-thumb ${index === activeIndex ? 'is-active' : ''}" data-featured-index="${index}" aria-label="查看 ${escapeHtml(item.name)}" aria-pressed="${index === activeIndex}"><img src="${item.image}" alt=""><span><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.tagline)}</small></span></button>`).join('');
    rotation = 0;
    tilt = 0;
  };
  const toggleImmersive = (force) => {
    const model = $('.featured-model', viewer);
    const nextState = typeof force === 'boolean' ? force : !viewer.classList.contains('is-immersive');
    viewer.classList.toggle('is-immersive', nextState);
    model?.setAttribute('aria-expanded', String(nextState));
    const label = $('.model-expand-label', viewer);
    const hint = $('.model-hint', viewer);
    if (label) label.textContent = nextState ? '退出沉浸查看 ×' : '点击沉浸查看 ＋';
    if (hint) hint.textContent = nextState ? '↔ 拖动旋转 · 按 ESC 退出' : '点击放大 · 拖动模拟 360° 旋转';
  };
  const move = (direction) => { activeIndex = wrapIndex(activeIndex + direction, featuredProducts.length); draw(); };
  $('[data-featured-prev]')?.addEventListener('click', () => move(-1));
  $('[data-featured-next]')?.addEventListener('click', () => move(1));
  thumbs.addEventListener('click', (event) => { const button = event.target.closest('[data-featured-index]'); if (!button) return; activeIndex = Number(button.dataset.featuredIndex); draw(); });
  viewer.addEventListener('click', (event) => { if (!event.target.closest('.featured-model') || dragDistance > 6) return; toggleImmersive(); });
  viewer.addEventListener('keydown', (event) => { if (!event.target.closest('.featured-model')) return; if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggleImmersive(); } });
  viewer.addEventListener('pointerdown', (event) => { if (!event.target.closest('.featured-model')) return; dragStart = event.clientX; dragStartY = event.clientY; dragDistance = 0; viewer.setPointerCapture(event.pointerId); });
  viewer.addEventListener('pointermove', (event) => { if (dragStart === null) return; const deltaX = event.clientX - dragStart; const deltaY = event.clientY - dragStartY; rotation += deltaX * .55; tilt = Math.max(-12, Math.min(12, tilt - deltaY * .12)); dragDistance += Math.abs(deltaX) + Math.abs(deltaY); dragStart = event.clientX; dragStartY = event.clientY; const image = $('.featured-model img', viewer); if (image) image.style.transform = `perspective(1100px) rotateX(${tilt}deg) rotateY(${rotation}deg) scale(${viewer.classList.contains('is-immersive') ? 1.05 : 1})`; });
  const endDrag = () => { dragStart = null; dragStartY = null; setTimeout(() => { dragDistance = 0; }, 0); };
  viewer.addEventListener('pointerup', endDrag);
  viewer.addEventListener('pointercancel', endDrag);
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && viewer.classList.contains('is-immersive')) toggleImmersive(false); });
  draw();
}

function renderProducts() {
  const grid = $('#product-grid'); if (!grid) return;
  const result = $('#result-count'); const category = query.get('category') || ''; const scenario = query.get('scenario') || '';
  let selectedCategory = category; let selectedScenario = scenario;
  const draw = () => { const list = filterProducts(products, { category: selectedCategory, scenario: selectedScenario }); grid.innerHTML = list.length ? list.map(productCard).join('') : '<div class="empty-state" style="grid-column:1/-1">暂时没有符合条件的产品，请重置筛选后继续浏览。</div>'; if (result) result.textContent = `找到 ${list.length} 款产品`; $$('[data-filter-group="category"] .filter-button').forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.value === selectedCategory))); $$('[data-filter-group="scenario"] .filter-button').forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.value === selectedScenario))); };
  $('[data-filter-group="category"]')?.addEventListener('click', (event) => { const button = event.target.closest('button'); if (!button) return; selectedCategory = button.dataset.value; draw(); });
  $('[data-filter-group="scenario"]')?.addEventListener('click', (event) => { const button = event.target.closest('button'); if (!button) return; selectedScenario = button.dataset.value; draw(); });
  $('[data-reset-filters]')?.addEventListener('click', () => { selectedCategory = ''; selectedScenario = ''; draw(); });
  draw();
}

function renderProductDetail() {
  const root = $('#product-detail'); if (!root) return;
  const product = getProductById(products, query.get('id'));
  if (!product) { root.innerHTML = '<div class="empty-state"><h2>暂未找到该产品</h2><p>请返回产品中心，浏览完整产品系列。</p><a class="btn btn-primary" href="products.html">返回产品中心</a></div>'; return; }
  root.innerHTML = `<div class="detail-grid"><div class="detail-media"><img src="${product.image}" alt="${escapeHtml(product.name)}"></div><div class="detail-copy"><span class="eyebrow">${escapeHtml(product.category)} · WARSUN</span><h1>${escapeHtml(product.name)}</h1><p class="lead">${escapeHtml(product.description)}</p><ul class="feature-list">${product.features.map((item) => `<li>${escapeHtml(item)}</li>`).join('')}</ul><div class="hero-actions"><a class="btn btn-primary" href="contact.html?product=${encodeURIComponent(product.id)}&interest=获取报价">获取报价</a><a class="btn btn-ghost" href="contact.html?product=${encodeURIComponent(product.id)}&interest=经销商合作">成为经销商</a></div><table class="spec-table"><tbody>${product.specs.map(([key, value]) => `<tr><th>${escapeHtml(key)}</th><td>${escapeHtml(value)}</td></tr>`).join('')}</tbody></table><p class="section-copy">适用场景：${product.scenario.map(escapeHtml).join(' · ')}</p></div></div>`;
}

function renderCollectionPages() {
  const scenarioRoot = $('#scenario-list'); if (scenarioRoot) scenarioRoot.innerHTML = scenarios.map((item) => `<article class="scenario-card" id="${encodeURIComponent(item.name)}"><span class="card-icon">${item.icon}</span><h3>${item.name}</h3><p>${item.description}</p><a class="text-link" href="products.html?scenario=${encodeURIComponent(item.name)}">查看相关产品 →</a></article>`).join('');
  const statRoot = $('#about-stats'); if (statRoot) statRoot.innerHTML = siteStats.map((item) => `<div class="stat"><strong>${item.value}</strong><span>${item.label}</span></div>`).join('');
}

function initInquiry() {
  const form = $('#inquiry-form'); if (!form) return;
  form.noValidate = true;
  const productId = query.get('product'); const product = getProductById(products, productId); const context = $('#inquiry-context');
  if (context && product) context.textContent = `当前咨询产品：${product.name}`;
  const interest = $('#interest'); if (interest && query.get('interest')) interest.value = query.get('interest');
  form.addEventListener('submit', (event) => { event.preventDefault(); let firstInvalid = null; $$('.field input[required], .field select[required], .field textarea[required]', form).forEach((field) => { const error = $(`[data-error-for="${field.id}"]`, form); if (!field.value.trim()) { field.setAttribute('aria-invalid', 'true'); if (error) error.textContent = '请填写此项'; firstInvalid ||= field; } else { field.removeAttribute('aria-invalid'); if (error) error.textContent = ''; } }); if (firstInvalid) { firstInvalid.focus(); return; } $('#form-status').textContent = '信息已准备完成。接入正式提交接口后，我们会根据你的需求联系你。'; form.reset(); });
}

function initDownloadPlaceholders() { $$('[data-action="download-placeholder"]').forEach((button) => button.addEventListener('click', () => { const status = $('[data-support-status]'); if (status) status.textContent = '资料下载功能将在接入真实文件后开放。'; })); }

renderSharedChrome(); renderHome(); renderProducts(); renderProductDetail(); renderCollectionPages(); initInquiry(); initDownloadPlaceholders();
window.WarsunSite = { categories, products, filterProducts, getProductById };
