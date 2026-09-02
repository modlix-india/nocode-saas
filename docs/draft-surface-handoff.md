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
| `GET`/`POST /api/core/publish/app/{appCode}` | The same two routes for core objects, same gate |
| `DELETE /api/ui/pages/{id}` | Deletes the object **and its draft**. Nothing is left pending |

`publishAll` reports every draft it looked at, including ones it could not ship. An object whose
document has gone missing under its draft comes back as `published: false` with an `error`, not as a
silently shorter list: a partial publish that quietly drops rows is worse than one that names them.

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
- **`isDraftable()`** is on for all eight `ui` services and all eleven `commons-core` ones. Core was
  off at first and that was wrong: a page's draft usually depends on a storage whose schema moved
  with it, or on a connection, template or event, so a change spanning both had to be published to be
  seen at all, which is the thing the draft surface exists to avoid. The five routes were always
  there, inherited from `AbstractOverridableDataController`, and the `core` `DraftService` bean
  already existed unused; only the flag was missing.
- **App-level publish is shared.** `AbstractPublishService` and `AbstractPublishController` live in
  `commons-mongo`; `ui` keeps `api/ui/publish` and `core` gains `api/core/publish` with the same two
  routes and the same `authorizedClientCode` gate (read for `pending`, write for `publishAll`).
  Subclasses supply only their service list, so the two cannot drift.
  A service missing from that list fails silently and invisibly: its drafts never appear in `pending`
  and `publishAll` never ships them, with nothing saying why. A test walks the Spring context to keep
  the core list complete.
- **The runtime read path is draft-aware for the whole app.** `readPage` was made draft-aware when
  the surface was built and the other four runtime reads were not, which is a bad shape to leave: the
  browser fetched a drafted page and then the LIVE application definition, styles, theme and
  functions to render it with, each cached seven days with no draft marker on the ETag.
  `readApplication`, `readStyle`, `readTheme` and `EngineController.function` now carry the surface.
  The first three take a `d-` marker on the uniqueId (they have no auth dimension to hang it on) and
  the marker is **re-derived, never taken from the client**, so a live eTag replayed against the
  draft host cannot write draft content under the live cache key. `function` gets `no-store` only: it
  has no server-side cache, so there is nothing to separate, and an unnecessary ETag marker is a way
  to answer 304 when the content actually differs.
- **`ApplicationService.readProperties` follows the surface.** This is where an app's own draft
  actually takes effect, since the client reads properties for its page list, theme and app-level
  settings. Two faults had to be fixed together: it called the 4-arg `readIfExistsInBase`, so
  `draft = false` and the draft was never substituted, and its cache name had no surface dimension,
  so both surfaces shared one entry and whichever was read first won. It now calls `readDrafted`
  (widened to protected for exactly this) and both eviction sites clear both surfaces.
- **Flag plumbing**: `LogUtil.DRAFT_KEY` / `LogUtil.isDraft()`, the `JWTTokenFilter` context write
  (one edit covering every service on `commons-security`), and the four `feignInterceptor()` beans.
- **Gateway** strips `x-draft` from every request, then sets it only from a resolved `DRAFT` row, and
  **grants the draft surface only when the request is actually for the app and client that hostname
  resolves to**.
  Setting the flag from the host was never enough on its own. The host decides which surface, the
  `appCode`/`clientCode` headers decide whose app, and the gateway fills those in only when the
  caller supplied none, so a supplied pair wins. Until the two were compared they were independent:
  a draft host for one app plus an `appCode` header naming another served the second app's
  unpublished work to anyone holding the first app's link, anonymously. Reproduced against the local
  stack, 110 draft components returned for an app whose draft link the caller did not hold.
  A mismatch **downgrades to live** rather than refusing: the request is still a legitimate read of a
  published page, and refusing would break the path-prefixed `/clientCode/appCode/page/` form and any
  tool that pairs a forwarded host with explicit codes.
  The `index` route branch now strips the header before returning too. No such route exists in either
  `gateway.yml`, so nothing takes that path today, but it returned before reaching the only strip,
  which put header forgery one config line away.
  New `getClientNAppCodeNType` endpoint returns the surface alongside the codes; the old
  `getClientNAppCode` is untouched so a rolling deploy is safe.
