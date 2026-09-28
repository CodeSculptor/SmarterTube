// SmarterTube opt-in anonymous stats — Cloudflare Worker + D1.
//
// Privacy by construction:
//   * no install ids: the app sends at most one ping per UTC day, and the server only
//     increments counters (see smarttubetv/src/stmobile/.../stats/Heartbeat.java);
//   * the client IP and headers are never read or stored (observability is off in wrangler.toml);
//   * crash reports are message-free stack traces, grouped by a hash of the trace.
//
// Routes:
//   POST /ping    {v, sdk, new_week, new_month, first}   -> bumps today's counters
//   POST /crash   {v, sdk, device, trace}                -> upserts a crash group
//   GET  /        public dashboard page (renders /stats)
//   GET  /stats   public aggregate counts (JSON; ?days=7|30|90|365|730, default 90; cached)
//   GET  /crashes crash groups — needs "Authorization: Bearer <ADMIN_TOKEN>"

import PAGE_HTML from "./page.html";

const MAX_BODY = 64 * 1024;
const MAX_TRACE = 32 * 1024;
const VERSION_RE = /^[0-9A-Za-z.+\-]{1,40}$/;
// /stats is public, so bound its database reads: ?days snaps to one of these, and each result is
// cached (in D1 + in the isolate) for STATS_TTL_MS. A page view then costs at most one row read.
const STATS_DAYS = [7, 30, 90, 365, 730];
const STATS_TTL_MS = 5 * 60 * 1000;
const memCache = new Map(); // days -> {body, at}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/ping") return await ping(request, env);
      if (request.method === "POST" && url.pathname === "/crash") return await crash(request, env);
      if (request.method === "GET" && url.pathname === "/") return page();
      if (request.method === "GET" && url.pathname === "/stats") return await stats(url, env);
      if (request.method === "GET" && url.pathname === "/crashes") return await crashes(request, env);
      return new Response("Not found", { status: 404 });
    } catch (e) {
      return new Response("Bad request", { status: 400 });
    }
  },
};

async function readJson(request) {
  const text = await request.text();
  if (text.length > MAX_BODY) throw new Error("too large");
  const body = JSON.parse(text);
  if (!body || typeof body !== "object") throw new Error("not an object");
  return body;
}

function version(v) {
  if (typeof v !== "string" || !VERSION_RE.test(v)) throw new Error("bad version");
  return v;
}

function sdk(n) {
  if (!Number.isInteger(n) || n < 1 || n > 100) throw new Error("bad sdk");
  return n;
}

function today() {
  return new Date().toISOString().slice(0, 10); // UTC yyyy-mm-dd
}

async function ping(request, env) {
  const b = await readJson(request);
  const v = version(b.v);
  const s = sdk(b.sdk);
  const w = b.new_week === true ? 1 : 0;
  const m = b.new_month === true ? 1 : 0;
  const f = b.first === true ? 1 : 0;
  await env.DB.prepare(
    `INSERT INTO pings (day, version, sdk, pings, new_week, new_month, first)
     VALUES (?1, ?2, ?3, 1, ?4, ?5, ?6)
     ON CONFLICT (day, version, sdk) DO UPDATE SET
       pings = pings + 1,
       new_week = new_week + excluded.new_week,
       new_month = new_month + excluded.new_month,
       first = first + excluded.first`
  ).bind(today(), v, s, w, m, f).run();
  return new Response(null, { status: 204 });
}

async function crash(request, env) {
  const b = await readJson(request);
  const v = version(b.v);
  const s = sdk(b.sdk);
  const device = typeof b.device === "string" ? b.device.slice(0, 80) : "";
  if (typeof b.trace !== "string" || b.trace.length === 0) throw new Error("no trace");
  const trace = b.trace.slice(0, MAX_TRACE);
  const sig = await sha256(trace.replace(/:\d+\)/g, ")")); // group across line-number drift
  const day = today();
  await env.DB.prepare(
    `INSERT INTO crashes (sig, version, count, first_day, last_day, sdk, device, trace)
     VALUES (?1, ?2, 1, ?3, ?3, ?4, ?5, ?6)
     ON CONFLICT (sig, version) DO UPDATE SET
       count = count + 1, last_day = excluded.last_day, sdk = excluded.sdk, device = excluded.device`
  ).bind(sig, v, day, s, device, trace).run();
  return new Response(null, { status: 204 });
}

