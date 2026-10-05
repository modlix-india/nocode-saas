# Aggregation support for app data storage

Status: IMPLEMENTED on `feature/blueprint`, 2026-10-02. Written as a proposal 2026-10-01.

Scope grew after the proposal was written: the three stale PRs in section 8 were folded in
rather than closed, on the grounds that each targets a capability that genuinely does not
exist. See section 11 for what was built and how it differs from this plan.

Scope: the `AppDataService` ecosystem in `commons-core`, its Mongo implementation, the
`api/core/data/**` controller in `core`, and the `CoreServices.Storage` KIRun function set.
No change to the JOOQ/SQL aggregation that already exists for entity-processor, and no change
to the analytics-engine, both of which are covered in section 2.

All file line references are as of 2026-10-01 and should be re-checked before relying on them.

---

## 1. The recommendation in one page

Add a single new operation, `aggregate`, alongside `readPage`, returning a
`Page<Map<String, Object>>` of **flat rows**. One row per group, group keys and measures
together at the top level, no nested `_id`.

That flatness is the whole design constraint and it comes from the consumer. The payoff for
this feature is the [Chart](../../../nocode-ui/ui-app/client/src/components/Chart/Chart.tsx)
component, which binds a plain array of objects. If the result flattens, a page goes from
"read ten thousand rows and fold them in KIRun" to one server call with no component change.
If it does not flatten, every consumer needs a reshaping step and the feature half-lands.

Everything else follows the shape of `readPage` deliberately: the same `AbstractCondition`
filter machinery for `$match`, the same `genericOperation` authorization on `Storage::getReadAuth`,
the same `getStorageWithKIRunValidation` storage fetch, the same `onSurface` draft handling on
the controller. The new surface area is a model class, a pipeline builder, and a validator.

**Date bucketing is number-only in this cut.** A date field must be stored as a number, epoch
seconds or epoch milliseconds, declared per query. String-dated storages cannot be charted
yet. Section 5 covers why, and it is not an arbitrary restriction.

**Data rows only.** Version and audit rows are out of scope, deliberately. Section 2.2 records
what that leaves on the table and the one pre-existing problem found while scoping it out.

---

## 2. What exists today

**There is no aggregation on app data.** `IAppDataService` is the whole contract: create,
update, read, readPage, delete, deleteByFilter, versions, storage drop, draft copy. The
controller at `core/src/main/java/com/fincity/saas/core/controller/connection/appdata/AppDataController.java`
mirrors it exactly. The KIRun set at `commons-core/src/main/java/com/fincity/saas/commons/core/functions/storage/`
has seven functions and none of them aggregate.

The only numeric rollup in the path is `AppDataService.estimatedRowCount` (line 456), a
whole-database row count for billing metering, not a query.

**Mongo's aggregation framework is already used, but only as a projection mechanism.**
`MongoAppDataService.applyQueryOnElements` (lines 697 to 721) switches from `find()` to
`aggregate()` when `query.fields` is set, with a fixed `$match → $sort → $project → $skip → $limit`.
No `$group` stage is ever constructed. The sort helper immediately below it (line 723) carries a
comment worth reading before touching the pipeline: an unsorted `Sort` produces an empty `$sort`
stage that the aggregation pipeline rejects, which `find()` tolerates.

**Aggregation exists elsewhere and is unrelated.** `AggregateFunction` (MAX/MIN/SUM/AVG/COUNT)
and `HavingCondition` live in `commons/src/main/java/com/fincity/saas/commons/model/condition/`,
but the only consumers are the JOOQ DAOs, `commons-jooq/.../AbstractDAO.java` line 292 and its
commons2 twin, plus `entity-processor/.../TicketDAO.java` line 457. Real reporting aggregation
lives in `entity-processor/.../analytics/` against relational tables. None of it touches app data.

### 2.1 A pre-existing 500 this work will attract

`commons/src/main/java/com/fincity/saas/commons/jackson/AbstractConditionDeserializer.java`
line 51 builds a `HavingCondition` from any payload carrying `aggregateFunction`, and line 39
builds a `GroupCondition` from one carrying `havingConditions`. Both are registered globally
through `CommonsSerializationModule` line 74.

