import { marked } from 'marked';
import { REPO } from './site';

export interface Release {
  version: string;
  tag: string;
  date: Date;
  latest: boolean;
  apkUrl: string | null;
  apkBytes: number | null;
  notesHtml: string;
}

interface GitHubAsset {
  name: string;
  size: number;
  browser_download_url: string;
}

interface GitHubRelease {
  tag_name: string;
  name: string | null;
  draft: boolean;
  prerelease: boolean;
  published_at: string;
  body: string | null;
  assets: GitHubAsset[];
}

let cached: Promise<Release[]> | null = null;

/**
 * Published releases, newest first, read from GitHub when the site is built. The APK's name
 * carries its version, so the download link always comes from here. A build without network
 * access (or over GitHub's rate limit) gets an empty list and the pages point to GitHub instead.
 * Set GITHUB_TOKEN to raise the rate limit in CI.
 */
export function getReleases(): Promise<Release[]> {
  cached ??= load();
  return cached;
}

async function load(): Promise<Release[]> {
  const api = REPO.replace('https://github.com/', 'https://api.github.com/repos/') + '/releases?per_page=30';
  const headers: Record<string, string> = { Accept: 'application/vnd.github+json' };
  const token = import.meta.env.GITHUB_TOKEN ?? process.env.GITHUB_TOKEN;
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    const res = await fetch(api, { headers });
    if (!res.ok) throw new Error(`GitHub answered ${res.status}`);
    const list = ((await res.json()) as GitHubRelease[]).filter((r) => !r.draft && !r.prerelease);
    return list.map((r, i) => {
      const apk = r.assets.find((a) => a.name.endsWith('.apk')) ?? null;
      return {
        version: r.tag_name.replace(/^v/, ''),
        tag: r.tag_name,
        date: new Date(r.published_at),
        latest: i === 0,
        apkUrl: apk?.browser_download_url ?? null,
        apkBytes: apk?.size ?? null,
        notesHtml: marked.parse(r.body ?? '', { async: false }) as string,
      };
    });
  } catch (e) {
    console.warn(`[releases] Couldn't read releases from GitHub: ${(e as Error).message}`);
    return [];
  }
}

export const formatSize = (bytes: number | null) => (bytes == null ? null : `${(bytes / 1_000_000).toFixed(1)} MB`);

export const formatDate = (d: Date) =>
  d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

/** The latest APK, or the releases page when it can't be read. */
export async function latestDownload() {
  const latest = (await getReleases())[0];
  return {
    href: latest?.apkUrl ?? `${REPO}/releases/latest`,
    version: latest?.version ?? null,
    size: formatSize(latest?.apkBytes ?? null),
  };
}