async function stats(url, env) {
  const asked = parseInt(url.searchParams.get("days") || "90", 10) || 90;
  const days = STATS_DAYS.find((d) => d >= asked) || STATS_DAYS[STATS_DAYS.length - 1];
  const now = Date.now();
  let hit = memCache.get(days);
  if (!hit || now - hit.at >= STATS_TTL_MS) {
    const row = await env.DB.prepare("SELECT body, at FROM stats_cache WHERE days = ?1").bind(days).first();
    hit = row && now - row.at < STATS_TTL_MS ? { body: row.body, at: row.at } : null;
    if (!hit) {
      hit = { body: JSON.stringify(await computeStats(env, days)), at: now };
      await env.DB.prepare(
        `INSERT INTO stats_cache (days, body, at) VALUES (?1, ?2, ?3)
         ON CONFLICT (days) DO UPDATE SET body = excluded.body, at = excluded.at`
      ).bind(days, hit.body, hit.at).run();
    }
    memCache.set(days, hit);
  }
  const maxAge = Math.max(0, Math.round((STATS_TTL_MS - (now - hit.at)) / 1000));
  return new Response(hit.body, {
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": `public, max-age=${maxAge}`,
    },
  });
}

async function computeStats(env, days) {
  const since = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);
  const daily = await env.DB.prepare(
    `SELECT day, SUM(pings) AS daily_active, SUM(new_week) AS new_week, SUM(new_month) AS new_month,
            SUM(first) AS new_installs
     FROM pings WHERE day >= ?1 GROUP BY day ORDER BY day`
  ).bind(since).all();
  // Weeks are Monday-based (matching the app); date(day, '-6 days', 'weekday 1') = that week's Monday.
  const weekly = await env.DB.prepare(
    `SELECT date(day, '-6 days', 'weekday 1') AS week, SUM(new_week) AS weekly_active
     FROM pings WHERE day >= ?1 GROUP BY week ORDER BY week`
  ).bind(since).all();
  const monthly = await env.DB.prepare(
    `SELECT substr(day, 1, 7) AS month, SUM(new_month) AS monthly_active
     FROM pings WHERE day >= ?1 GROUP BY month ORDER BY month`
  ).bind(since).all();
  const versions = await env.DB.prepare(
    `SELECT version, SUM(pings) AS pings FROM pings WHERE day >= ?1 GROUP BY version ORDER BY pings DESC`
  ).bind(since).all();
  const android = await env.DB.prepare(
    `SELECT sdk, SUM(pings) AS pings FROM pings WHERE day >= ?1 GROUP BY sdk ORDER BY sdk`
  ).bind(since).all();
  return {
    since, days, generated: new Date().toISOString(),
    daily: daily.results, weekly: weekly.results, monthly: monthly.results,
    versions: versions.results, android_sdk: android.results,
  };
}

function page() {
  return new Response(PAGE_HTML, {
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "public, max-age=300" },
  });
}

async function crashes(request, env) {
  const auth = request.headers.get("Authorization") || "";
  if (!env.ADMIN_TOKEN || auth !== `Bearer ${env.ADMIN_TOKEN}`) {
    return new Response("Unauthorized", { status: 401 });
  }
  const rows = await env.DB.prepare(
    `SELECT sig, version, count, first_day, last_day, sdk, device, trace
     FROM crashes ORDER BY last_day DESC, count DESC LIMIT 200`
  ).all();
  return Response.json(rows.results);
}

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}
