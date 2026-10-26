import { REPO } from './site';
import { FAQS } from './faq';
import { getReleases } from './releases';

/** Structured data (schema.org JSON-LD) for the pages. `site` is the site's origin. */

export const SITE_NAME = 'Resurge';
export const DEFAULT_DESCRIPTION =
  'Resurge is an AI health coach for Android. Sleep, steps, workouts, meals and habits, scored every morning, with a coach that has read them. Try it for free.';

const id = (site: URL, fragment: string) => new URL(`/#${fragment}`, site).href;

/** Who publishes the site and the app; on every page. */
export function organization(site: URL) {
  return {
    '@type': 'Organization',
    '@id': id(site, 'organization'),
    name: SITE_NAME,
    url: site.href,
    logo: { '@type': 'ImageObject', url: new URL('/icon-512.png', site).href, width: 512, height: 512 },
    sameAs: [REPO],
  };
}

/** The site itself; on every page. */
export function website(site: URL) {
  return {
    '@type': 'WebSite',
    '@id': id(site, 'website'),
    name: SITE_NAME,
    url: site.href,
    description: DEFAULT_DESCRIPTION,
    inLanguage: 'en',
    publisher: { '@id': id(site, 'organization') },
  };
}

/** The Android app, with its latest release from GitHub. */
export async function app(site: URL) {
  const latest = (await getReleases())[0];
  return {
    '@type': 'MobileApplication',
    '@id': id(site, 'app'),
    name: SITE_NAME,
    description: DEFAULT_DESCRIPTION,
    url: site.href,
    operatingSystem: 'Android 8.0 or later',
    applicationCategory: 'HealthApplication',
    applicationSubCategory: 'Fitness and health coaching',
    image: new URL('/icon-512.png', site).href,
    screenshot: ['screen-scores', 'screen-food', 'screen-coach'].map((s) => new URL(`/ui/${s}.webp`, site).href),
    license: 'https://www.gnu.org/licenses/gpl-3.0.html',
    isAccessibleForFree: true,
    offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD', category: 'Free trial' },
    publisher: { '@id': id(site, 'organization') },
    ...(latest && {
      softwareVersion: latest.version,
      datePublished: latest.date.toISOString().slice(0, 10),
      ...(latest.apkUrl && { downloadUrl: latest.apkUrl, installUrl: latest.apkUrl }),
      ...(latest.apkBytes && { fileSize: `${(latest.apkBytes / 1_000_000).toFixed(1)}MB` }),
    }),
  };
}

export function faqPage(site: URL) {
  return {
    '@type': 'FAQPage',
    '@id': id(site, 'faq'),
    mainEntity: FAQS.map(([q, a]) => ({ '@type': 'Question', name: q, acceptedAnswer: { '@type': 'Answer', text: a } })),
  };
}

/** Home › this page. */
export function breadcrumbs(site: URL, name: string, path: string) {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: site.href },
      { '@type': 'ListItem', position: 2, name, item: new URL(path, site).href },
    ],
  };
}
