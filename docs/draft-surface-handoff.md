# Draft Surface: what is built, and what is left

Handoff for follow-on sessions in `nocode-ui`, `nocode-ai`, `modlix-apps` and the docs site.
The backend is complete and tested. Everything below the "Still to do" line is not.

## The model in one page

An app has two surfaces.

- **Live** is the published app at its normal hostname.
- **Draft** is a complete parallel copy at its own hostname: its own definitions, its own
  schemas, its own data. Publishing promotes draft definitions to live. Draft data is kept,
  never promoted.

Two separate mechanisms, deliberately not one:

| | Driven by | Where |
|---|---|---|
| **Which surface a read sees** | the ambient `x-draft` flag | set by the gateway from the resolved hostname, never by a caller |
| **Where a DEFINITION write goes** | an explicit `?draft=true` on the call | the caller names its target |
| **Where a DATA write goes** | the ambient flag | `AppDataController` has no draft parameter |

Keeping definition writes explicit matters: a page running *on* the draft surface doing an ordinary
`UIEngine.SendData` PUT must not be silently diverted into a draft it never asked for. App data is
the deliberate exception, and it is what makes the draft surface a usable sandbox rather than a
read-only preview.

### The write matrix

| Call | Effect |
|---|---|
| `PUT /api/ui/pages/{id}` | Live, unchanged from before this work |
| `PUT /api/ui/pages/{id}?draft=true` | Writes a `Draft` row. Live document untouched, nothing evicted |
| `POST /api/ui/pages` | Live and published, unchanged |
| `POST /api/ui/pages?draft=true` | Live document with `published = FALSE`. Real id, invisible on the live surface |
| `GET /api/ui/pages/{id}?draft=true` | Draft content if present, else live. Same access check as a live read |
| `POST /api/ui/pages/{id}/publish` | Applies the draft, clears the flag, deletes the row |
| `DELETE /api/ui/pages/{id}/draft` | Discards |
| `GET /api/ui/publish/app/{appCode}/pending` | Everything with pending work, by type |
| `POST /api/ui/publish/app/{appCode}` | Publishes all of it |

**Creation is never drafted.** `POST ?draft=true` still writes a real live document, only marked
unpublished. Drafting creation would mean every create returns a `Draft` id instead of a real
object id, which breaks `PATCH /{id}/components/{key}`, version history, `createForClient`,
`UIIndexService` (so the object vanishes from the builder tree), app properties naming a page,
and the AI agent's `build_page` which creates then immediately uses the returned id.

`GET /api/ui/page/{name}` (the runtime route) **ignores** a `draft` query parameter entirely. It
switches on the header alone. That route is public, serves anonymous traffic, and returns
cacheable ETagged responses, so it must not be switchable by anything a caller controls.

## Backend: done

- **`Draft` collection** in `commons-mongo`, unique on `(objectAppCode, objectType, objectName, clientCode)`.
  Holds the entity as sent, not a delta, so publish runs the ordinary update pipeline over it.
- **Publish routes through `service.update()`**, never `repo.save`. Eviction in this codebase is
  layered across per-service `update()` overrides, so a base-class publish writing to the
  repository would skip `ApplicationService.evictAll` (index HTML, manifest, `cacheProperties`),
  the Style/Theme OUI caches and `URIPatternCache`, and publishing an Application would appear to
  do nothing.
- **`Boolean published`** on `AbstractOverridableDTO`. `null` means legacy and published, so no
  migration. Filtered in `readIfExistsInBase` **before** its `size()==1` early return, filtered in
  `URIPathRepository.findAllNamesByAppCodeAndClientCode` separately, since the URI matcher builds its
  candidate list there and never goes through `readIfExistsInBase` (an unpublished path left in that
  list would match first and shadow a live one into an empty response), and reset
  in `createForClient`.
- **`isDraftable()`** is on for all seven UI services and off for `commons-core`.
- **Flag plumbing**: `LogUtil.DRAFT_KEY` / `LogUtil.isDraft()`, the `JWTTokenFilter` context write
  (one edit covering every service on `commons-security`), and the four `feignInterceptor()` beans.
- **Gateway** strips `x-draft` from every request, then sets it only from a resolved `DRAFT` row.
  New `getClientNAppCodeNType` endpoint returns the surface alongside the codes; the old
  `getClientNAppCode` is untouched so a rolling deploy is safe.
- **`security_client_url.URL_TYPE`** (`V82`), plus `GET`/`POST /api/security/clienturls/draft` to
  read and mint. Minting is a rotate, so it revokes the previous link, and requires
  `hasWriteAccess` on the app, not just `Client_UPDATE`.