`MongoAppDataService.filter()` (line 740) casts anything that is not a `ComplexCondition`
straight to `FilterCondition`. Neither `HavingCondition` nor `GroupCondition` extends it.

So an aggregate-shaped body sent to `POST api/core/data/{storage}/query` parses cleanly and
then throws `ClassCastException`, surfacing as a 500 rather than a 400. It is small to fix and
belongs in this change, because shipping aggregation invites people to send exactly that body.

### 2.2 Version and audit rows are out of scope

Versioning and auditing exist to record what happened to **data rows**, and aggregating that
history is not part of this work. `aggregate` targets the data collection only. There is no
`target` field on the query: an enum with one usable value would be a field that lies, the same
reason `distinct` is held back in section 10.

Two findings from scoping it out are worth keeping, because neither is this feature's problem
and both will outlive this document.

**There is no collection-level access to version history at all.** `readPageVersion`
(`MongoAppDataService.java` line 539) force-ANDs an `objectId EQUALS <id>` condition into every
query it runs, so the only question the version collection can answer is "what happened to this
one row". Anything across rows, such as edits per user per month, is unanswerable today by
construction rather than by omission. If that is ever wanted, note that version `createdAt` is a
real BSON date (line 418), unlike every timestamp in the data collection, so it would need none
of section 5's encoding machinery.

**The version collection has no indexes whatsoever**, and this is a live problem independent of
aggregation. `getVersionCollection` (line 972) resolves a database and returns
`getCollection(uniqueName + "_version")`; that is the entire method. The data path is handed
`storage.getIndexes()` and `getTextIndexFields()`, and the comment at line 644 notes that routing
through it is specifically what keeps index creation working. The version path is outside that
entirely, so there is no index on `createdAt`, none on `createdBy`, and none on `objectId`, which
is the one field every `readPageVersion` call filters on. The collection is also the larger of
the two for any actively edited storage, one row per write, with nothing pruning it and no TTL
anywhere. That most likely means version history is already slower than anyone has noticed. It
is listed in section 10 as a finding to hand off, not as work in this plan.

---

## 3. The model

New classes in `commons/src/main/java/com/fincity/saas/commons/model/`, beside `Query`.

```java
public class AggregateQuery implements Serializable {
    private AbstractCondition condition;      // $match
    private List<GroupByField> groupBy;       // empty or null = whole-collection rollup
    private List<Aggregation> aggregations;   // the measures
    private AbstractCondition having;         // post-group filter
    private Sort sort;
    private int size = 100;
    private int page = 0;
    private Boolean count = Boolean.FALSE;
}

public class Aggregation implements Serializable {
    private AggregateFunction function;       // reuses the existing enum
    private String field;                     // null permitted only for COUNT
    private String alias;                     // output key
}

public class GroupByField implements Serializable {
    private String field;
    private String alias;                     // defaults to field
    private DateBucketUnit bucket;            // null = group by exact value
    private DateEncoding encoding;            // required when bucket is set
    private String timezone;                  // IANA, defaults to UTC
}

public enum DateBucketUnit { YEAR, QUARTER, MONTH, WEEK, DAY, HOUR }
public enum DateEncoding   { EPOCH_SECONDS, EPOCH_MILLIS }
```

Two deliberate choices.

**`having` is typed `AbstractCondition`, not `HavingCondition`.** After `$group` the aliases
are ordinary field names, so plain `FilterCondition` and `ComplexCondition` work unchanged and
the entire existing condition vocabulary comes along free. `HavingCondition` is a JOOQ-shaped
thing where the aggregate sits inline in the condition, and it does not fit here. The validator
must reject a `HavingCondition` instance in this slot explicitly rather than silently misreading
it, since the deserializer will happily construct one.

**`AggregateFunction` is reused, not replaced.** Note the constraint this imposes: the switch at
`commons-jooq/.../AbstractDAO.java` line 303 is an exhaustive switch expression with no `default`,
and the commons2 twin is identical. Adding a constant to that enum, FIRST or LAST or STDDEV,
breaks compilation in both JOOQ DAOs. Any future extension has to grow those branches in the
same commit.