- **`security_client_url.URL_TYPE`** (`V82`), plus `GET`/`POST /api/security/clienturls/draft` to
  read and mint. Minting is a rotate, so it revokes the previous link, and requires
  `hasWriteAccess` on the app, not just `Client_UPDATE`.
- **DRAFT rows are invisible to the general URL readers.** `getLatestClientUrlBasedOnAppAndClient`,
  `getClientUrlsBasedOnAppAndClient` and `getClientUrls` all filter `URL_TYPE = 'LIVE'`.
  A draft host is a bearer credential, so an unfiltered read handed it to anything that asks an app
  for its URLs. Worse than the leak: the "latest" query orders by `UPDATED_AT` and takes one, and a
  freshly minted draft is by definition the most recently updated row, so it was picked as the app's
  canonical URL. `getDraftUrl` is the one reader that should see DRAFT and asks for it by name.
- **The draft host is `d<32 hex><security.appCodeSuffix>.modlix.com`**, and deliberately ignores the
  app's own live URL.
  The environment comes from `appCodeSuffix`, the marker this service and `IndexHTMLService` already
  use for every other URL they build, rather than a draft-specific key. A second per-environment
  setting meaning almost the same thing is one someone eventually forgets to move, and a draft host
  silently pointing at the wrong environment is a bad way to find that out. Blank is production and
  gives `d<hex>.modlix.com`. The `.modlix.com` base is a constant, because the point of a draft host
  is that the platform can serve it.
  Deriving it from the live URL was tried and reverted. The data says why: of 587 client URLs, 555
  are `*.<env>.modlix.com` and derive fine, but 32 are not, and those break in three different ways.
  `theorempro.in` and `gugul.ai` have two labels, so replacing the first gives `d<hex>.in` and
  `d<hex>.ai`, names directly under a public suffix. `dev.adzump.ai` and `dev.leadzump.ai` have the
  **environment** as their first label, so replacing it puts a dev app's draft host on the production
  apex. And `ashwa.fincity.com` or `app-dev.cityville.in` derive to domains the platform holds no
  wildcard certificate for. A fixed suffix has none of those failure modes.
  Blank suffix refuses to mint, checked **before** the existing row is touched: minting is a rotate,
  so a bad value would otherwise destroy a working link to replace it with a dead one.
- **Caches**: `_DRAFT`-suffixed definition cache; a `d` prefix on the existing `lg-`/`nlg-` ETag
  marker so the OUI caches need no rename; `no-store` on draft responses instead of seven days.
- **Draft data**: `<clientCode>_<appCode>_draft`, same collection names, with the flag in the
  index-provisioning cache key.
- **A deleted object takes its draft with it**, both on `delete(id)` and on the app-wide
  `deleteEverything`, alongside the version sweep. The draft is discarded by **name**, not by id,
  because name is the key the read path uses. This matters more than it sounds: `readDrafted` looks a
  draft up by `(type, appCode, name, clientCode)`, so an orphan left behind by a delete attached
  itself to any **new** object later created with the same name and served the dead draft's content,
  carrying the dead object's id. It also sat in `pending` permanently, and `publishAll` neither
  cleared nor reported it. Six tests pin this, including that the app sweep does not cross app
  boundaries.
  Both cleanups are **deliberately not gated on `isDraftable()`**. Nothing can write a draft for a
  non-draftable service today, so it is a no-op query for them, but "a deleted object never leaves a
  draft" is worth having unconditionally rather than contingent on a flag a later service could turn
  off with rows already stored. One indexed lookup on a delete is a fair price.
- **KIRun** needs nothing: every `ReactiveFunction.execute()` stays on one pipeline, so
  `Mono.deferContextual` already sees the flag.

