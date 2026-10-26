/**
 * The site's Worker. Static pages are served straight from the assets; only /api/* runs here.
 *
 * POST /api/waitlist {email, company}: emails the sign-up to the address the WAITLIST_MAIL binding
 * allows (Cloudflare Email Routing; the destination must be verified there). `company` is a field
 * people never see, so a filled one means a bot and is quietly accepted without sending.
 */
import { EmailMessage } from 'cloudflare:email';

interface Env {
  ASSETS: { fetch(request: Request): Promise<Response> };
  WAITLIST_MAIL: { send(message: EmailMessage): Promise<void> };
}

const FROM = 'waitlist@resurge.fit';
const TO = 'shashankbhatme@gmail.com';
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

const json = (body: object, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === '/api/waitlist') {
      if (request.method !== 'POST') return json({ error: 'Method not allowed' }, 405);
      return waitlist(request, env);
    }
    return env.ASSETS.fetch(request);
  },
};

async function waitlist(request: Request, env: Env): Promise<Response> {
  let body: { email?: unknown; company?: unknown };
  try {
    body = await request.json();
  } catch {
    return json({ error: "That didn't come through. Try again." }, 400);
  }
  const email = typeof body.email === 'string' ? body.email.trim().toLowerCase() : '';
  if (body.company) return json({ ok: true });
  if (!EMAIL.test(email) || email.length > 254) return json({ error: "That doesn't look like an email address." }, 400);

  const cf = (request as Request & { cf?: { country?: string; city?: string } }).cf;
  const when = new Date();
  const text = [
    `New waitlist sign-up: ${email}`,
    '',
    `Time:    ${when.toISOString()}`,
    `Where:   ${[cf?.city, cf?.country].filter(Boolean).join(', ') || 'unknown'}`,
    `Page:    ${request.headers.get('Referer') ?? 'unknown'}`,
  ].join('\r\n');
  const raw = [
    `From: Resurge waitlist <${FROM}>`,
    `To: <${TO}>`,
    `Reply-To: <${email}>`,
    `Subject: Waitlist: ${email}`,
    `Date: ${when.toUTCString()}`,
    `Message-ID: <${crypto.randomUUID()}@resurge.fit>`,
    'MIME-Version: 1.0',
    'Content-Type: text/plain; charset=utf-8',
    'Content-Transfer-Encoding: 8bit',
    '',
    text,
  ].join('\r\n');

  try {
    await env.WAITLIST_MAIL.send(new EmailMessage(FROM, TO, raw));
  } catch (e) {
    console.error('Waitlist email failed', e);
    return json({ error: "We couldn't add you just now. Try again in a minute." }, 502);
  }
  return json({ ok: true });
}
