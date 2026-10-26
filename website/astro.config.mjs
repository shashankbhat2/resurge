import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  site: 'https://resurge.fit',
  redirects: { '/about': '/story' },
  // story.html rather than story/index.html, so /story is served without a trailing-slash redirect.
  build: { format: 'file' },
  vite: { plugins: [tailwindcss()] },
});
