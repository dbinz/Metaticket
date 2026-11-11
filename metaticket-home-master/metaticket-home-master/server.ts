import express from 'express';
import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Minimal Node/Express server — no Angular SSR.
 *
 * Routing:
 * - `/news/:slug.html` → proxy to service.news BFF render (full HTML, SEO-ready)
 * - `/news/:slug` → 301 redirect to `.html` (canonical)
 * - static assets (*.js, *.css, …) → express.static
 * - everything else (including /news hub) → CSR index.html
 *
 * In staging/production, nginx should route /news/:slug directly to service.news
 * (port 4041) and bypass this Node process entirely. This proxy is a local-dev
 * convenience so devs can test without touching nginx.
 *
 * BFF base: NEWS_RENDER_BASE env (default http://127.0.0.1:8082 for local dev).
 */
const NEWS_RENDER_BASE = (process.env['NEWS_RENDER_BASE'] ?? 'http://127.0.0.1:8082').replace(/\/$/, '');

/** Parses article slug from /news/<slug> or /news/<slug>.html (not category). */
function parseArticleSlug(path: string): string | null {
  const m = path.match(/^\/news\/([^/]+?)(?:\.html)?\/?$/i);
  if (!m || m[1] === 'category' || !m[1]) return null;
  return m[1].replace(/\.html$/i, '');
}

function isArticleHtmlRoute(path: string): boolean {
  return /\.html\/?$/i.test(path) && parseArticleSlug(path) !== null;
}

function isBareArticleRoute(path: string): boolean {
  return parseArticleSlug(path) !== null && !isArticleHtmlRoute(path);
}

async function proxyBffRender(slug: string, res: express.Response, next: express.NextFunction): Promise<void> {
  const url = `${NEWS_RENDER_BASE}/public/news/render/${encodeURIComponent(slug)}`;
  try {
    const upstream = await fetch(url);
    if (upstream.status === 404) {
      res.status(404).send('Article not found');
      return;
    }
    if (!upstream.ok) {
      next(new Error(`BFF render error ${upstream.status} for slug=${slug}`));
      return;
    }
    const html = await upstream.text();
    const cc = upstream.headers.get('cache-control');
    if (cc) res.setHeader('Cache-Control', cc);
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.status(200).send(html);
  } catch (err) {
    next(err);
  }
}

export function app(): express.Express {
  const server = express();
  const serverDistFolder = dirname(fileURLToPath(import.meta.url));
  const browserDistFolder = resolve(serverDistFolder, '../browser');
  const browserIndexHtml = join(browserDistFolder, 'index.html');
  // Fallback when browser build not yet generated (e.g. watch mode startup)
  const fallbackIndex = existsSync(browserIndexHtml) ? browserIndexHtml : join(serverDistFolder, 'index.html');

  server.get('*.*', express.static(browserDistFolder, { maxAge: '1y', index: false }));

  server.get('*', (req, res, next) => {
    if (isBareArticleRoute(req.path)) {
      const slug = parseArticleSlug(req.path);
      res.redirect(301, `/news/${slug}.html`);
      return;
    }
    if (isArticleHtmlRoute(req.path)) {
      const slug = parseArticleSlug(req.path);
      if (slug) {
        void proxyBffRender(slug, res, next);
        return;
      }
    }
    // All other routes (hub, category, SPA pages) → CSR
    res.sendFile(fallbackIndex);
  });

  return server;
}

function run(): void {
  // Optional local helper (not used by Docker: nginx.conf proxies articles to service.news).
  const port = Number(process.env['PORT'] || 4000);
  const server = app();
  server.listen(port, '0.0.0.0', () => {
    console.log(`News BFF proxy + CSR static on http://0.0.0.0:${port}`);
  });
}

run();
