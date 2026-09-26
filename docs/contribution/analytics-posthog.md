# Analytics (PostHog) — Self-Hosted

> **Superseded, and being read with that in mind.** Ingest and query no longer go to PostHog.
> Pages load a beacon served by the [analytics engine](https://github.com/modlix-india/analytics-engine)
> and `/api/ui/analytics/query` proxies to its fixed widget API; `HogQLTenantRewriter` has been
> deleted, because a closed widget set has no expression to rewrite. What is still accurate
> below: the per-app toggles on the application document, the consent model, and the tenancy
> rules `AnalyticsService` enforces. What is not: the PostHog snippet, HogQL, session replay,
> the project and personal API keys, and the VM stacks — those are retired in milestone 12,
> and this document is rewritten when they go rather than half-edited now.

This document captures the end-to-end analytics architecture so anyone (or anyone's AI) can pick up the work without re-discovering it.

## Why self-host

We host PostHog ourselves so that user-facing event capture lives entirely on Modlix infrastructure (no third-party data egress). The platform builds many apps for many tenants — analytics has to be tenant-aware, ad-blocker-resilient, and consent-respecting from day one.

Session replay was part of the original scope and has since been withdrawn; the stack still carries its remains. Read workaround 1 before assuming anything about it.

## Architecture at a glance

```
Browser
  │
  ▼
analytics{,-stage,-dev}.modlix.com  (Cloudflare DNS → Worker)
  │  (proxy, edge cache for /static, /array)
  ▼
posthog-{dev,stage,prod}.modlix.com  (Caddy + Let's Encrypt on the VM)
  │
  ▼
PostHog hobby compose (web, capture, replay-capture, recording-api,
ingestion-sessionreplay, worker, temporal-django-worker, ...)
  │
  ├── ClickHouse, Postgres, Redis, Kafka (Redpanda), MinIO, SeaweedFS
  └── Session replay blobs → OCI Object Storage (S3-compat)
```

Three independent VMs, one per environment:

| Environment | VM (via `~/ocissh.sh`) | Public DNS | Cloudflare proxy host |
|---|---|---|---|
| Dev | `~/ocissh.sh dev analytics` | `posthog-dev.modlix.com` | `analytics-dev.modlix.com` |
| Stage | `~/ocissh.sh stage analytics` | `posthog-stage.modlix.com` | `analytics-stage.modlix.com` |
| Prod | `~/ocissh.sh prod analytics` | `posthog-prod.modlix.com` | `analytics.modlix.com` |

OCI Object Storage buckets (S3-compatible):

| Compartment | Bucket | Used by |
|---|---|---|
| non-prod | `dev-posthog` | dev session replays |
| non-prod | `stage-posthog` | stage session replays |
| prod | `prod-posthog` | prod session replays |

## Code paths

### Backend (`nocode-saas/ui`)

- [IndexHTMLService.java](../../ui/src/main/java/com/fincity/saas/ui/service/IndexHTMLService.java) — injects the PostHog snippet into rendered HTML at the application server. `generateAnalyticsSnippet(appProps)` reads:
  - **Env-level** (Spring Cloud Config): `ui.analytics.ingestionHost`, `ui.analytics.posthog.projectApiKey` — injected via `@Value`.
  - **App-level** (per-application toggles): see the table under "Application properties" below.
  - **Critical:** keys/URLs are NEVER stored on the application document. Only user-facing toggles.
- [ApplicationService.java](../../ui/src/main/java/com/fincity/saas/ui/service/ApplicationService.java) — `applyChange` inlines the app's `consentPage` as `consentPageDefinition`, exactly as it does for `shellPage`, and folds that page's uniqueId into the cache key.

### Frontend (`nocode-ui`)

- [`ui-app/ssr/src/render/htmlRenderer.ts`](../../../nocode-ui/ui-app/ssr/src/render/htmlRenderer.ts) — same snippet generator, server-side. Mirrors the Java implementation byte-for-byte.
- [`ui-app/ssr/src/config/configLoader.ts`](../../../nocode-ui/ui-app/ssr/src/config/configLoader.ts) — reads `ui.analytics.*` from Spring Cloud Config; merged in `mergeConfigs()` (must include the `analytics` branch — easy to miss).
- [`ui-app/client/src/App/analyticsConsent.ts`](../../../nocode-ui/ui-app/client/src/App/analyticsConsent.ts) — the only place that decides whether PostHog captures. Reads and writes the decision (localStorage with a cookie fallback, `modlix_analytics_consent`), mirrors it to `Store.analyticsConsent`, and drives `opt_in_capturing` / `opt_out_capturing`.
- [`ui-app/client/src/App/AnalyticsBinder.tsx`](../../../nocode-ui/ui-app/client/src/App/AnalyticsBinder.tsx) — renders nothing. Subscribes to `STORE_PREFIX.application`, `.auth.user`, `.auth.client`, `.urlDetails` via `addListenerAndCallImmediately` (NOT `getStore()` — that's expensive) to register the `app_code` / `url_client_code` / `client_code` / `page_name` super properties, call `identify` / `reset` around sign in, and replay a decision made on an earlier visit.
  - It reads the analytics settings from `Store.application`, **not** `window.__APP_BOOTSTRAP__`. Only the Node SSR renderer emits that global, so a binder keyed on it is dead on anything the Java `ui` service serves — which is how the original banner silently never ran locally.
- [`ui-app/client/src/functions/{Get,Set}AnalyticsConsent.ts`](../../../nocode-ui/ui-app/client/src/functions/) — the KIRun surface apps build their consent box against.
- [`ui-app/client/src/Engine/RenderEngineContainer.tsx`](../../../nocode-ui/ui-app/client/src/Engine/RenderEngineContainer.tsx) — renders `consentPageDefinition` as a sibling overlay of whichever page is showing.

### The consent page

Consent UI is **not** in React. An app points `properties.consentPage` at one of its own pages and builds the box with components; the platform renders it over every page until the visitor answers. Three things about that page are not obvious:

1. **Its `onLoad` is run by `RenderEngineContainer`, not by `Page`.** `Page` only runs its own onLoad when `Store.urlDetails.pageName` matches its context, which is never true for an overlay. The shell page has the same problem and is handled the same way.
2. **Root must be a zero-size, `pointer-events: none` anchor** (`position: fixed`, `width/height: 0`, high `z-index`), with each visible box `position: fixed` and `pointer-events: auto`. Otherwise it covers the page underneath.
3. **Drive which box shows from a single store key.** Two `SetStore` writes racing (hide bar, show panel) can interleave with a re-seed and leave both hidden. The shipped pages use one `Page.view` key with values `bar` / `panel`.

Consent belongs to the **browser**, not the account: it survives sign out, and signing in neither grants nor revokes it. An app whose terms of service already cover measurement should set `consentRequired: false` rather than try to infer consent from a login.

### Application properties

| key | default | effect |
|---|---|---|
| `analytics.enabled` | `false` | Master switch. False emits no snippet at all. |
| `analytics.consentRequired` | **`true`** | Sets `opt_out_capturing_by_default`. True means PostHog boots opted out and captures nothing until consent is granted — so an app that leaves this unset and ships no consent page captures nothing. |
| `analytics.autocapture` | `true` | Clicks/inputs. Heatmaps piggyback on this listener. |
| `analytics.capturePageviews` | `true` | `capture_pageview` |
| `analytics.capturePageleaves` | `true` | `capture_pageleave` |
| `analytics.heatmaps.enabled` | `false` | `enable_heatmaps`. Must be explicit — see the workaround below. |
| `analytics.consentCookieName` | `modlix_analytics_consent` | Name of the localStorage key / cookie. Client-side only. |
| `consentPage` | *(none)* | Top-level, **not** under `analytics`. Names a page in the same app. |

`analytics.sessionReplay.*` is **inert** — see below.

### Spring Cloud Config (`oci-config`)

Per-env analytics config:

```yaml
ui:
  analytics:
    ingestionHost: https://analytics-{env}.modlix.com
    posthog:
      projectApiKey: phc_<env-specific>
```

Files: `application-oci{dev,stage,prod}.yml` in the `oci-config` repo.

### Deploy infra (`oci-config/scripts/`)

- `{dev,stage,prod}-analytics/composer/posthog/` — docker-compose, .env, Caddyfile, README.md, keepup.sh
- `cloudflare/analytics-proxy/` — Cloudflare Worker that proxies `analytics{,-dev,-stage}.modlix.com` to the VM origins. Static paths (`/static/`, `/array/`) are edge-cached; ingestion paths (`/e/`, `/i/`, `/s/`, `/capture/`, `/decide/`, `/flags/`, `/engage/`, `/track/`) are passthrough.

## Critical workarounds (read before touching)

### 1. We do not record sessions

Session replay was built and then deliberately withdrawn. Both snippet generators now hard-code `disable_session_recording: true` rather than deriving it from a toggle, so **no application document can switch recording back on** — an `analytics.sessionReplay` block is inert. The consent code touches no recording API.

What went with it: the `$session_recording_remote_config` bootstrap (a workaround for the SDK's `get ws()` gate, which `/decide` would normally satisfy but our hobby self-host 403s), `maskAllInputs`, `sampleRate`, and the two recording methods in the snippet stub.

**What is deliberately still standing**, parked for a later decision rather than overlooked:

- `SessionReplayList` and `SessionReplayPlayer` components in `nocode-ui`, plus their catalog registration. Nothing but `appbuilder/docs` and `appbuilder/componentBook` references them.
- The replay surface in [AnalyticsService.java](../../ui/src/main/java/com/fincity/saas/ui/service/AnalyticsService.java) — recordings list, tenant-ownership check, sharing-token and embed-URL builder.
- The docs pages under `modlix-apps/docs-content/pages/platform/`, which still describe replay as available.
- On the analytics VMs: the `replay-capture`, `ingestion-sessionreplay` and `recording-api` containers, and the `{dev,stage,prod}-posthog` OCI buckets, which still hold everything recorded to date.

So the surface reads as supported while nothing feeds it. Decide whether to remove it or revive it before trusting either half.

`advanced_disable_flags: true` stays in the init options — it silences the `/decide` 403 noise and is what makes the heatmaps flag below necessary.

### 1b. Heatmaps need `enable_heatmaps: true`

PostHog's heatmap collector (`Heatmaps.isEnabled`) checks `config.enable_heatmaps` first and falls back to `persistence.props['$heatmaps_enabled_server_side']`. The server-side flag is populated by `/decide`, which we have disabled (`advanced_disable_flags: true`) — so without the explicit config flag, no `$$heatmap` events are ever sent and the PostHog UI shows empty heatmaps.

**The fix** (in both `IndexHTMLService.java` and `htmlRenderer.ts`): pass `enable_heatmaps: true` in the init options. We expose it as the per-app toggle `analytics.heatmaps.enabled` — opt-in (defaults to `false`); apps that want heatmaps must set it explicitly in the application document.

Heatmaps also depend on autocapture being enabled (default true) — heatmap rage-click and click-position data piggybacks on the autocapture click listener.

### 2. PostHog preflight wizard kafka/plugins patch

PostHog hobby's preflight wizard fails for `kafka` and `plugins` checks (the underlying probes don't work in non-cloud mode). This blocks the initial setup wizard.

**The fix** is applied at every web container start by `/home/opc/composer/posthog-hobby/compose/start` on each analytics VM (host-mounted into the container as `/compose/start`). The script idempotently sed-patches `/code/posthog/views.py` to set `"plugins": True,` and `"kafka": True,`. Same patch is also in `/compose/temporal-django-worker`. Backups are saved as `*.bak`.

If you upgrade PostHog and views.py shape changes, the sed regex in the start script may need updating.

### 3. OCI Object Storage doesn't support trailing checksums

Only matters if session replay is ever revived — nothing writes these blobs now. Kept because the override is still in place on the VMs.

PostHog uploads session-replay blobs via boto3, which by default uses `Transfer-Encoding: chunked` with CRC32C trailing checksums. OCI rejects these with `MissingContentLength`.

**The fix** is in `docker-compose.override.yml`:

```yaml
AWS_REQUEST_CHECKSUM_CALCULATION: WHEN_REQUIRED
AWS_RESPONSE_CHECKSUM_VALIDATION: WHEN_REQUIRED
```

These env vars force boto3 to fall back to plain `Content-Length` unless the operation explicitly demands a checksum. Applied to: `web`, `worker`, `temporal-django-worker`, `recording-api`, `capture`, `replay-capture`, `ingestion-sessionreplay`.

The `.env` variables that drive this override are named `OBJECT_STORAGE_*` (NOT `OCI_S3_*`) — the override file uses standard PostHog variable names.

### 4. SSR `mergeConfigs` must explicitly handle `analytics`

`configLoader.ts` `mergeConfigs()` only merges branches it explicitly knows about. If analytics config silently disappears in SSR-rendered HTML, check that the `analytics` branch is in the merge function.

## Deploy

### Code changes (analytics snippet, consent plumbing, etc.)

Standard branch promotion: `feature/x` → `master` (PR) → release branches:

| Repo | Stage trigger | Prod trigger |
|---|---|---|
| `nocode-saas` (ui module) | push to `oci-stage` | push to `oci-production` |
| `nocode-ui` (ssr) | push to `cf-stage` | push to `cf-production` |

Both are GitHub Actions CI: build Docker → push to OCIR → SSH to app VM → `keepup.sh deploy <service> <image>` (blue-green).

After deploy, **flush the Redis HTML cache** on the app VM, otherwise stale rendered HTML will mask your changes:

```bash
# pseudo — actual command depends on your Redis client
redis-cli FLUSHDB
```

### PostHog stack changes (compose, .env, Caddyfile, start scripts)

The PostHog VM scripts live in `oci-config/scripts/{dev,stage,prod}-analytics/composer/posthog/`. To roll out a change:

1. Edit the script(s) in `oci-config`.
2. SCP to the target VM into `/home/opc/composer/posthog-hobby/`.
3. `cd /home/opc/composer/posthog-hobby && sudo docker compose up -d` (only the affected services).

The `start` and `temporal-django-worker` scripts are host-mounted, so editing them on disk takes effect on the next container start — no rebuild required.

### Cloudflare Worker

Source in `oci-config/scripts/cloudflare/analytics-proxy/`. Deploy with `wrangler deploy`.

## Verifying after deploy

1. Open an app domain (e.g. `dev.leadzump.ai`) in a clean profile. The consent box should appear over the landing page; accept it.
2. In the browser console, confirm the decision was stored and applied:
   ```js
   localStorage.getItem('modlix_analytics_consent')  // {"status":"granted","categories":{...},...}
   posthog.has_opted_in_capturing()                  // true
   ```
3. PostHog UI → Activity → confirm events flow with `app_code`, `url_client_code`, `client_code`, `page_name` super properties.
4. Reload. The box must not come back.
5. Reject in a second clean profile and confirm no events arrive for it.

If the box never appears, in order: is `analytics.enabled` true and `consentRequired` not `false`; does the app have `consentPage` set and does that page exist; did `Store.application.properties.consentPageDefinition` actually arrive (the server inlines it, so a stale HTML/app cache will hide it — flush Redis).

## Open / future work

- **Decide the fate of the replay pipeline** — see workaround 1. Recording is off; the components, `AnalyticsService` endpoints, docs pages, VM containers and OCI buckets are all still standing. Parked deliberately.
- **Settings UI** in AppBuilder + SiteZump (no-code panels) for the per-app analytics toggles. Today these have to be set directly on the application JSON.
- **`marketing` consent drives nothing.** The category is stored and readable, but no advertising pixel consumes it — so an app that offers the toggle is making a promise the platform does not keep. Meta CAPI is the natural first consumer.
- **`appbuilder` still sets `consentRequired: true`.** Its terms of service arguably already cover product analytics, in which case `false` is the honest setting and the box should not show there at all.
- **Stop unused MinIO + SeaweedFS containers** on the analytics VMs (we use OCI Object Storage; the local stores are wasted RAM).
- **Worker container preflight patch** — currently only `web` and `temporal-django-worker` self-heal on restart. The `worker` container's CMD is baked into the image. If the preflight check ever matters there, add a `command:` override in `docker-compose.override.yml`.
- **Rotate PostHog Personal API tokens and Cloudflare API token** — the bootstrap tokens we generated during initial setup should be rotated.