**Tests: 3025 passing across all 22 modules, 0 failures.** The ones that carry this work are `ui` 88,
`core` 38, `security` 2319 and `notification` 4. Both `ui` and `core` gained a Mongo Testcontainer
integration harness that did not exist before, and `notification` had no tests at all before this.

Two conventions in that suite, both there because the first version of it went green over dead code:

- **Every test drives a service or a controller, never the repository.** No test may set `published`
  through `mongoTemplate.save()`; four early tests did, and were validating a state no production
  path could produce.
- **Anything about "which database received the bytes" is asserted against Mongo directly**
  (`listDatabaseNames` / `listCollectionNames`), not through the service that decides it. Going back
  through the deciding code proves nothing.

## Configuration to set per environment

**None.** Draft hosts reuse `security.appCodeSuffix`, which every environment already sets:

| `appCodeSuffix` | Draft host |
|---|---|
| `.local` | `d17c5afd…c01.local.modlix.com` |
| `.dev` | `d17c5afd…c01.dev.modlix.com` |
| blank (production) | `d17c5afd…c01.modlix.com` |

One lowercase alphanumeric label under the environment's domain, so the existing
`*.<env>.modlix.com` wildcard covers it with no new DNS record and no new certificate. SSL issuance
is keyed on an explicit `URL_ID`, not a sweep over client URLs, so a `DRAFT` row triggers no ACME
request.

There is no pre-flight refusal on a missing setting any more, and that is deliberate rather than an
omission: nothing can be missing. A blank `appCodeSuffix` is production, not an error.

**Local caveat**: `/etc/hosts` cannot wildcard, so a minted local host does not resolve until its
exact name is added there. The agent gets round it with `X-Forwarded-Host`, which the gateway already
reads; a human opening the draft in a browser needs the hosts line.

**Why this is not derived from the app's own live URL.** It is the obvious alternative and it does
not work. Counted over the 587 rows in `security_client_url`:

| Live URL | Derived draft host | Problem |
|---|---|---|
| `sitezump.dev.modlix.com` (555 rows) | `d<hex>.dev.modlix.com` | fine |
| `theorempro.in`, `gugul.ai`, `abc.com` | `d<hex>.in`, `d<hex>.ai`, `d<hex>.com` | under a public suffix, and not ours to mint |
| `dev.adzump.ai`, `dev.leadzump.ai` | `d<hex>.adzump.ai` | the first label is the ENVIRONMENT, so a dev app lands on the production apex |
| `ashwa.fincity.com`, `app-dev.cityville.in` | `d<hex>.fincity.com` | a domain with no Modlix wildcard certificate |

A per-environment suffix is one line of config and is always right. Deriving is no config and is
wrong for 5% of apps, in ways that only show up as a dead link after the mint has already rotated
the old one away.

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
- **`onSavedVersions`** is similarly declared and unwired, and **cannot be filled yet**. This file
  used to claim `Version` rows distinguish a plain save from a publish. They do not.
  `Version` carries `objectName`, `objectAppCode`, `clientCode`, `message`, `objectType`, `object`,
  `versionNumber` and `subElementKey`, and both writers in `AbstractOverridableDataService` build it
  identically. `publish()` goes through the same `update()`, so a publish's row differs from a save's
  only by the free-text `message`, which is caller-supplied and usually null. There is nothing to
  filter on.
  Making it real means a `published` boolean on `Version`, written from `publish()`. That is a
  separate change, deliberately not made here.
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

This still holds, and the page editor's preview did not change it. Making the editor canvases
draft-aware needed the surface to cover things no header can reach anyway — the iframe's own
document, the `<link>` to `api/ui/style`, `EventSource`, and every page the preview navigates to —
so it is carried the only way that reaches all of them, as **a second kind of hostname**. See
"The draft edit token" below.

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
- That draft data is real data in a real database, dropped only when the storage definition or the
  app is deleted. There is no age-based expiry and no manual clear action; a long-lived draft surface
  accumulates rows and bills for them.
- The draft link is a bearer credential: anyone with the URL sees unpublished work. Rotating
  revokes it.

## What the draft surface does NOT isolate

