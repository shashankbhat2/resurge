# Resurge website

The site for [Resurge](https://github.com/shashankbhat2/resurge): Astro and Tailwind CSS, static output.

```sh
npm install
npm run dev       # http://localhost:4321
npm run build     # static site in dist/
npm run preview   # serve dist/
npm run deploy    # build and publish to Cloudflare
npm run og        # render og/og.html to public/og.jpg (the share image)
```

- **Pages:** `src/pages/` has Home, About, Download and Releases.
- **Releases and the download button** are read from the GitHub releases API when the site is built (`src/lib/releases.ts`). APK names carry their version, so rebuild after publishing a release. Set `GITHUB_TOKEN` in CI to raise GitHub's rate limit. A build that can't reach GitHub still succeeds and points to the releases page there.
- **Look:** the app's Mono tokens in `src/styles/global.css`, with Bricolage Grotesque and Figtree from `public/fonts/` and the app icon at `public/icon.png`.
- **Motion:** sections rise, sharpen and fade in as they scroll into view (`.reveal`, staggered with `--d`). Score rings fill from empty, and the scores section on Home is told by scrolling (`ScoreStory.astro`). All of it is switched off for people who prefer reduced motion.
- **Hosting:** a Cloudflare Worker (`resurge-website`) serving `dist/` as static assets on https://resurge.fit, set in `wrangler.jsonc`. `public/_headers` sets long caching for hashed assets and fonts.
- **SEO:** every page has a canonical URL, Open Graph and Twitter tags with the share image (`public/og.jpg`), and schema.org JSON-LD (`src/lib/seo.ts`): Organization and WebSite everywhere, the app as a MobileApplication (version and APK from the latest release) on Home and Download, the FAQ on Home, and breadcrumbs on the other pages. `sitemap.xml` is built from `src/pages/sitemap.xml.ts`; `robots.txt` points to it.