- **Caches**: `_DRAFT`-suffixed definition cache; a `d` prefix on the existing `lg-`/`nlg-` ETag
  marker so the OUI caches need no rename; `no-store` on draft responses instead of seven days.
- **Draft data**: `<clientCode>_<appCode>_draft`, same collection names, with the flag in the
  index-provisioning cache key.
- **KIRun** needs nothing: every `ReactiveFunction.execute()` stays on one pipeline, so
  `Mono.deferContextual` already sees the flag.

**Tests: 2390 passing.** `ui` 57, `core` 21, `security` 2312. Both `ui` and `core` gained a Mongo
Testcontainer integration harness that did not exist before.

## Configuration to set per environment

```yaml
security:
  draftUrlSuffix: ".dev.modlix.com"   # leading dot; blank leaves draft URLs unmintable
```

Nothing else. A generated host is a single DNS label of lowercase alphanumerics, so an existing
`*.<env>.modlix.com` wildcard covers it with no new DNS record and no new certificate. SSL
issuance is keyed on an explicit `URL_ID`, not a sweep over client URLs, so a `DRAFT` row triggers
no ACME request.

---

## Still to do

### 1. `modlix-apps` (appbuilder app definitions, not code)

This is the largest remaining piece and it is all data. Per the ownership rule, these go in the
generator scripts, not through the page editor or MCP, or they get reverted.

- **`editPage` event function `savePageOverride`**: the `sendPage` step's url becomes
  `/api/ui/pages/{{...}}?draft=true`. The POST branch stays as it is unless you want new pages
  created unpublished, in which case it also takes `?draft=true`.
- **`editPage` page load**: read with `?draft=true` so a reopened editor shows unsaved work.
- **New `onPublish` event function**: `POST /api/ui/pages/{{Page.pageDefinition.id}}/publish`.
  **The button already exists.** `onPublish` is declared at `pageEditorProperties.ts:53`,
  `publishFunction` is at `LazyPageEditor.tsx:296`, and `DnDTopBar.tsx:1023` renders it whenever
  the handler prop is present. It is invisible today only because the app never configures it.
- **`onSavedVersions`** is similarly declared and unwired. `Version` rows now distinguish a plain
  save from a publish, so both hooks can be filled.
- A "pending changes" affordance somewhere in the builder, from
  `GET /api/ui/publish/app/{appCode}/pending`.
- A way to mint and copy the draft link, from `POST /api/security/clienturls/draft`.

### 2. `nocode-ui` client: mostly done, one thing left

Already done: `globalThis.isDraftMode` (read from the server-stamped `data-draft` attribute, not
from the URL), `DraftBanner`, and SSR resolving the surface from the hostname.

Note on SSR specifically: an earlier version forwarded an `x-draft` header from SSR to the gateway.
**That never worked.** `GatewayFilter` strips `x-draft` from every request including the SSR's own
server-to-server hop, then re-derives the surface from `gateway-server:8080`, which never matches a
DRAFT row. A draft host therefore got a live pre-render cached under a draft key. SSR now forwards
`X-Forwarded-Host` (which `GatewayFilter.getSchemeHostPort` already reads) and takes its own cache
dimension from `getClientNAppCodeNType`, leaving the gateway the single authority.

Left: nothing required. Note deliberately that **no axios interceptor was added**. The gateway
stamps by hostname and editor writes carry an explicit query parameter, so a client-sent `x-draft`
would be stripped at the gateway anyway. Do not add one.

### 3. `nocode-ai`

- The AppBuilder agent's page tools write through `/api/ui/pages`. Decide whether agent edits
  should draft. If yes, add `?draft=true` to the write in `app/agents/appbuilder/tools/modlix/_page_ops.py`
  and `build_page.py`, and expose a publish tool.
- `app/core/tools/draft_registry.py` already holds agent writes for objects the user has open.
  That is a *client-held* draft and is a different mechanism from this one. Worth deciding whether
  they should converge; today they do not conflict, because the registry intercepts before the
  HTTP call.
- `PATCH /{id}/components/{key}` and `PUT /{id}/events/{name}` are **live-only** and were left that
  way. They are MCP and agent endpoints, and drafting them correctly means merge-over-base, patch,
  re-extract, which is the whole update pipeline rather than a map mutation. If the agent should
  draft, this needs designing.

### 4. Docs

- The two surfaces and the read/write split (the table at the top of this file).
- Publish semantics: definitions promote, draft data is kept and never promoted.
- That draft data is real data in a real database that persists indefinitely, and needs a
  retention story and an explicit clear action. **Not built.**
- The draft link is a bearer credential: anyone with the URL sees unpublished work. Rotating
  revokes it.

