# SmarterTube stats Worker

A tiny Cloudflare Worker + D1 database that receives SmarterTube's **opt-in** anonymous
usage heartbeat and crash reports. The app side lives in
`smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/stats/`.

## What it stores

| Table | Contents |
|---|---|
| `pings` | Per UTC day × app version × Android SDK: counters only (`pings`, `new_week`, `new_month`, `first`). |
| `crashes` | Crash groups: hash of a message-free stack trace, app version, count, first/last day, SDK, phone model, trace. |

No install id exists anywhere. Client IPs and headers are never read, and Worker logging is off
(`[observability] enabled = false`).

How to read the counters:

- **Daily active users:** `SUM(pings)` for a day (each install pings at most once a day).
- **Weekly / monthly active users:** `SUM(new_week)` over a Monday-based week, `SUM(new_month)` over a month.
- **Newly counted installs:** `SUM(first)`, meaning an install's first ever ping. That covers new installs, but also existing users who have just opted in. Right after launch it mostly reflects existing users.

All numbers only count people who opted in. Scale up by the opt-in rate if you want an estimate
of everyone.

## Deploy (one time)

**Deployed 2026-09-28** at `https://smartertube-stats.codesculptor.workers.dev` (Cloudflare account of the maintainer, D1 database `smartertube-stats` in WEUR). The `ADMIN_TOKEN` is kept outside the repo. To ship a code change, run `npx wrangler deploy` from this folder.

Needs Node.js and a free Cloudflare account.

```bash
cd stats-worker
npx wrangler login
npx wrangler d1 create smartertube-stats      # copy the database_id into wrangler.toml
npx wrangler d1 execute smartertube-stats --remote --file=schema.sql
npx wrangler secret put ADMIN_TOKEN           # any long random string; protects /crashes
npx wrangler deploy                           # prints https://smartertube-stats.<you>.workers.dev
```

Then put that URL in `mobile_stats_endpoint` in
`smarttubetv/src/stmobile/res/values/strings.xml` and ship a release. Until that string is set
the feature is dormant: no prompt, no Settings row, no network.

## Endpoints

| Route | Access | Purpose |
|---|---|---|
| `POST /ping` | app | `{v, sdk, new_week, new_month, first}` |
| `POST /crash` | app | `{v, sdk, device, trace}` |
| `GET /stats?days=90` | public | daily / weekly / monthly actives, version and Android breakdown (JSON) |
| `GET /crashes` | `Authorization: Bearer <ADMIN_TOKEN>` | latest 200 crash groups |

```bash
curl https://smartertube-stats.<you>.workers.dev/stats
```

```bash
curl -H "Authorization: Bearer <ADMIN_TOKEN>" https://smartertube-stats.<you>.workers.dev/crashes
```

Aggregate counts are public on purpose, so anyone can see exactly what is collected.

Anyone could send fake pings and inflate the counts. For a hobby project's rough numbers that is
acceptable, and nothing personal is at risk.