---

## 4. The Mongo pipeline

Interface addition:

```java
Mono<Page<Map<String, Object>>> aggregate(
        String clientCode, Connection conn, Storage storage, AggregateQuery query);
```

`Page` keeps it symmetric with `readPage` and gives paging over groups, which matters as soon
as someone groups by a high-cardinality field.

### 4.1 Worked example

Request:

```json
{
  "condition": { "field": "status", "operator": "EQUALS", "value": "PAID" },
  "groupBy": [
    { "field": "orderDate", "alias": "month", "bucket": "MONTH",
      "encoding": "EPOCH_SECONDS", "timezone": "Asia/Kolkata" },
    { "field": "region" }
  ],
  "aggregations": [
    { "function": "SUM",   "field": "amount", "alias": "revenue" },
    { "function": "COUNT",                    "alias": "orders"  }
  ],
  "having": { "field": "revenue", "operator": "GREATER_THAN", "value": 10000 },
  "sort": [{ "property": "month", "direction": "ASC" }],
  "size": 100
}
```

Pipeline:

```js
[
  { $match: { status: "PAID" } },

  { $group: {
      _id: {
        month: { $dateTrunc: {
                   date: { $toDate: { $multiply: ["$orderDate", 1000] } },
                   unit: "month",
                   timezone: "Asia/Kolkata" } },
        region: "$region"
      },
      revenue: { $sum: "$amount" },
      orders:  { $sum: 1 }
  }},

  { $project: {
      _id: 0,
      month:  { $toLong: { $divide: [ { $toLong: "$_id.month" }, 1000 ] } },
      region: "$_id.region",
      revenue: 1,
      orders: 1
  }},

  { $match: { revenue: { $gt: 10000 } } },
  { $sort:  { month: 1 } },
  { $skip: 0 },
  { $limit: 100 }
]
```

The `$match` stage is built by the **existing** `filter(storage, condition)` with no changes,
so indexes still apply to it and the whole filter vocabulary is inherited.

With `groupBy` empty, `_id` is `null` and the result is exactly one row.

### 4.2 Semantics worth pinning down now

- **COUNT with no field** counts rows, `$sum: 1`.
- **COUNT with a field** counts rows where the field is present and non-null, matching SQL
  `COUNT(col)`. That needs `$sum: { $cond: [...] }`, not a bare `$sum: 1`.
- **SUM and AVG silently skip non-numeric values** in Mongo. A column holding numeric strings
  aggregates to zero with no error. Flag this in the function docs; it is a quiet-wrong case.
- **Sort properties must be a group alias or a measure alias.** Anything else references a
  field that no longer exists after the `$project` and sorts every row equal. Validate it.
- **Output encoding matches input encoding.** A field stored in milliseconds buckets back to
  milliseconds. That way the same page binding and date formatter that renders the raw field
  renders the bucket, with nothing to special-case.

### 4.3 Count

When `count` is true, wrap from the `$sort` onward in a `$facet`:

```js
{ $facet: {
    rows:  [ { $sort: ... }, { $skip: ... }, { $limit: ... } ],
    total: [ { $count: "value" } ]
}}
```

`$group` already defeats post-match index optimisation, so a second pipeline would mean
scanning twice for no benefit. The `$facet` runs `$match` once and branches.

### 4.4 Guardrails, none of them optional against real data

- **`allowDiskUse(true)`.** Grouping a large collection otherwise hits Mongo's 100MB in-memory
  limit and fails outright.
- **A hard server-side cap on `size`**, 1000 or so, so a group-by on an unbounded field cannot
  be asked to materialise millions of groups.
- **`maxTime` on the aggregation**, so a runaway query cannot pin the Mongo node.

---

## 5. Date bucketing, and why it is number-only

### 5.1 The bucket

`$group` groups by exact value. Grouping ten thousand orders by a raw `orderDate` that carries
a time component yields roughly ten thousand groups of one row. Bucketing truncates to a
boundary first, so rows land together. This is the difference between "revenue by month" and
a crash.