## What the draft surface does NOT isolate

Decided deliberately, not oversights. The draft surface is a preview of **definitions** plus an
isolated **data** namespace. Nothing else is separated, because `isDraftable()` is false for
`Connection`, `Template`, `EventDefinition`, `EventAction`, `Notification` and `CoreFunction`, so it
runs on live credentials and live endpoints, and nothing in `security`, `files`, `notification`,
`entity-processor` or `worker` reads the flag at all.

- **Email and notifications** go to the real user directory.
- **Entity processor** is outside the draft system: WhatsApp, Exotel, ad-platform conversions.
- **Files** are outside it: the draft surface reads and writes the **production bucket** at the
  production path, keyed by client code only with no app or surface dimension.
- **REST callouts and `CoreServices.AI.Chat`** use live endpoints and live credentials, and cost real
  money.

Two consequences worth stating rather than leaving to inference:

- **A draft page can overwrite a live production file** through `override=true`. Unlike an unwanted
  email, that destroys something.
- **The draft host serves anonymous traffic**, gated only by an unguessable hostname. The population
  that can trigger any of the above is "anyone with the link", not "a trusted builder". The mint gate
  is the lever if that is not the intended audience.

## Draft storage data

- Draft rows live in `<clientCode>_<appCode>_draft`; collection names are identical on both surfaces,
  so publishing a storage never renames or moves anything.
- **Draft rows are metered.** They count toward `core.storage.rows` and `FREE_STORAGE_ROWS` and are
  billed like live rows. The split is not reported separately, so a bill can move because someone
  loaded test data into a sandbox. `estimatedRowCount` is the one place that must NOT consult
  `LogUtil.isDraft()`: it runs from a scheduled job with no request, so it names both databases
  explicitly.
- **The draft marker crosses the message queue** as a field on `EventQueObject`. Before this, an event
  raised by a draft-surface write executed with `isDraft() == false`, so a `CALL_CORE_FUNCTION` action
  writing through `CoreServices.Storage` landed in the **live** database. Worth knowing that was once
  true.
- **Nothing drops a `_draft` database.** Deleting an app orphans it, as it already orphans the live
  one. There is still no retention story and no clear action. **Not built.**

## Edge and ingress facts, corrected

- **`oci-config` contains no nginx configuration** and never has. The only nginx in the workspace is
  local-dev in the `dbs` repo.
- Internal endpoints are denied at the edge by a **regex**, `location ~/internal { deny all; }`,
  evaluated before `~ /api/`. There is no explicit deny list, so a new `/internal/` route needs no
  edge change. Note this is a different layer from the Spring
  `pathMatchers("(.*internal.*)")` rule, which **is** dead because that takes a PathPattern, not a
  regex.
- Nothing in these repos routes public traffic to the SSR: no route in `gateway.yml`, SSR is not in
  Eureka, and the dev nginx declares an `ssr_service` upstream it never uses. How SSR receives
  production traffic is not answerable from here.

## Known limitations, all commented at the call site

1. **Draft substitution is for the document being served, not every ancestor in the override
   chain.** A base client's draft shows on its own draft surface but does not re-merge into a
   derived client's. Doing that correctly means re-deriving a delta from the draft and folding it,
   and getting it subtly wrong would silently resurrect keys a draft had deleted.
2. **Nothing reconciles a draft with a live edit made after the draft was taken.**
   `Draft.baseVersion` plus the existing optimistic-lock check rejects the publish and leaves the
   draft intact; it does not merge.
3. **`Notification`'s second compound index is not unique**, though it was declared so. It was
   never created (name collision with the first), so the constraint has never been enforced, and
   enforcing it now would mean at most one notification per type per app per client, which existing
   data very likely violates. Needs a data audit before tightening.
4. **Local Flyway is drifted**: history stops at V80 while V81 and V82 exist on disk. V82 was
   applied by hand locally and `flyway_schema_history` was left alone, because faking a row there
   could mask a migration that genuinely needs running.

## Pre-existing bugs fixed along the way

- **`getMergedSources` dropped the root of any override chain three or more deep** and returned the
  caller's own entity, so `extractOverride` diffed an object against itself and a `PUT` on a 3-deep
  override **saved an empty document**. Fixed and pinned by tests in both `ui` and `core`.
- **`Connection` and `Notification`** each declared two `@CompoundIndex` under one name, so Mongo
  rejected the second and it never existed. A reflection test now fails on any recurrence.
- **Seven `IFeignSecurityService` methods** were reachable by another method's property key
  (`appInheritance` under `security.feign.hasWriteAccess`, and six more).
