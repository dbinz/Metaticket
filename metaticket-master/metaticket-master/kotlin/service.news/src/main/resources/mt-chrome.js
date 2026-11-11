/**
 * Progressive navbar hydration on BFF news pages — menus/auth/search load after first paint.
 */
(function () {
  'use strict';

  var TOKEN_KEY = 'token';
  var DEBOUNCE_MS = 500;

  var PROFILE_LINKS = [
    { title: 'Thông tin tài khoản', path: '/thong-tin-tai-khoan' },
    { title: 'Quản lý vé', path: '/quan-ly-ve' },
    { title: 'Quản lý đơn hàng', path: '/quan-ly-don-hang' },
  ];

  function cfg() {
    return window.__MT_CHROME__ || {};
  }

  function frontendBase() {
    var base = cfg().frontend || '';
    return base.replace(/\/$/, '');
  }

  function getToken() {
    try {
      var raw = localStorage.getItem(TOKEN_KEY);
      if (!raw) return null;
      if (raw.charAt(0) === '"') {
        var parsed = JSON.parse(raw);
        return typeof parsed === 'string' ? parsed : null;
      }
      return raw;
    } catch (e) {
      return null;
    }
  }

  function isAuthenticated() {
    var token = getToken();
    if (!token) return false;
    try {
      var payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.exp ? payload.exp * 1000 > Date.now() : false;
    } catch (e) {
      return false;
    }
  }

  function apiRoot() {
    var root = cfg().api;
    return root == null ? '/api' : String(root).replace(/\/$/, '');
  }

  function apiFetch(path, opts) {
    opts = opts || {};
    var headers = { Accept: 'application/json' };
    var token = getToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    return fetch(apiRoot() + path, {
      method: opts.method || 'GET',
      headers: headers,
      credentials: 'same-origin',
      body: opts.body,
    });
  }

  function iconBase() {
    return cfg().iconBase || '/assets/homepage/icons';
  }

  function escHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function showDetailPath(show) {
    var slug = (show.slug || '').trim();
    return slug ? '/s/' + encodeURIComponent(slug) : '/show/' + encodeURIComponent(show.id);
  }

  function eventCategoryHref(cat) {
    var base = frontendBase();
    return (
      base +
      '/su-kien?id=' +
      encodeURIComponent(cat.id || '') +
      '&name=' +
      encodeURIComponent(cat.name || '')
    );
  }

  function buildEventMenuItems(categories) {
    var html = '';
    categories.forEach(function (cat) {
      html +=
        '<a href="' +
        escHtml(eventCategoryHref(cat)) +
        '">' +
        escHtml(cat.name || '') +
        '</a>';
    });
    return html;
  }

  function buildEventDrawerItems(categories) {
    var base = frontendBase();
    var html = '';
    categories.forEach(function (cat) {
      html += '<a href="' + escHtml(eventCategoryHref(cat)) + '">' + escHtml(cat.name || '') + '</a>';
    });
    return html;
  }

  function buildNewsMenuItems(categories) {
    var base = frontendBase();
    var html = '<a href="' + escHtml(base + '/news') + '">Tất cả tin tức</a>';
    categories.forEach(function (cat) {
      html +=
        '<a href="' +
        escHtml(base + '/news/category/' + encodeURIComponent(cat.code || '')) +
        '">' +
        escHtml(cat.name || '') +
        '</a>';
    });
    return html;
  }

  function buildNewsDrawerItems(categories) {
    var base = frontendBase();
    var html = '<a href="' + escHtml(base + '/news') + '">Tất cả tin tức</a>';
    categories.forEach(function (cat) {
      html +=
        '<a href="' +
        escHtml(base + '/news/category/' + encodeURIComponent(cat.code || '')) +
        '">' +
        escHtml(cat.name || '') +
        '</a>';
    });
    return html;
  }

  /** Fetches event/news categories in parallel and replaces skeleton menu slots. */
  function hydrateNavMenus() {
    var eventsMenu = document.querySelector('[data-mt-menu="events"]');
    var newsMenu = document.querySelector('[data-mt-menu="news"]');
    var eventsDrawer = document.querySelector('[data-mt-drawer="events"]');
    var newsDrawer = document.querySelector('[data-mt-drawer="news"]');

    fetchEventCategories()
      .then(function (list) {
        if (eventsMenu) eventsMenu.innerHTML = buildEventMenuItems(list);
        if (eventsDrawer) eventsDrawer.innerHTML = buildEventDrawerItems(list);
      })
      .catch(function () {
        if (eventsMenu) eventsMenu.innerHTML = '<p class="navbar-menu-error">Không tải được danh mục</p>';
        if (eventsDrawer) eventsDrawer.innerHTML = '';
      });

    apiFetch('/public/news/category/fetch/all')
      .then(function (res) {
        if (!res.ok) throw new Error('news ' + res.status);
        return res.json();
      })
      .then(function (data) {
        var list = Array.isArray(data) ? data : [];
        if (newsMenu) newsMenu.innerHTML = buildNewsMenuItems(list);
        if (newsDrawer) newsDrawer.innerHTML = buildNewsDrawerItems(list);
      })
      .catch(function () {
        if (newsMenu) newsMenu.innerHTML = '<p class="navbar-menu-error">Không tải được danh mục</p>';
        if (newsDrawer) newsDrawer.innerHTML = '';
      });
  }

  function profileHref(path) {
    return frontendBase() + path;
  }

  function buildAuthDesktop(name) {
    var icon = iconBase() + '/ic_arrow_bottom_white_thin.svg';
    var items = PROFILE_LINKS.map(function (link) {
      return '<a href="' + escHtml(profileHref(link.path)) + '">' + escHtml(link.title) + '</a>';
    }).join('');
    return (
      '<div class="nav-item" tabindex="0">' +
      '<span>' +
      escHtml(name) +
      '</span>' +
      '<img class="arrow" src="' +
      icon +
      '" alt="" />' +
      '<div class="nav-dropdown align-right"><div class="nav-dropdown-inner">' +
      items +
      '<button type="button" class="navbar-logout-btn">Đăng xuất</button>' +
      '</div></div></div>'
    );
  }

  function buildAuthDrawer(name) {
    var items = PROFILE_LINKS.map(function (link) {
      return (
        '<a class="drawer-plain-item" href="' +
        escHtml(profileHref(link.path)) +
        '">' +
        escHtml(link.title) +
        '</a>'
      );
    }).join('');
    return items + '<button type="button" class="drawer-logout-btn navbar-drawer-logout">Đăng xuất</button>';
  }

  function buildLoginDesktop() {
    return (
      '<a class="navbar-login" href="' +
      escHtml(cfg().loginHref || frontendBase() + '/dang-nhap') +
      '">Đăng Nhập</a>'
    );
  }

  function buildLoginDrawer() {
    return (
      '<a class="drawer-login-btn" href="' +
      escHtml(cfg().loginHref || frontendBase() + '/dang-nhap') +
      '">Đăng Nhập</a>'
    );
  }

  function authSkeleton() {
    return '<span class="navbar-skeleton-pill" aria-label="Đang tải"></span>';
  }

  function bindLogout(root) {
    if (!root) return;
    root.addEventListener('click', function (e) {
      var btn = e.target.closest('.navbar-logout-btn, .navbar-drawer-logout');
      if (!btn) return;
      e.preventDefault();
      apiFetch('/public/customer-auth/logout', { method: 'POST' }).finally(function () {
        localStorage.removeItem(TOKEN_KEY);
        window.location.reload();
      });
    });
  }

  function bindLoginButtons() {
    var loginHref = cfg().loginHref || '/dang-nhap';
    document.querySelectorAll('[data-mt-login]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        window.location.href = btn.getAttribute('data-mt-login-href') || loginHref;
      });
    });
  }

  function hydrateAuth() {
    var desktop = document.getElementById('mt-nav-auth');
    var drawer = document.getElementById('mt-nav-auth-drawer');
    if (!isAuthenticated()) {
      if (desktop) desktop.innerHTML = buildLoginDesktop();
      if (drawer) drawer.innerHTML = buildLoginDrawer();
      return;
    }

    if (desktop) desktop.innerHTML = authSkeleton();
    if (drawer) drawer.innerHTML = authSkeleton();

    apiFetch('/customer-user/profile')
      .then(function (res) {
        if (!res.ok) throw new Error('profile ' + res.status);
        return res.json();
      })
      .then(function (profile) {
        var name = profile.name || profile.phone || 'Tài khoản';
        if (desktop) {
          desktop.innerHTML = buildAuthDesktop(name);
          bindLogout(desktop);
        }
        if (drawer) {
          drawer.innerHTML = buildAuthDrawer(name);
          bindLogout(drawer);
        }
      })
      .catch(function () {
        if (desktop) desktop.innerHTML = buildLoginDesktop();
        if (drawer) drawer.innerHTML = buildLoginDrawer();
      });
  }

  function fetchEventCategories() {
    return apiFetch('/public/show/event-category/fetch')
      .then(function (res) {
        if (!res.ok) throw new Error('events ' + res.status);
        return res.json();
      })
      .then(function (data) {
        return Array.isArray(data) ? data : [];
      });
  }

  function articleHref(slug) {
    return frontendBase() + '/news/' + encodeURIComponent(slug) + '.html';
  }

  function newsCardHtml(item) {
    return (
      '<a class="hs-card" href="' +
      escHtml(articleHref(item.slug || '')) +
      '">' +
      '<div class="hs-media hs-media--tile">' +
      '<img src="' +
      escHtml(item.thumbnail || '') +
      '" alt="' +
      escHtml(item.title || '') +
      '" loading="lazy" />' +
      '<span class="hs-cat">' +
      escHtml(item.categoryName || '') +
      '</span></div>' +
      '<div class="hs-body"><h3 class="hs-name">' +
      escHtml(item.title || '') +
      '</h3><p class="hs-subdesc">' +
      escHtml(item.summary || '') +
      '</p></div></a>'
    );
  }

  function fillIsland(gridId, items) {
    var grid = document.getElementById(gridId);
    if (!grid) return;
    var section = grid.closest('.art-rail');
    if (!items.length) {
      grid.innerHTML = '';
      if (section) section.style.display = 'none';
      return;
    }
    if (section) section.style.display = '';
    grid.innerHTML = items.map(newsCardHtml).join('');
  }

  function jsonContent(res) {
    if (!res.ok) throw new Error('news ' + res.status);
    return res.json();
  }

	/**
	 * Featured = pins. Related = `/fetch/related`. “Có thể bạn quan tâm” = same category, size 6.
	 */
	function hydrateIslands() {
		var slug = cfg().slug || '';
		var categoryCode = cfg().categoryCode || '';
		var exclude = slug ? 'excludeSlug=' + encodeURIComponent(slug) : '';

		apiFetch('/public/news/fetch/featured' + (exclude ? '?' + exclude : ''))
			.then(jsonContent)
			.then(function (data) {
				fillIsland('mt-featured-grid', data.content || []);
			})
			.catch(function () {
				fillIsland('mt-featured-grid', []);
			});

		if (!slug) {
			fillIsland('mt-related-grid', []);
		} else {
			apiFetch('/public/news/fetch/related?slug=' + encodeURIComponent(slug) + '&size=6')
				.then(jsonContent)
				.then(function (data) {
					fillIsland('mt-related-grid', data.content || []);
				})
				.catch(function () {
					fillIsland('mt-related-grid', []);
				});
		}

		if (!categoryCode) {
			fillIsland('mt-more-grid', []);
			return;
		}
		var moreQs = 'offset=0&size=6&categoryCode=' + encodeURIComponent(categoryCode);
		if (exclude) moreQs += '&' + exclude;
		apiFetch('/public/news/fetch/all?' + moreQs)
			.then(jsonContent)
			.then(function (data) {
				fillIsland('mt-more-grid', data.content || []);
			})
			.catch(function () {
				fillIsland('mt-more-grid', []);
			});
	}

  function renderSearchResults(panel, shows, keyword) {
    if (!shows.length && keyword) {
      panel.innerHTML =
        '<div class="navbar-search-empty">' +
        '<span class="navbar-search-empty-muted">Không có kết quả nào cho tìm kiếm </span>' +
        '<span>"' +
        escHtml(keyword) +
        '"</span></div>';
      panel.hidden = false;
      return;
    }
    if (!shows.length) {
      panel.hidden = true;
      panel.innerHTML = '';
      return;
    }
    var heading =
      keyword === ''
        ? '<p class="navbar-search-results-title">Top sự kiện nổi bật</p>'
        : '';
    var items = shows
      .map(function (show) {
        var thumb = show.thumbnailUrl || show.bannerImageUrl || '';
        return (
          '<a class="navbar-search-item" href="' +
          showDetailPath(show) +
          '">' +
          (thumb ? '<img src="' + escHtml(thumb) + '" alt="" loading="lazy" />' : '') +
          '<span class="one-line-text">' +
          escHtml(show.showName || '') +
          '</span></a>'
        );
      })
      .join('');
    panel.innerHTML = heading + items;
    panel.hidden = false;
  }

  function fetchShows(keyword) {
    if (keyword) {
      return apiFetch(
        '/public/metasearch/shows/suggestion?page=0&size=10&keyword=' + encodeURIComponent(keyword),
      ).then(function (res) {
        if (!res.ok) throw new Error('search ' + res.status);
        return res.json();
      });
    }
    return apiFetch('/public/metasearch/shows/highlight?page=0&size=6').then(function (res) {
      if (!res.ok) throw new Error('highlight ' + res.status);
      return res.json();
    });
  }

  function hydrateSearch(rootId) {
    var root = document.getElementById(rootId);
    if (!root) return;

    var input = root.querySelector('input');
    var panel = root.querySelector('.navbar-search-results');
    if (!input || !panel) return;

    var timer = null;
    var lastKeyword = null;

    function load(keyword) {
      lastKeyword = keyword;
      fetchShows(keyword)
        .then(function (data) {
          if (lastKeyword !== keyword) return;
          renderSearchResults(panel, (data && data.content) || [], keyword);
        })
        .catch(function () {
          panel.hidden = true;
        });
    }

    input.addEventListener('focus', function () {
      load(input.value.trim());
    });

    input.addEventListener('input', function () {
      clearTimeout(timer);
      var keyword = input.value.trim();
      timer = setTimeout(function () {
        load(keyword);
      }, DEBOUNCE_MS);
    });

    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        var first = panel.querySelector('.navbar-search-item');
        if (first) window.location.href = first.getAttribute('href');
      }
    });

    document.addEventListener('click', function (e) {
      if (!root.contains(e.target)) {
        panel.hidden = true;
      }
    });
  }

  function init() {
    bindLoginButtons();
    hydrateNavMenus();
    hydrateAuth();
    hydrateIslands();
    hydrateSearch('mt-search');
    hydrateSearch('mt-search-mobile');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