### 5.2 The timezone

The truncation happens in some timezone, and the stored instant is UTC while the users are not.

An order placed at **2026-10-01 03:00 IST** is the instant **2026-09-30 21:30 UTC**. Truncated
to a month in UTC it buckets as **September**. Truncated in `Asia/Kolkata` it buckets as
**October**, which is what the business means and what the invoice says.

Daily is where it stings most: every IST day, the window from midnight to 05:30 is attributed
to the previous day. About 23% of each day's hours land in the wrong bucket. Nothing errors,
the chart renders, the shape looks plausible, and every daily total is wrong. Month-end
reconciliation is where someone eventually notices.

Hence the IANA `timezone` on `GroupByField`, defaulting to UTC.

### 5.3 Why number-only

App data dates are **not BSON dates**. The only write-side conversion is `BJsonUtil.from()`,
which has no `Date` branch at all and maps JSON primitives straight through, so a date field
holds whatever JSON handed it. The convention is stated explicitly in a comment at
`MongoAppDataService.java` line 1063: *"every other timestamp the client receives is epoch
SECONDS and its date formatter reads nothing else."* Only version rows carry a real BSON date
(line 418), and even those are normalised to epoch seconds on the way out (line 1070).

`SchemaType` offers INTEGER, LONG, FLOAT, DOUBLE, STRING, OBJECT, ARRAY, BOOLEAN, NULL. There
is no DATE. So a date is a number or a string, and only the number can be bucketed arithmetically.

But **number alone does not narrow it enough**, and the reason is in the Calendar component.
`nocode-ui/ui-app/client/src/components/Calendar/utils/dateFormatting.ts` lines 26 and 27:

```ts
if (toFormat === 'x') return dateObject.getTime();                        // epoch MILLISECONDS
else if (toFormat === 'X') return Math.floor(dateObject.getTime() / 1000); // epoch SECONDS
```

`storageFormat` is a page-author property. Both write a number and the schema calls both LONG.
Multiply milliseconds by a thousand and you land in the year 56000: it does not error, it
buckets, and the chart renders one group past the heat death of the sun.

Hence `encoding` is **declared per query** rather than inferred, with the schema used as a
guard rather than a source of truth.

### 5.4 What this excludes, and a subtlety for later

The same `storageFormat` accepts `YYYY-MM-DD` and friends, and plenty of authors pick that
because it reads well in the data view. Those storages cannot be charted in this cut. That is
a product decision, taken deliberately, not a technical limit.

When string support does arrive it is **not** just another parse branch. The string path builds
its output from `getFullYear()`, `getMonth()`, `getDate()`, `getHours()` (same file, lines 29
to 34), which are local-time methods reading the browser's timezone. A stored `2026-10-01` is
already wall-clock in whatever timezone the writer's browser had, so bucketing it needs **no**
timezone conversion, the opposite of the epoch case. Treating a string as UTC would reintroduce
exactly the 05:30 skew in the one case where it was never present.

### 5.5 Server version

`$dateTrunc` needs MongoDB 5.0 or later, `$toDate` needs 4.0.

Local is **8.0.9**, confirmed 2026-10-01, so development is unblocked. Dev and prod run on
dedicated hosts (`dev-mongo` and `prod-mongo` in `oci-config/application-ocidev.yml` and
`application-ociprod.yml`) with no image pinned anywhere in the repos, so **their versions are
unverified and must be checked before deploy.**

---

## 6. Validation and authorization

### 6.1 Field validation is the security-critical step

`groupBy.field` and `aggregation.field` become Mongo field paths, and `alias` becomes both a
`$project` key and an output map key. Unvalidated, this is aggregation-pipeline injection into
app data. It needs its own named step, not a footnote:

- Fields must resolve against the storage schema or `storage.getFieldDefinitionMap()`.
- Reject any `$` anywhere in a field path.
- Aliases must match `[A-Za-z_][A-Za-z0-9_]*`. Mongo rejects `.` and `$` in keys outright, and
  the map-key charset has bitten this platform elsewhere.