Decided deliberately, not oversights. The draft surface is a preview of **definitions** plus an
isolated **data** namespace, and nothing else is separated.

Note what changed: `Connection`, `Template`, `EventDefinition`, `EventAction`, `Notification` and
`CoreFunction` **are** draftable now, so their definitions do follow the surface. What their
definitions point AT does not. A drafted `Connection` still names a real endpoint with real
credentials, and a drafted `Notification` still reaches real people. Drafting the definition changes
which version of it runs, never where its side effects land, and nothing in `security`, `files`,
`entity-processor` or `worker` reads the flag at all.

- **Email and notification recipients** are the real user directory. The *definitions* do follow the
  surface (see below), but who receives the message does not: a draft notification reaches real
  people at their real addresses.
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

## Adding a draftable service

Worth reading before adding a service or a route, because the shape is the opposite of what it looks
like.

**The draft routes are inherited, not declared.** Every controller extending
`AbstractOverridableDataController` already exposes all five, so a new service gets
`PUT /{id}?draft=true`, `POST ?draft=true`, `GET /{id}?draft=true`, `POST /{id}/publish` and
`DELETE /{id}/draft` whether or not anyone thought about drafting. What decides is `isDraftable()`,
checked on each of the five service entry points, which answer **405** when it is false.

So a new service is non-draftable by default and safely so, and turning it on is one override. Two
things have to happen together:

1. `isDraftable()` returns true.
2. The service joins its module's publish list (`PublishService` for ui, `CorePublishService` for
   core). **Missing this fails silently and invisibly**: the object drafts fine, and then its drafts
   never appear in `pending` and `publishAll` never ships them, with nothing anywhere saying why.
   `CoreDraftableIntegrationTest.publishListIsComplete` walks the Spring context to catch it for
   core.

**Why leaving a service half-on is worse than leaving it off.** `createUnpublished` sets
`published = FALSE`, and `readIfExistsInBase` filters that out unless the read passes `draft = true`.
An object created unpublished on a service whose reads are not draft-aware is **permanently
invisible**: unpublishable (publish is refused for the same reason), and reachable only by id.

**Current state**: all eight `ui` services and all eleven `commons-core` ones are draftable.

## Draft conflicts: baseVersion is frozen

A draft records the live version it was taken FROM, and that number never moves for the life of the
draft. It is the whole mechanism behind limitation 2 below.

`upsert` used to re-stamp `baseVersion` on every save, so the optimistic-lock check on publish
compared a version against itself and always passed. The check existed and could never fire. The
sequence that matters is ordinary: someone drafts a page, someone else publishes a change to it live,
the first person keeps editing their draft. That second save rebased the draft onto the newer version
and the publish then silently overwrote the newer live content with work derived from an older copy.

Now the publish fails and the draft is left intact, so nothing is lost. Recoverable rather than
terminal: discard and save again takes the current live version as a fresh base.

**Where the conflict now happens, and what that means for the client.** There are now two, and they
answer different questions.

- **Live moved under the draft** is caught at **publish**, by `baseVersion` against the live version.
- **Someone else edited the same draft** is caught at **save**, by `Draft.version` against the
  `X-Draft-Version` the caller last read. Send the version, get a 412 when it has moved; omit it and
  the save is last-write-wins exactly as before, so nothing existing breaks.

`baseVersion` cannot do the second job and never could: a draft row is keyed on
`(app, type, name, clientCode)`, so it belongs to a client and two editors share it, and both of them
read the same live document and therefore send the same number.

The client has to move with this. The editor's existing conflict handling is built entirely around a
412 from the save call (`workspace.saveObject`: the `stale` branch on `Steps.send.error.status = 412`,
the `re`/`mkc`/`setc` steps, and the `conflictBar` with `forceSave`). Two things have to happen:

1. Keep the 412 path on save, but feed it from `X-Draft-Version`. Read the header on
   `GET /{id}?draft=true`, send it back as `?draftVersion=N` on the save. Recovery is a re-read,
   which is what the existing `re` step already does.
