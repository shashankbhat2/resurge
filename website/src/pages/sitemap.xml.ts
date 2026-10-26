import type { APIRoute } from 'astro';
import { getReleases } from '../lib/releases';

/** The pages worth indexing (not the 404 page or the /about redirect), for search engines. */
export const GET: APIRoute = async ({ site }) => {
  const latest = (await getReleases())[0]?.date.toISOString().slice(0, 10);
  const pages = [
    { path: '/', priority: '1.0', lastmod: latest },
    { path: '/download', priority: '0.9', lastmod: latest },
    { path: '/releases', priority: '0.7', lastmod: latest },
    { path: '/story', priority: '0.6' },
  ];
  const urls = pages
    .map(
      (p) =>
        `  <url><loc>${new URL(p.path, site)}</loc>${p.lastmod ? `<lastmod>${p.lastmod}</lastmod>` : ''}<priority>${p.priority}</priority></url>`,
    )
    .join('\n');
  return new Response(
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`,
    { headers: { 'Content-Type': 'application/xml; charset=utf-8' } },
  );
};