- When `bucket` is set, the field's schema type must be INTEGER, LONG, FLOAT or DOUBLE.
  A STRING field gets a clear 400, not a `$multiply` failure mid-pipeline.
- A magnitude sanity check: epoch seconds for any real date sits near 1e9, milliseconds near
  1e12. A value past 1e11 declared as `EPOCH_SECONDS` is a mistake and should be rejected
  loudly rather than silently coerced.

### 6.2 Authorization reuses what exists

Route through `genericOperation(storage, ..., Storage::getReadAuth, FORBIDDEN_READ_STORAGE)`
(`AppDataService.java` line 1526). An aggregate is a read, so `readAuth` is the correct gate
and no new authority is needed.

Fetch the storage with `getStorageWithKIRunValidation`, which is what enforces `onlyThruKIRun`,
so a storage flagged that way stays unreachable from REST for free.

The controller wraps in `onSurface(appCode, draft, ...)`, exactly as `readPage` does, so the
draft and live surfaces work with no new reasoning. The authorization for naming a surface
explicitly lives in `onSurface` (line 290), not in the controller.

---

## 7. Surfaces

**REST.** `POST api/core/data/{storage}/aggregate`, `@RequestBody AggregateQuery`, optional
`?draft=`. Modelled on the `PATH_QUERY` handler at line 204. Body-only, so the `IGNORE_PARAMS`
trap documented at line 74 of the controller does not apply here.

**KIRun.** `CoreServices.Storage.Aggregate`, modelled closely on `ReadPageStorageObject`,
registered in `CoreFunctionRepository.makeStorageFunctions`. The `aggregations` and `groupBy`
parameters are arrays of objects: follow the `SORT` parameter's `Schema.ofArray(name, objectSchema)`
precedent at line 124 exactly, since array-typed parameters have a history of being mangled by
the function editor and that one is known to survive.

Both surfaces, not one. `readAuth` gates them identically and `onlyThruKIRun` storages are
already excluded from the REST path, so a KIRun-only restriction would buy nothing.

---

## 8. Adjacent in-flight work

Three open PRs add storage capabilities that do not exist today and touch the same four files
this plan edits. **None of them can be merged as they stand.** All three predate commit
`eb25c707a` (2025-04-07), which moved these services from `core` to `commons-core`, all three
still edit `core/src/main/java/com/fincity/saas/core/service/connection/appdata/` paths that no
longer exist, and GitHub reports all three `CONFLICTING` / `DIRTY`. The value below is the
feature intent and the defects worth not repeating, not the code.

### 8.1 PR 946, `CoreServices.Storage.UpdateMany` (opened 2025-03-11)

Batch update mirroring `CreateMany`. The gap is real: there is no way to update multiple rows
in one call today.

Three defects, and the first is the significant one.

**It performs no authorization check.** The method goes storage, then straight to
`Flux.fromIterable(dataArray)`, with no `genericOperation` and no `Storage::getUpdateAuth`,
while `update` has one.

This is not only the PR's problem. **`createMany` on master has exactly the same gap**, lines
463 to 511: no `genericOperation`, no `createAuth`, while `create` at line 203 has one. And
`getStorageWithKIRunValidation` does not cover for it, since `resolveStorage` (line 1877) only
enforces `onlyThruKIRun`. So `CoreServices.Storage.CreateMany` bypasses a storage's `createAuth`
today. That is a live authorization hole, it is out of scope here, and it is listed in section 10.

**An NPE on storages without relations.** `fillRelatedObjects(..., eager ? storage.getRelations().keySet() ...)`
dereferences a null relations map when `eager` is true. `readPage` guards this explicitly;
`createMany` on master does not, and the PR copies that.

**A stray `import org.apache.xpath.operations.Bool;`**, an accidental auto-import.

### 8.2 PR 930, version on delete (opened 2025-02-24)

Adds a `deleteVersion` flag to `delete` and `deleteByFilter`. False writes a `DELETE` version
row capturing the final state before the row goes; true purges that row's version history
instead.

