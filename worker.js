// Ponte https per BatTV (Cloudflare Worker)
// Riceve richieste in https e le gira al provider in http.
// 1) Cambia la chiave qui sotto con una parola tua, lunga e non ovvia.
// 2) Nel player, campo "Ponte": https://NOME.TUOACCOUNT.workers.dev/?k=LA_TUA_CHIAVE

const KEY = 'cambia-questa-chiave';

export default {
  async fetch(req) {
    const u = new URL(req.url);
    const cors = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Range',
      'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges',
    };
    if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
    if (u.searchParams.get('k') !== KEY) return new Response('Chiave non valida', { status: 403, headers: cors });

    const target = u.searchParams.get('url');
    if (!target || !/^https?:\/\//i.test(target)) return new Response('Indirizzo mancante', { status: 400, headers: cors });

    const h = new Headers({ 'User-Agent': 'Mozilla/5.0' });
    const range = req.headers.get('Range');
    if (range) h.set('Range', range);

    let r;
    try {
      r = await fetch(target, { headers: h, redirect: 'follow' });
    } catch (e) {
      return new Response('Il provider non risponde', { status: 502, headers: cors });
    }

    const out = new Headers(r.headers);
    for (const [k, v] of Object.entries(cors)) out.set(k, v);

    // Playlist HLS: riscrive i link interni perché passino anch'essi dal ponte
    const ct = (r.headers.get('content-type') || '').toLowerCase();
    const finalUrl = r.url || target;
    if (ct.includes('mpegurl') || /\.m3u8?$/i.test(new URL(finalUrl).pathname)) {
      const wrap = x => `${u.origin}/?k=${encodeURIComponent(KEY)}&url=${encodeURIComponent(new URL(x, finalUrl).href)}`;
      const body = (await r.text()).split('\n').map(line => {
        const t = line.trim();
        if (!t) return line;
        if (t.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, x) => `URI="${wrap(x)}"`);
        return wrap(t);
      }).join('\n');
      out.delete('content-length');
      out.delete('content-encoding');
      return new Response(body, { status: r.status, headers: out });
    }

    return new Response(r.body, { status: r.status, headers: out });
  },
};