2. Add a second path for the publish conflict, which is new and has no UI at all. Recovery there is
   discard and re-save, not force.

Left as is, a user's first sight of a conflict is a publish failing with no affordance to resolve it,
and a colleague's draft edits disappearing with no error at all.

## Draft storage data

Read this section before touching anything that writes app data. "Draft data" here means Mongo app
data and nothing else: files, email, REST callouts and entity-processor are all on the live surface
(see the section above).

### Where it lives

`<clientCode>_<appCode>_draft`, a sibling database of `<clientCode>_<appCode>`. **Collection names
are identical on both surfaces** (the Storage's `uniqueName`), which is the whole reason publishing a
storage never has to rename or move anything. Version collections (`<uniqueName>_version`) are
draft-scoped the same way.

Both databases are created lazily by Mongo on first write. Nothing is provisioned up front, so an app
whose draft surface was never written to **has no draft database at all**, which is the normal case.
Anything enumerating these databases has to treat "absent" as empty rather than as an error;
`countRowsIn` does exactly that, and a missing draft database counts as zero rather than failing a
metering window.

### How a write lands there

Purely ambient, from `LogUtil.isDraft()` in the Reactor Context. This is the part that surprises
people: **`AppDataController` has no `draft` parameter on any of its routes.** A write on the draft
surface goes to the draft database because of the ambient flag alone. That is the deliberate
exception to the rule stated at the top of this file, and it is what makes the surface a usable
sandbox instead of a read-only preview.

The flag is set only by the gateway from the resolved hostname, and the gateway strips any inbound
value first, so a caller cannot forge it. It reaches queue consumers as a field on the message
(`EventQueObject`, `NotificationQueObject`), because a consumer runs on its own thread with no
inbound request to read a context from.

### Publish does not promote data

Definitions promote; **draft rows stay where they are**. There is no "copy my draft rows live"
operation, and none was asked for. The two data sets are independent from the moment the draft
surface is first written to. A draft surface is therefore not a rehearsal of a data migration, and
should not be described as one.

### Metering

- **Draft rows are metered.** They count toward `core.storage.rows` and `FREE_STORAGE_ROWS` and are
  billed like live rows. The split is not reported separately, so a bill can move because someone
  loaded test data into a sandbox. `estimatedRowCount` is the one place that must NOT consult
  `LogUtil.isDraft()`: it runs from a scheduled job with no request, so it names both databases
  explicitly. There is a comment saying so at the call site, because it reads like an inconsistency
  worth "simplifying" and is not.

### Events and notifications

- **The draft marker crosses the message queue** as a field on `EventQueObject`. Before this, an event
  raised by a draft-surface write executed with `isDraft() == false`, so a `CALL_CORE_FUNCTION` action
  writing through `CoreServices.Storage` landed in the **live** database. Worth knowing that was once
  true.
- **`NotificationQueObject` carries the same marker**, and it governs definition resolution only. The
  sender passes `x-draft` on its two `IFeignCoreService` lookups, so a notification raised from a
  draft page resolves the draft `Notification` and `Connection` documents and previews the template
  being worked on rather than the published one. The header is set on those two methods by hand and
  **not** through the notification module's Feign `RequestInterceptor`, which would have applied it to
  every client in the module including `IFeignSecurityService`: recipient resolution has to stay on
  the live directory. A test pins both halves, so moving it into the interceptor fails the build.

### Lifecycle and deletion

Created on first draft write. Dropped only by these two paths:

| Deleting | Drops | Path |
|---|---|---|
| a `Storage` definition | that storage's draft collection **and** its `_version` collection | `StorageService.delete` → `AppDataService.dropDraftStorageData` |
| an app | the whole `<cc>_<ac>_draft` database | `DeletionService.deleteEverything` → `AppDataService.dropDraftData` |

Both **name the draft namespace explicitly** rather than reading `isDraft()`, because deletion runs
on the live surface, where the ambient flag is false exactly when the draft namespace is wanted.

The **live** database is deliberately untouched in both cases. Orphaning live app data on delete
predates this work, and a delete that starts destroying customer data is a separate decision, not a
side effect of the draft surface. Both halves of that asymmetry are asserted, so it will not drift by
accident.

Two implementation notes that matter if you call these from anywhere new:

- The storage drop is **best effort**: the definition is already gone by then, so failing the whole
  delete would leave a worse state than an orphaned sandbox collection. Failures are logged, not
  propagated.
- `dropDraftStorage` opens with `getUsersContextAuthentication()`, so **with no security context the
  Mono is empty and nothing drops, silently.** It is only ever called from inside `StorageService`'s
  authenticated chain today. It is not safe to call from a scheduled job as written.

**What is retained.** There is no age-based expiry and no manual "clear draft data" action. A draft
surface that is never deleted keeps its rows indefinitely, and meters them.

### Test coverage of the deletion paths, and its gaps

Covered by `DraftDataNamespaceIntegrationTest`, both driving the real service and both asserting
against Mongo directly:

| Test | Asserts |
|---|---|
| `deletingStorageDropsDraftOnly` | draft collection gone, **live collection still present** |
| `deletingAppDropsDraftDatabaseOnly` | draft database gone, **live database still present** |

Both were confirmed to fail with the drop calls removed, so they are testing the behaviour rather
than passing incidentally.

Not covered, and worth knowing before relying on them:

- **The `_version` collection drop is executed but unverified.** The fixture storage is not
  versioned, so there is no `_version` collection for the test to look for.
- **The `isAppLevel` branch**, which keys the database off `urlClientCode` instead of `clientCode`.
  The fixture is not app-level.
- **A custom `appData` Connection.** The tests run against the default Mongo client, so the
  `getMongoClient(conn)` path with a real connection document is untested.
- **The error-swallowing paths** (`onErrorReturn(FALSE)` / the logged `onErrorResume`).

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
   `Draft.baseVersion` is frozen at first save and the existing optimistic-lock check rejects the
   publish, leaving the draft intact; it does not merge. Recovery is discard and re-save. See
   "Draft conflicts" above for why the check could never fire before.
5. **Two people drafting the same object can still overwrite each other if the client does not
   opt in.** `Draft.version` now exists and moves on every save, and `saveDraft` refuses with 412
   when the caller's expected version has moved on. The check is deliberately optional: omit the
   version and the old last-write-wins behaviour is unchanged, so no existing caller starts failing.
   Until the editor round-trips `X-Draft-Version`, the silent overwrite is still reachable.
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

## The draft edit token

A second hostname that yields DRAFT, added so the page editor's preview canvases render the draft
surface. The permanent draft link could not do that job:

- It is minted per (logged-in client, appCode) — `ClientUrlService.getDraftUrl` and `mintDraftUrl`
  both key off `ca.getClientCode()` — so it cannot express previewing an app in **another client's**
  context, which the editor's client picker allows.
- It is permanent and anonymous, which is right for sharing a link and wrong for a canvas that is
  open whenever somebody is editing.

### Shape

`t-<32 hex><appCodeSuffix>.modlix.com`, built by the same construction as `newDraftHost()` with a
`t-` prefix instead of `d`. 128 bits of `SecureRandom` as lowercase hex, so the label is valid by
construction and the existing `*.modlix.com` wildcard covers it: no DNS record, no certificate.

The token is stored, not signed — `security_draft_token` (V83), keyed on the 32 hex characters,
carrying `APP_CODE`, `CLIENT_ID`, `USER_ID` and `EXPIRES_AT`. A DNS label caps at 63 characters, so
a JWT was never an option, and a row buys real revocation in exchange.

Deliberately **not** a `SECURITY_CLIENT_URL` row. Three queries there already filter
`URL_TYPE = LIVE` to keep draft hosts out of the general URL readers, and the "latest URL" query
orders by last-updated and takes one — a token minted every time somebody opens the page editor
would keep winning that and become the app's canonical URL.

### Routes

- `POST /api/security/clienturls/draft/token?appCode=` — mint. Gated exactly like `mintDraftUrl`:
  app write access, not `Client_UPDATE`, no `@PreAuthorize`. Does **not** rotate, so two editor tabs
  on one app get a token each and neither invalidates the other.
- `POST /api/security/clienturls/draft/token/extend?token=` — heartbeat. Pushes `EXPIRES_AT`
  forward on the same row, scoped to the minting user, and evicts the gateway's cache. Never mints
  a replacement: the token **is** the hostname, so a new value would change the canvases' origin and
  reload all three, losing scroll position and everything the previewed page holds in its store.
- `GET /api/security/clienturls/internal/draft/token/resolve?host&appCode&clientCode` — what the
  gateway asks. Returns `(allowed, expiresAtEpochSeconds, appCode, clientCode)`. Under
  `/clienturls/internal/` specifically because that prefix is already in `SecurityConfiguration`'s
  permitAll list; the gateway calls it with no credentials.

### What the gateway does

`GatewayFilter.resolveCodesAndSurface` consults the host **alongside** the path when, and only when,
the first label matches `^t-[0-9a-f]{32}$`. That is the opposite of the permanent draft link, where
a path-prefixed URL is pinned LIVE — and the difference is the point. The path names the client
being previewed; the token names who is entitled to preview it, and the check is that the requested
client is the minting client or one it manages.

Codes come from the path, then the request headers, then the token itself. The header step is not
optional: SSR renders the shell by calling back through the gateway with explicit `appCode` and
`clientCode` headers and no path prefix.

`modifyRequest` is untouched. The strip-then-set is still the only place `x-draft` originates, and
`codesMatchResolved` still refuses to stamp when a supplied header disagrees with what resolved.

Two traps worth keeping in mind:

1. **The expiry is re-checked in the gateway, against the cached value.** `CacheService` has no
   per-entry TTL and the Caffeine backstop is `cache.local.expire-after-write-minutes` (60), twice a
   token's life, so caching a bare verdict would let a grant outlive its token by half an hour.
2. **The expiry crosses the wire as a String.** The tuple deserializer reads elements as plain
   `Object`, so epoch seconds arrive as an `Integer` and a declared `Long` blows up on the cast.

### What the client does

Nothing is injected. `LazyPageEditor` mints once per session and points the canvases at
`https://<host>/{appCode}/{clientCode}/page/{pageName}`; everything else follows from the hostname.
The `url` state stays a path so what the address bar edits and what personalization remembers stay
portable — a hostname belongs to one session, and a remembered one would come back stale.

The consequence to know about is that the canvases are now **cross-origin** to the editor:

- `contentWindow.location.reload()` and `history.back()`/`.forward()` became `EDITOR_RELOAD`,
  `EDITOR_HISTORY_BACK` and `EDITOR_HISTORY_FORWARD`, applied by each frame to itself.
- `determineRightClickPosition` no longer reads the iframe's rect out of `parent.window.document`;
  it sends raw `clientX/clientY` and `toMasterPosition` in the editor's `masterFunctions` does the
  arithmetic.
- Both message listeners now compare `event.source` against the window they expect. That needs no
  origin allowlist and cannot be spoofed, and it also closes the `throw` on an unrecognised message
  type, which a stranger could otherwise use to stop the editor.
- The canvas has its **own localStorage**, so it runs anonymous: `Store.auth` is empty and
  authority-based visibility renders the logged-out variant. Accepted deliberately rather than
  postMessaging the auth token in, which would be the injection this shape exists to avoid.

`DraftBanner` hides itself in design mode. The canvas genuinely is the draft surface and the shell
does stamp `data-draft`, but a banner pinned to the bottom of each of three canvases only covers
the page being worked on, and nobody in the page editor is confused about which surface they are on.

### Local development

`*.local.modlix.com` is already covered — the local nginx certificate carries that SAN and listens
`default_server` on 443 — but `/etc/hosts` cannot wildcard and the host is per-session. A one-time
`/etc/resolver/modlix.com` plus dnsmasq (`address=/local.modlix.com/127.0.0.1`) fixes it. Without
it the canvas is blank locally and the reason is not guessable.