**The gap it targets is confirmed on master.** `MongoAppDataService.delete` (line 490) and
`deleteByFilter` go straight to `findOneAndDelete` and `deleteMany` with **no `addVersion` call
at all**. So a deleted row leaves no audit trace whatsoever, and the version rows it already
accumulated are orphaned in the `_version` collection forever, pointing at an `objectId` that no
longer resolves. Both halves of that are worth fixing, and the `_version` collection having no
retention (section 2.2) means the orphans are permanent.

Two cautions if it is revived. It rewrites `deleteByFilter` to `find()` the entire matching set
into a `List<Document>` before deleting, so a bulk delete now materialises every matched
document in memory, which is fine for tens and not for a filter matching a large slice of a
collection. And there is a stray `import org.jooq.Null;`.

### 8.3 PR 926, `CoreServices.Storage.GetVersionDetails` (opened 2025-02-21)

Exposes `readPageVersion` to KIRun with an `includeObject` flag that omits the snapshot payload.

The gap: version history is reachable over REST at `POST {storage}/version/{id}/query` but not
from a page, so an app cannot render its own audit trail today.

It stays per-row. The function errors when `objectId` is null, so it does not change the
section 2.2 finding that there is no cross-row access to version data. Stray
`import org.apache.xpath.operations.Bool;` here too.

### 8.4 How this interacts with the aggregation work

Loosely, and that is worth recording rather than assuming. All three are storage-function
additions touching `IAppDataService`, `AppDataService`, `MongoAppDataService` and
`CoreFunctionRepository.makeStorageFunctions`, which is exactly the set this plan edits. If any
is revived it needs rebasing onto `commons-core` first; after that the changes are mostly
additive, being different methods and different functions sharing one registration site.

The one real overlap is the `createMany` and `updateMany` authorization gap, because section 6.2
of this plan argues that reusing `genericOperation` is what makes a new storage operation safe
by default. Those two are the counterexample already in the tree, and they are the reason that
argument is stated explicitly here rather than left as an obvious convention.

---

## 9. Phasing

1. Model, interface, Mongo pipeline, validation, controller, plus the `HavingCondition` 500 fix
   from section 2.1. Testable over REST on its own.
2. Date bucketing with timezone.
3. KIRun function and registration. This is the point at which pages can use it.
4. Index review and a Chart-backed example page.

---

## 10. Open items

- **Prod and dev Mongo versions are unverified.** Section 5.5. Blocks deploy, not development.
- **Indexes.** `$match` on an unindexed field followed by `$group` is a full collection scan on
  every chart render. `Storage` already carries an `indexes` map so the mechanism exists, but
  the index-creation gap in this platform should be confirmed closed before this ships, or the
  first dashboard will be slow in a way that looks like this feature's fault.
- **To hand off, not to do here: the version collection has no indexes and no retention.**
  Section 2.2. It is a pre-existing problem that this scoping exercise surfaced, it affects
  `readPageVersion` today, and it belongs to whoever owns versioning rather than to this plan.
- **To hand off, and the most urgent of these: `createMany` bypasses `createAuth` on master.**
  Section 8.1. `CoreServices.Storage.CreateMany` performs no authorization check at all, where
  `create` does. It is the same class of mistake that section 6.2 is written to prevent, which
  is why that argument is spelled out rather than assumed. Needs raising on its own, not folded
  into this work.
- **Deletes are never versioned, and deleting a row orphans its version history.** Section 8.2.
  PR 930's intent is sound even though the PR itself is unmergeable.
- **Three stale PRs need a decision: revive after rebase, or close.** Section 8. PRs 946, 930
  and 926 have been open since early 2025, all predate the `commons-core` move, and all three
  are conflicting. Each targets a capability that genuinely does not exist today, so leaving
  them open and rotting is the worst of the three options.
- **`distinct` is deliberately out of phase 1.** `$addToSet` holds every distinct value per
  group in memory, and nobody has asked for it. It is an additive field later, so leaving it
  out of the model now avoids shipping a field that lies.
- **String-dated storages.** Section 5.4. Needs a decision on whether to support them, and the
  timezone semantics are inverted, so it is not a small addition.
