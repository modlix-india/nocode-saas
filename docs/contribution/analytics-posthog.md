# Analytics (PostHog) — Self-Hosted

This document captures the end-to-end analytics architecture so anyone (or anyone's AI) can pick up the work without re-discovering it.

## Why self-host

We host PostHog ourselves so that user-facing event capture and session replay live entirely on Modlix infrastructure (no third-party data egress). The platform builds many apps for many tenants — analytics has to be tenant-aware, ad-blocker-resilient, and consent-respecting from day one.

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
  - **App-level** (per-application toggles): `analytics.enabled`, `analytics.sessionReplay.enabled`, `analytics.heatmaps.enabled`, `analytics.consentRequired`, `analytics.autocapture`, `analytics.capturePageviews`, `analytics.capturePageleaves`, `analytics.sessionReplay.maskAllInputs`. All sub-toggles are opt-in (default false).
  - **Critical:** keys/URLs are NEVER stored on the application document. Only user-facing toggles.

### Frontend (`nocode-ui`)

- [`ui-app/ssr/src/render/htmlRenderer.ts`](../../../nocode-ui/ui-app/ssr/src/render/htmlRenderer.ts) — same snippet generator, server-side. Mirrors the Java implementation byte-for-byte.
- [`ui-app/ssr/src/config/configLoader.ts`](../../../nocode-ui/ui-app/ssr/src/config/configLoader.ts) — reads `ui.analytics.*` from Spring Cloud Config; merged in `mergeConfigs()` (must include the `analytics` branch — easy to miss).
- [`ui-app/client/src/App/AnalyticsConsentBanner.tsx`](../../../nocode-ui/ui-app/client/src/App/AnalyticsConsentBanner.tsx) — React component that:
  - Subscribes to `STORE_PREFIX.application`, `STORE_PREFIX.auth.user`, `STORE_PREFIX.auth.client` via `addListenerAndCallImmediately` (NOT `getStore()` — that's expensive).
  - Calls `posthog.register({ app_code, url_client_code, client_code })` super properties for per-app filtering.
  - Calls `posthog.identify(userId)` on login, `posthog.reset()` on logout.
  - Manages opt-in / opt-out via `posthog.opt_in_capturing` / `opt_out_capturing` and `start/stopSessionRecording`.
  - Stores consent in localStorage with cookie fallback (`modlix_analytics_consent`).

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

### 1. Session replay needs a manual `$session_recording_remote_config` bootstrap

PostHog SDK v1.372.x has a `get ws()` getter that gates session recording start. It checks `_instance.get_property("$session_recording_remote_config")` for `enabled: true`. That property is normally populated by `/decide` (or `/flags?config=true` in newer SDKs).

In our hobby self-host, `/decide` 403s and the SDK doesn't request `?config=true`, so the property is never set and recording never starts — even after `posthog.opt_in_capturing()` + `posthog.startSessionRecording()`.

**The fix** (in both `IndexHTMLService.java` and `htmlRenderer.ts`):

1. Set `advanced_disable_decide: true` in init options to silence the 403 noise.
2. Use the `loaded` callback to `register` the property manually:

```js
posthog.init(KEY, {
  ...options,
  advanced_disable_decide: true,
  loaded: function(ph) {
    ph.persistence.register({
      '$session_recording_remote_config': {
        enabled: true,
        recorderVersion: 'v2',
        endpoint: '/s/',
        sampleRate: null,
        linkedFlag: null,
        urlBlocklist: [], urlTriggers: [], eventTriggers: []
      }
    });
    ph.sessionRecording.startIfEnabledOrStop();
  }
});
```

If session replay stops working again, **first** check whether the SDK upgraded and changed the property name or shape.

### 1b. Heatmaps need `enable_heatmaps: true` (same root cause)

PostHog's heatmap collector (`Heatmaps.isEnabled`) checks `config.enable_heatmaps` first and falls back to `persistence.props['$heatmaps_enabled_server_side']`. The server-side flag is populated by `/decide`, which we have disabled (`advanced_disable_decide: true`) — so without the explicit config flag, no `$$heatmap` events are ever sent and the PostHog UI shows empty heatmaps.

**The fix** (in both `IndexHTMLService.java` and `htmlRenderer.ts`): pass `enable_heatmaps: true` in the init options. We expose it as the per-app toggle `analytics.heatmaps.enabled` — opt-in (defaults to `false`); apps that want heatmaps must set it explicitly in the application document.

Heatmaps also depend on autocapture being enabled (default true) — heatmap rage-click and click-position data piggybacks on the autocapture click listener.

### 2. PostHog preflight wizard kafka/plugins patch

PostHog hobby's preflight wizard fails for `kafka` and `plugins` checks (the underlying probes don't work in non-cloud mode). This blocks the initial setup wizard.

**The fix** is applied at every web container start by `/home/opc/composer/posthog-hobby/compose/start` on each analytics VM (host-mounted into the container as `/compose/start`). The script idempotently sed-patches `/code/posthog/views.py` to set `"plugins": True,` and `"kafka": True,`. Same patch is also in `/compose/temporal-django-worker`. Backups are saved as `*.bak`.

If you upgrade PostHog and views.py shape changes, the sed regex in the start script may need updating.

### 3. OCI Object Storage doesn't support trailing checksums

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

### Code changes (analytics snippet, consent banner, etc.)

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

1. Open an app domain (e.g. `dev.leadzump.ai`), accept the consent banner.
2. PostHog UI → Activity → confirm events flow with `app_code`, `url_client_code`, `client_code` super properties.
3. Replay → confirm sessions are listed (give it ~30s after page interaction).
4. If replay doesn't start, in browser console:
   ```js
   posthog.persistence.get_property('$session_recording_remote_config')
   posthog.sessionRecording.bs   // should be 'active' or 'lazy_loading', not 'disabled'
   ```

## Open / future work

- **Settings UI** in AppBuilder + SiteZump (no-code panels) for the per-app analytics toggles. Today these have to be set directly on the application JSON.
- **Stop unused MinIO + SeaweedFS containers** on the analytics VMs (we use OCI Object Storage; the local stores are wasted RAM).
- **Worker container preflight patch** — currently only `web` and `temporal-django-worker` self-heal on restart. The `worker` container's CMD is baked into the image. If the preflight check ever matters there, add a `command:` override in `docker-compose.override.yml`.
- **Rotate PostHog Personal API tokens and Cloudflare API token** — the bootstrap tokens we generated during initial setup should be rotated.
- **PostHog SDK upgrades** — the session-recording bootstrap workaround is tightly coupled to SDK internals (`$session_recording_remote_config`, `recorderVersion: 'v2'`, `bs` field). On every SDK bump, retest replay end-to-end.