- **Storage-level encoding declaration.** Longer term the encoding belongs on the storage rather
  than on every query, since it is a property of the data and not of the question. A page author
  building a chart should not need to know how someone else stored the column.
  `Storage.fieldDefinitionMap` is the natural home: it is a free-form `Map<String, Object>`
  carried through the override and diff machinery but never read by anything in the backend and
  not referenced in the UI client at all. Using it means defining its shape for the first time,
  and existing storages would need a default that is itself a guess. It layers cleanly on top of
  the per-query field, which survives as an override.


---

## 11. What was actually built

Implemented 2026-10-02 on `feature/blueprint`. All three modules build clean with tests:
commons 93, commons-core 23, core 66.

### 11.1 Delivered

**Aggregation**, as sections 3 to 7 describe, with no design changes. `AggregateQuery`,
`Aggregation`, `GroupByField`, `DateBucketUnit` and `DateEncoding` in `commons`;
`IAppDataService.aggregate` and the Mongo pipeline in `commons-core`;
`POST api/core/data/{storage}/aggregate`; and `CoreServices.Storage.Aggregate`.

**The `HavingCondition` 500 from section 2.1.** `MongoAppDataService.filter()` no longer casts
blind. A `HavingCondition` or `GroupCondition` body now returns 400 with the class name rather
than a ClassCastException.

**`createMany` authorization.** It now routes through `genericOperation` on
`Storage::getCreateAuth`, closing the hole in section 8.1. All eight `CreateMany` references in
`modlix-apps` are documentation, so nothing in that repo depended on the gap.

**The null-relations NPE**, via a new `resolveEagerFields` helper used by `create`,
`createMany`, `update`, `read` and `readPage`. Four of those five dereferenced
`storage.getRelations()` unguarded.

**`CoreServices.Storage.UpdateMany`** (was PR 946), gated on `updateAuth`, which the PR did not do.

**Version on delete** (was PR 930). `delete` and `deleteByFilter` take `deleteVersion`.

**`CoreServices.Storage.GetVersionDetails`** and `includeObject` on the version read (was PR 926).

### 11.2 Where the implementation differs from the plan

**`deleteVersion` is three-state, not two.** NULL leaves version rows untouched, FALSE records a
DELETE row, TRUE purges the history. The plan assumed two states. The third is needed because
`clearAllRows` is contractually "every row gone, history kept", and the create-rollback path is
not a user deleting anything. Both pass NULL; every caller-facing surface defaults to FALSE.

**`deleteByFilter` streams.** PR 930 collected every matched `Document` into a `List` before
deleting, which turns a wide delete into an out-of-memory risk. The record path streams rows one
at a time; the purge path collects only ids and deletes history in batches of 1000.

**The millisecond guard samples one document.** It catches a systematic units mistake, which is
the realistic case, and will not catch a collection with mixed encodings. Verified firing
against a fixture.

### 11.3 Verified against the running local core

Grouping, multiple measures, `having`, whole-collection rollup, every validation rejection,
UTC versus `Asia/Kolkata` bucketing, the millisecond guard, the 500-to-400 fix, the DELETE
version row, history purge, and `includeObject`.

Showcase pages in `monkbars`: `aggIndex`, `aggRevenue`, `aggTimezone`, `aggAudit`, `aggBulkOps`,
over a demo storage `aggSales` (75 rows plus fixtures) and `aggMsCheck` (the millisecond fixture).

### 11.4 Two findings about this repo's build

**`mvn compile` lies here.** Both plain `mvn compile` and `rm -rf target/classes && mvn compile`
returned BUILD SUCCESS on a tree with genuine compile errors, twice. Only `mvn clean compile`
is trustworthy, and that is worth knowing before believing a green local build.

**`mvn clean` on `core` kills the running service.** `startall.sh` runs `mvn spring-boot:run`
off `core/target/classes`, so a clean wipes the classes out from under the live JVM. Restart
`core` after any clean build of it.

### 11.5 Still open

Prod and dev Mongo versions remain unverified (section 5.5); local is 8.0.9, so `$dateTrunc` is
fine there. Everything in section 10 that was not folded in still stands, in particular the
version collection having no indexes and no retention.