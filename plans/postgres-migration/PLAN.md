# Migrating Modlix from MySQL to self-managed PostgreSQL

Status: proposal, not approved. Written 2026-09-07.

Scope: the eleven MySQL databases behind `nocode-saas` and `nocode-ai`, the OCI MySQL DB Systems
that host them on dev, stage and prod, and every place in `oci-config`, `dbs` and the docs that
names MySQL. MongoDB, RabbitMQ and Redis do not change, though section 8 covers what a cutover
does to them. The WhatsApp bridge is on SQLite with Litestream and is unaffected.

---

## 1. The recommendation in one page

Move to **PostgreSQL 18.6, self-managed on a dedicated OCI compute VM per environment**, one
database called `modlix` with **one schema per service**, cutting over **one wave of services at
a time**.

**18.6, from 2026-08-13, is not merely the latest. It is the floor.** Two separate things are
true and both matter.

Minor releases carry only fixes, never features, so **no 18.x lifts a single one of the collation
restrictions** in section 3. Every one was re-measured on 18.6 and behaves identically, down to
the error wording. Nothing in this plan may be loosened on the strength of a version bump, and the
live danger is somebody reading one as permission to put back `ILIKE` or a trigram index, both of
which return wrong rows with no error and no failing test.

But 18.2, which most of the evidence here was first gathered on, is **not a safe version to build
this design against**. Three of the five shipped 18.x minors carry wrong-answer fixes inside the
exact nondeterministic-collation machinery this plan depends on, and two were reproduced here. A
mixed-collation `EXISTS` semijoin returns **4 rows on 18.2 where the correct answer is 2**, and
this plan deliberately mixes collations on its hottest paths by pairing a collated column with a
`lower(col) COLLATE "C"` index. A plain `LIKE` pattern containing a backslash silently matched
nothing on 18.0 through 18.4, and plain `LIKE` is this plan's entire substitute for `ILIKE`. 18.6
fixes both, plus a lost index optimisation on precisely the `lower(col) COLLATE "C"` shape the
plan mandates, and a filter push-down that could return wrong groups through a collated
`GROUP BY`.

So: take 18.6, pin it by digest, re-take the evidence on it, and change no rule. Section 5 covers
the pinning.

The mechanism that makes this safe is a **dual-dialect build**: every service jar carries both
driver sets and picks its dialect from the connection URL, so a service moves engine by editing
`application-oci<env>.yml` and redeploying the same image. Rollback inside a window is a revert of
that yml plus a redeploy, with MySQL untouched because the service was stopped before the dump.

The sequencing principle is **never change the engine and the code in the same step**. Three
releases go to production on MySQL first: behaviour pins, then pool and transaction wiring, then
the dual-dialect image soaked for a week.

Estimated effort is **about 130 engineer-days**, which is roughly four to five months to the final
production wave with three engineers. Production downtime is around 10 minutes per service in the
first wave, 15 for the messaging pair and under 30 for security.

**Three things to understand before reading further.**

**This is not a lift and shift.** The platform has run on case-insensitive collation,
`ON UPDATE CURRENT_TIMESTAMP` and MySQL upsert semantics for years, and the code leans on all
three without saying so. Section 3 is the real work. A mechanical conversion plus a data copy
would come up, serve traffic and quietly return wrong answers.

**Two of the findings are silent, and they are silent in different ways.** A `ULong` binding that
sends `numeric` turns every primary key lookup into a sequential scan, so the platform gets slower
under load until it falls over. A trigram index on a collated column returns a **subset** of the
matching rows, so an equality lookup can report that a record does not exist when it does. Both
are verified below. No functional test sees either, because the query succeeds in both cases.

**The plan is not yet ready to execute.** Fifteen of its 34 load-bearing claims are verified, the
rest are asserted, and three of the asserted ones would force a redesign rather than a patch if
they fail. Section 9 says which. The shortest path to ready is the P0 spike phase.

---

## 2. What is on MySQL today

Eleven databases on one instance per environment at
`<env>-mysql.sub10150624021.modlixvcn.oraclevcn.com:3306`, reached by a single shared `admin`
user whose password is committed in plaintext. Production is an OCI MySQL DB System on 8.4; local
development runs MySQL 8.0.42 in the `mysqldev8` container.

| Database | Local size | Tables | Owner service | Stack | Migrated |
|---|---|---|---|---|---|
| `security` | 136 MB | 68 | security | reactive R2DBC | yes, wave C |
| `entity_processor` | 55 MB | 39 | entity-processor | reactive R2DBC | yes, wave B |
| `ai` | 54 MB | 15 | nocode-ai | Python aiomysql | yes, wave A |
| `message` | 41 MB | 11 | message | reactive R2DBC | yes, wave B |
| `files` | 18 MB | 6 | files | JDBC and HikariCP | yes, wave A |
| `core` | 10 MB | 3 | core and commons-core | reactive R2DBC | yes, wave A |
| `notification` | 1.6 MB | 3 | notification | JDBC and HikariCP | yes, wave A |
| `worker` | 0.6 MB | 14 | worker | JDBC, Quartz job store | yes, wave A |
| `adzump` | 0.3 MB | 10 | adzump | JDBC and HikariCP | **decide, see below** |
| `entity_collector` | 0.1 MB | 3 | retired | none | no, archived in the final dump |
| `multi` | 0 | 0 | multi | reactive R2DBC, unused | no, stack deleted |

Three of those rows are decisions rather than facts. `entity_collector` belongs to a service that
no longer deploys and whose tables were folded into `entity_processor`, so it is archived, not
migrated. `multi` has zero tables while the service still opens a pool to it, so its relational
stack is deleted rather than ported.

**`adzump` is deployed nowhere.** There is no adzump container in any of the three compose
manifests, no `oci-config/adzump.yml`, and it is absent from the deploy script's service list. It
nevertheless carries a disproportionate share of the type conversion work: most of the JSON
columns, half the enum columns and a large share of the char columns in its own schema. It also
has a **pre-existing defect**: its generated columns are lowercase while the shared DAO looks them
up in upper case, so its write path throws today. Either descope it explicitly, or schedule it and
fix that defect on MySQL first. Do not leave it half-in.

Data volume is small, a few hundred megabytes in total, which is why a dump-and-load beats change
data capture and why the windows are minutes rather than hours.

### How the code reaches it

Two persistence stacks, which doubles most shared work:

- **Reactive**, via `commons-jooq`: security, core with commons-core, multi, message,
  entity-processor. jOOQ over an R2DBC connection factory.
- **Imperative**, via `commons2-jooq`: files, notification, worker, adzump. jOOQ over a JDBC
  `DataSource`, with `SQLDialect.MYSQL` passed explicitly. This is the **only** hardcoded dialect
  literal in the tree.

`commons2-jooq` is a near copy of `commons-jooq` under a different package, differing materially
in only three places, so most shared fixes land twice.

The reactive stack's contact with MySQL is narrower than it looks: it forcibly sets the R2DBC
protocol to `mysql`, but the **dialect itself is auto-detected** from the driver metadata, so
correcting the protocol is enough. There is no raw SQL, no raw table name and no reference to
jOOQ's MySQL metadata classes anywhere in either commons module.

Schema management is Flyway, running at every container start on dev, stage and prod and never
locally, over 300 MySQL-dialect scripts. jOOQ code generation runs against a **live local MySQL**
and the generated classes are committed.

Configuration is centralised, which is the one piece of luck here. MySQL is named in 22 URL lines
per environment file, 27 driver-class lines and one logging category, plus the two lines above.
No Dockerfile and no compose manifest contains anything MySQL-specific. **A service changes engine
by changing a URL.**

---

## 3. Eleven findings that shape the plan

Each is a case where a correct-looking port returns wrong answers, or gets slower with no error.
The ones marked **verified** I tested directly.

### 3.1 A `ULong` binding that sends `numeric` destroys every index (verified consequence)

This is the most dangerous finding in the document.

Ids are `org.jooq.types.ULong` in 481 files. The plan's central type decision is to keep that Java
type through a code generation converter on a PostgreSQL `bigint`, precisely so those 481 files do
not change. The hazard is **how** the value reaches the wire. jOOQ's built-in unsigned support
converts `ULong` through `BigInteger`, and the JDBC driver sends a `BigInteger` as `numeric`.

I measured what that costs on a 200,000-row table with a `bigint` primary key:

| Parameter type | Plan |
|---|---|
| `bigint` | `Index Scan using big_pkey`, `Index Cond: (id = ...)` |
| `numeric` | `Parallel Seq Scan`, `Filter: ((id)::numeric = ...)` |

The column gets cast to `numeric`, which makes the predicate non-indexable. That is a sequential
scan **for a primary key lookup**, across roughly 681 bigint columns, every primary key and every
foreign key join predicate in the platform. There is no error, no warning and no failing test.
The platform simply gets slower under load until it falls over.

The mitigation is a **hand-written `Binding<Long, ULong>` using `setLong` and `getLong`**, so the
wire type stays `bigint`, registered in all nine code generation configurations, plus a CI gate
that runs `EXPLAIN` on a primary key lookup and fails if it is not an index scan. What has **not**
been verified is whether jOOQ plus the driver actually sends `numeric` for a `ULong` in this
configuration; that requires running the stack and is the first item in P0. The consequence is
proven, so the spike is about whether the trigger fires, not whether it matters.

The same question has to be asked separately for the reactive driver, which nobody has run at all.

### 3.2 Two kinds of index silently return the wrong rows (verified)

I first tested this wrongly and reported that a trigram index cannot be built on a collated
column. That was an artifact of my own rolled-back transaction. The truth is worse.

On PostgreSQL 18.2 with an ICU nondeterministic collation, a `gin_trgm_ops` index **builds
successfully, is chosen by the planner, and returns a subset of the correct rows.** The reason is
that `pg_trgm` folds case when it extracts trigrams but does **not** fold accents, while the
collation folds both. Measured on a table with an accented row:

| Query | With the index | With index scans disabled |
|---|---|---|
| `name LIKE '%creme%'` | 2 rows | 3 rows |
| `name = 'CREME BRULEE'` | **0 rows** | **1 row** |

The second line is the one to sit with. A plain equality lookup on an indexed column returned
**zero rows when the correct answer is one**, and whether it does so depends on whether the
planner picks the index. If that column is an email or a client code, the record simply does not
exist as far as the application is concerned.

So the constraint list splits in two, and the split matters:

| Operation on a nondeterministic collation | Behaviour |
|---|---|
| `=`, `IN`, `GROUP BY`, `LIKE`, `LIKE ... ESCAPE`, unique index, joins, `lower()`, `split_part` | work correctly |
| `ILIKE`, regex match, `starts_with()`, `text_pattern_ops` index | **fail loudly** |
| `gin_trgm_ops` index | **builds, is used, silently wrong** |

**Full-text search over a collated column has the same defect**, and this plan needs full-text
search because it is where the MySQL fulltext indexes go. A stored `tsvector` generated from a
collated column builds and queries with no error, and is accent-sensitive while the column is not:
searching one spelling returned 3 rows and the accented spelling 1, against a true answer of 4.
The fix is a copied text search configuration with the `unaccent` dictionary mapped in, which
brought both spellings to 4. Note that the obvious alternative, a generated column over
`unaccent(...)`, **cannot** work: `unaccent` is not immutable, so the column definition is
rejected. The configuration route is the only one.

Rules that follow. Never build a trigram or pattern-ops index on a column carrying the collation;
where a search must be indexed, index `lower(col) COLLATE "C"` explicitly and query that exact
expression, which I confirmed is sound because the index and a sequential scan agree. Require the
unaccent-bearing text search configuration for every `tsvector` over a collated column. Lint for
both. And add a P0 harness that runs every candidate query with index scans forced on and off and
asserts identical row sets, kept in CI, because this class of bug is invisible to any test that
does not compare the two.

One warning about that harness: it must run at realistic cardinality. On a handful of rows the
index access method falls back to a full scan with a recheck and answers correctly, so a
small-fixture version of this test **passes while the bug is live**. The confirmed reproductions
used tens of thousands of rows.

Also worth stating because it is the cheapest possible substitution: where a case-insensitive key
is being built rather than searched, `casefold()` is the correct primitive rather than `lower()`,
because it does full Unicode case folding. Taking that now is one word in the schema generator and
awkward to retrofit once the indexes exist.

Also verified: **PostgreSQL 17 rejects plain `LIKE` on a nondeterministic collation outright.**
That is the whole reason the target is 18. The fallback if 18 is unacceptable is not "use 17 with
a caveat", it is a different collation design.

### 3.3 Lowercase identifiers break every write, loudly (verified in the jOOQ sources)

The plan folds identifiers to lowercase so that raw SQL, `psql` and the copy tool need no
quoting. That choice collides with how the shared DAO addresses fields.

The DAO converts an API field name to `UPPER_SNAKE` and calls `table.field(String)`, which jOOQ
resolves by **exact, case-sensitive equality** with no fallback. Around 29 sites depend on that
uppercase literal. Most fail soft, returning null, and some are rescued by accident because a
second lookup scans the select list case-insensitively.

Six sites do not fail soft. `Record.reset(String)` resolves through a helper that **throws**
rather than returning null. They sit in `AbstractUpdatableDAO` in both stacks and in adzump's
JSON DAO, on the `CREATED_BY` and `CREATED_AT` columns. Under lowercase generated code, **every
`update(entity)` in both stacks throws before issuing any SQL**, which is every PUT on every
jOOQ-backed entity.

This is good news disguised as bad: the failure is total and immediate, so one smoke test that
performs a single update catches it, and no read-only test ever will. The fix is to resolve the
field case-insensitively once and pass the `Field` rather than the string, shipped in release 1
where it works against both uppercase and lowercase generated code.

### 3.4 Sign-in becomes case-sensitive (verified against local data)

All 877 string columns in the eleven databases are `utf8mb4_unicode_ci`, with no exceptions. So
every `=`, `IN`, `LIKE`, `GROUP BY`, `DISTINCT` and `UNIQUE` on user names, emails, codes, URL
patterns, sources, file names and tokens ignores case, accents and trailing spaces today, and the
Java code almost never normalises case itself.

Of 4856 users with an email, **963 have uppercase in it**, and each is a login that fails on
PostgreSQL if the person types their address in lower case. Separately, 52 addresses collide once
case is ignored, covering 193 rows, though only **5 collide inside a single client**, which is the
scope uniqueness is actually enforced in. User names are clean.

The pattern runs through the platform: 22 of 601 client URL patterns are off canonical case, so a
lowercase hostname stops resolving to a mixed-case app code; 22627 of 42030 file names carry
uppercase, 31 of them differing from a sibling in the same folder only by case; ticket `SOURCE`
has **34 byte-distinct spellings for 21 collation-distinct values**, so reports split one source
into several rows and an exclusion rule written for one spelling stops matching another.

There are **six** SQL-side case-insensitive comparisons in the tree, not three as first counted,
and three of them sit on the client URL pattern column, which is host-to-app resolution and the
hottest path on the platform. jOOQ renders those as `lower(x) = lower(?)`, which works on a
collated column but **cannot use a b-tree index**; survivable at 601 rows, and worth knowing
because the collation scope was drawn from the smaller count.

The answer is the ICU collation on identity and name columns, with **plain `LIKE` and never
`ILIKE`**, for the reason in 3.2. jOOQ renders `likeIgnoreCase` as a literal `ILIKE`, which throws
on a collated column, so a CI grep should forbid `likeIgnoreCase` and its siblings tree-wide in
favour of `containsIgnoreCase`, which renders `lower(x) LIKE ... ESCAPE` identically on both
engines. That substitution also fixes a live bug: the current substring filter interpolates user
input into a `LIKE` pattern with `%` and `_` unescaped.

One correction to an earlier draft: the files service does **not** issue prefix queries. It
resolves paths by exact name within a parent and walks the chain in Java, and its only two `LIKE`
calls are leading-wildcard and therefore unindexable on any engine. The collation on file names
costs no index use, so the trade-off reserved for a spike does not exist.

### 3.5 Ninety-eight columns stop updating themselves

Ninety-eight columns are declared `ON UPDATE CURRENT_TIMESTAMP`, and the statement digests show
tens of thousands of updates that never mention the column. PostgreSQL has no equivalent, and the
code sorts and decides on those values: latest-URL resolution, ticket ordering, SSL renewal and
the AI session list all read them. Without a replacement they freeze at their migrated values and
nothing errors.

The mechanism is worth stating precisely, because the fix depends on it. Nothing in Java stamps
these columns. The DAO nulls them, and jOOQ omits a null from the statement when the column is
`NOT NULL`, which lets the MySQL default fire. So **the statement shape, and therefore whether a
default or a trigger fires, depends on the nullability of each column**, and 11 of the 98 are
nullable today. If the generated baseline flips one, behaviour changes with no code change and no
compile error. The lint must assert nullability column by column against MySQL and emit the diff
as a reviewed artifact.

The replacement is one generated `BEFORE UPDATE` trigger per schema with two guards: return
unchanged if the caller already set the column, then return unchanged if the row did not actually
change. The first guard reproduces the `UPDATED_AT = UPDATED_AT` trick that two sites use to
freeze the column deliberately, one of which is in Python. The second reproduces MySQL's no-op
rule, and is the reason all 67 json columns must become `jsonb`: `json` has no equality operator,
so the comparison would not compile.

An earlier draft proposed a session-variable escape hatch for the deliberate freezes. Drop it: it
cannot reach the Python site, and both sites assign the column explicitly, so the first guard
already handles them.

### 3.6 Two pool defects would sink the first PostgreSQL deploy

The reactive stack builds its own pool and wraps it in a second one, so the configured pool size
is ignored and both default to 10. The imperative stack puts its Hikari settings under a prefix
Spring does not bind, so Hikari also runs at its default of 10 rather than the configured 4; the
local process list confirms it.

There is a third effect nobody had counted. On the imperative side jOOQ runs on the raw
`DataSource` while a transaction manager also exists, so each `@Transactional` DAO call **leases
two connections** for its duration: one for the transaction nobody uses, one for jOOQ.

Summed over the production compose that is roughly 210 potential connections at steady state,
against a PostgreSQL default of 100. And the overlap is worse than a single blue-green pair: the
deploy script brings up the next colour of **both** instances of the multi-instance services
before stopping either active one, so an ordinary deploy of those services doubles their pools.

Both defects are fixed in **release 2, on MySQL**, verified against the process list, where a
regression is attributable to the pool change alone. `max_connections` and the alert threshold are
then derived from the measured numbers plus that worst-case overlap, not guessed. An alert set at
70 percent of a guessed 300 would fire on every ordinary deploy and be muted before the cutover.

### 3.7 `@Transactional` is a no-op today, and fixing it changes error semantics

Because jOOQ runs on a connection the transaction manager does not know about, in both stacks,
every statement is effectively autocommit. The wallet ledger insert and balance update can already
diverge on a crash.

The trap is the interaction: a failed statement inside a PostgreSQL transaction aborts the whole
transaction, where MySQL lets later statements proceed. Three insert-then-read-on-error sites are
harmless today precisely because transactions are inert, and start failing requests the moment the
wiring is fixed. They are converted in release 2, in the same change.

### 3.8 Fourteen upsert sites, and the rendering is not what it looks like

There are **6** `onDuplicateKeyUpdate` and **8** `onDuplicateKeyIgnore` sites, not the 5 and 7
first counted. The eighth ignore site was unlisted in every source document, and every ignore site
needs the same treatment, so the enumeration has to be by file and line.

I read the jOOQ 3.20.5 sources to settle what the update sites render as, because two analyses
disagreed. Both were right about different sites. With no explicit conflict target, no returning
clause, and a table whose keys number more than one, jOOQ emits a **`MERGE`**, matching on every
key. Otherwise it emits `ON CONFLICT`, falling back to **the primary key columns only**, with a
source comment stating that MySQL's all-unique-keys behaviour cannot be emulated.

Every update site inserts without an id and relies on a secondary unique key, so the metrics sync,
bridge registration and app property writes take the `MERGE` path, which is not concurrency-safe
and can never match a key whose columns are absent from the insert. The invoice counter has a
returning clause, takes the `ON CONFLICT` path, and conflicts on a primary key it never supplied.

The fix is the same for all of them: an explicit `onConflict(columns).doUpdate()`, shipped in
release 1 on MySQL where the rendering is unchanged.

`INSERT IGNORE` is a separate problem. MySQL downgrades foreign key violations, not-null
violations and truncation to warnings; `ON CONFLICT DO NOTHING` only skips uniqueness conflicts.
Three ignore sites insert caller-supplied ids, so a stale role id that MySQL quietly dropped
becomes a 500. Also, the affected-row count changes: MySQL reports 1 for an insert and 2 for an
update, PostgreSQL reports 1 for both, and two call sites map that count to a boolean.

### 3.9 Char columns come back padded, inconsistently (verified)

There are 264 `CHAR(n)` columns. MySQL strips padding on read; PostgreSQL stores and returns it. Of
4018 client codes in a `CHAR(8)`, 3714 are shorter than 8, so a code becomes `SYSTEM  ` and flows
into JWT claims, Redis keys, URL segments, headers and every Java `equals`, while SQL equality
still passes because the comparison pads both sides.

The detail makes it worse. Selecting the column returns the padded value, which is what a driver
hands to Java, but almost any expression coercing to `text` **silently strips it**. The same
column reads padded through one path and unpadded through another.

Every `CHAR(n)` becomes `varchar(n)`, and the lint rejects `bpchar` outright. That also drops
MySQL's trailing-space-insensitive comparison, which costs nothing here: there are zero client
codes, app codes or user names with a trailing space.

### 3.10 Null ordering reverses, which changes which row gets picked (verified)

MySQL places nulls first ascending and last descending; PostgreSQL does the opposite, confirmed on
both engines. None of the six generic sort translators emits a nulls clause, and they serve every
UI page sort.

The data makes it concrete: the ticket last-message column is null on 3223 of 3328 rows, so
conversation lists would open on thousands of never-messaged records. Worse, one site orders by a
nullable column and takes the first row to decide which existing ticket an incoming lead attaches
to, with 1663 of 3328 null there, so **lead attribution changes**. Several `LIMIT 1` queries have
no `ORDER BY` at all, and InnoDB's index order is not PostgreSQL's heap order.

### 3.11 The tinyint rule has to be inverted

An earlier draft said `boolean` only for `TINYINT(1)` and `smallint` for the other 112 columns.
That is backwards and would break the build.

Entity-processor and message already force **every** tinyint to `Boolean` in code generation, and
97 columns are committed as `Field<Boolean>` today. The draft rule would retype about 95 of them to
`Field<Short>` and break the two largest reactive modules at compile time. Meanwhile only a
handful of columns in the whole estate actually hold numbers.

So: **`boolean` for every tinyint, except a checked-in numeric allow-list** covering the lore
confidence and attempts columns and the learning feedback rating. The blanket forced type is added
to the remaining poms rather than removed from two. The edit-access column, previously flagged as
numeric, holds only 0 and 1 and becomes boolean, with its call sites retyped. A lint rule should
assert that no boolean column generates a Java `Byte` or `Short`, because jOOQ renders a truth
test as `x = 1` for those and `x = true` for `Boolean`.

Related: 116 enum columns, 58 generated enum classes and 31 forced-type columns leaves **27 enum
columns with no stated rule**, which the schema generator has to resolve. Enum columns sort by
declaration order today and one sort depends on it to put folders before files, so the generated
classes get native types while the hand-written enum classes, whose name accessor returns null and
which therefore render no cast, stay on `varchar` with a check constraint. Native types and
regeneration must land in the same commit per module, with a CI check, or a partially deployed
module throws on every enum predicate.

Finally, MySQL scopes index names per table and PostgreSQL per schema: **13 index names are reused
across tables inside one schema**, so a naive conversion fails at DDL time.

---

## 4. Decisions

| Decision | Choice | Rejected |
|---|---|---|
| Cutover unit | Dual-dialect image, config-only flip, three waves per environment | One window for everything; per-service PostgreSQL-only images |
| Version | **PostgreSQL 18.6 pinned by image digest, one variant, everywhere**: local, Testcontainers, code generation, the standby, restore drills and all three environments. See section 5 | 17 and 16, which cannot do `LIKE` on the collation; the floating `postgres:18` tag; mixing base variants; major-only pinning |
| Topology | Dedicated VM per environment, own compose project, prod hot standby with manual promotion | Co-location on the app host; the analytics host for prod; managed PostgreSQL; auto-failover in phase one |
| Layout | One database `modlix`, one schema per service, per-service login role | One database per service; everything in `public` |
| Identifier case | Unquoted lowercase, with the case-insensitive field resolution fix shipped first | Quoted upper case, which forces quoting in every raw statement forever |
| Unsigned ids | `bigint` plus a **hand-written `Binding<Long, ULong>`** using `setLong`, with an `EXPLAIN` CI gate | jOOQ's built-in unsigned support, which risks the `numeric` index collapse; a repo-wide `Long` refactor inside this programme |
| Booleans | `boolean` for every tinyint except a checked-in numeric allow-list | `smallint` for everything not `tinyint(1)`, which breaks 97 committed fields |
| Enums | Native types for the 58 generated classes, `varchar` plus check for hand-written ones, a stated rule for the remaining 27 columns | All native; all varchar |
| JSON | `jsonb` everywhere | `json`, which has no equality operator and breaks the trigger |
| Timestamps | `timestamp(0) without time zone`, UTC pinned on server, JVM and Python | `timestamptz(3)` |
| Updated-at | Generated trigger per schema with two guards, plus column-by-column nullability pinning | DAO-side stamping; a session-variable escape hatch |
| Collation | ICU nondeterministic on identity and name columns; **plain `LIKE`, never `ILIKE`**; no trigram or pattern-ops index on a collated column | `citext`; collation everywhere; trigram indexes for search on collated columns |
| Code generation | Generate once from PostgreSQL; the same jar runs on MySQL during the soak | Two generated trees per service |
| Flyway | Generated baseline in **three** buckets: pre-load schema, post-load constraints and triggers, fresh-database-only seeds | Porting 300 scripts; two buckets, where a truncating load wipes the seeds |
| Copy tool | `mysqldump` into a staging container of the same **release series** (8.4 for production), then the copy tool in data-only mode, then sequence sync | Copying straight from the managed instance; change data capture; an 8.0 container for an 8.4 dump |
| Copy tool fallback | `mysqldump --tab` into `COPY FROM` with explicit casts, costed in P0, using `ON_ERROR ignore` with verbose logging to debug cast rules and `REJECT_LIMIT` on the real load so a surprise still aborts | Discovering in P10 that the tool will not install; an all-or-nothing load that names no failing row |
| Pooler | None. Fix both pool defects, then derive `max_connections` from measurements | A pooler, which conflicts with prepared statements and per-session `search_path` |
| Verification | Per-column profiles, sampled row hashes, orphan scan, shadow reads, an index-versus-sequential row-set harness | Row counts and max id |
| Python driver | asyncpg behind a thin adapter, statements hand-ported | A shim that keeps `lastrowid`-shaped code silently returning 0 |
| Backups | pgBackRest to per-environment Object Storage, **weekly automated restore drill** | A one-off restore test |
| Existing gate | int-test, already in the repo, as a mandatory gate at three points | Leaving the cheapest end-to-end check unscheduled |
| Local development | **Built and verified**: `dbs/postgres`, port 7382, digest-pinned 18.6, builtin `C.UTF-8` database, nine schemas with per-service owning roles, the `ci` collation and the accent-folding text search configuration. Flyway stays off locally per team convention | Port 5432, left free deliberately; reusing another project's container; Flyway at boot locally |

---

## 5. Target platform

A **dedicated compute VM per environment** in the app VCN, running PostgreSQL 18.6 in its own
compose project with its own singleton keepup script. The operational pattern is proven here (the
analytics and bridge hosts work this way) though the **version precedent is not**: those hosts run
PostgreSQL 16. Local, Testcontainers, the staging container and all three environments must be 18,
or the collation policy is untestable where it is cheapest to test.

It is deliberately **not** on the app host: the deploy script's restart verb runs
`docker compose down` on the whole project, and the production host is already committed to about
57 GB of container memory limits.

- Production: 2 OCPU and 16 GB primary, plus a streaming hot standby in a second fault domain with
  manual promotion, drilled once on stage. Dev and stage: 1 OCPU and 8 GB.
- Data on a block volume at `/opt/pg`, mounted into the container as a **single mount at
  `/var/lib/postgresql`**, not at `/var/lib/postgresql/data`. From 18 the official image keeps
  data in a major-version subdirectory so a future `pg_upgrade --link` does not cross a mount
  boundary, and mounting the older path makes the container refuse to start. Verified by building
  the local compose, which failed exactly that way first.
- **A reviewed `postgresql.conf`, not four knobs.** `max_connections` and the alert threshold
  derived from post-release-2 measurements plus the worst-case blue-green overlap; `work_mem`
  sized against the resulting backend count; `shared_buffers`, `maintenance_work_mem`,
  `effective_cache_size`, checkpoint and WAL sizing; `statement_timeout` and `lock_timeout`;
  `idle_in_transaction_session_timeout`; `timezone` UTC; `pg_stat_statements` in
  `shared_preload_libraries`. Add three that the first draft missed:
  **`transaction_timeout`**, which is the enforcement that goes with the longest-transaction alert
  and catches the case `idle_in_transaction_session_timeout` structurally cannot, a transaction
  that stays continuously active and never commits, and which matters precisely because release 2
  makes transactions real for the first time; **`reserved_connections`**, so the ops and monitoring
  roles can still connect at the exact moment the application has consumed every slot; and a
  decision on **`io_method` and `io_workers`**, which are new in 18 and default to three worker
  processes, because on a 2 OCPU primary that is a real contention choice and it has to be made
  before the first `initdb` rather than discovered under load.
- **Autovacuum tuning per table for the update-heavy tables.** The workload is update heavy, and
  the migration adds a `BEFORE UPDATE` trigger on 98 tables plus explicit foreign key indexes, so
  nearly every update becomes a non-HOT update touching more indexes than it does on InnoDB. Bloat
  is the classic first PostgreSQL surprise, and it needs a baseline and an alert, not a discovery.
- **A `pg_hba.conf` design and a TLS decision**, per role and per source CIDR with
  `scram-sha-256`. An NSG rule is not access control. Redis is already reached over TLS in
  production while the MySQL URLs are plaintext, so there is an existing inconsistency and no
  stated target. This must exist before the first `initdb`.
- pgBackRest to a per-environment bucket through the S3-compatible endpoint already used by
  PostHog, Litestream and the Mongo backups: weekly full, daily differential, continuous WAL, four
  fulls retained, **plus a weekly automated restore into a scratch container**.
- Exporters per VM into Prometheus and Grafana on the analytics hosts. Alerts on connection count,
  replication lag, backup age, disk, collation version mismatch, **longest transaction, oldest
  transaction id, lock waits, deadlocks and p99 statement time**. The transaction-age alert matters
  because release 2 turns `@Transactional` real across eight services. There is no database
  observability in the tree today, so this is new build.
- Port 5432 open only to the app host, the standby and the jumphost, never the Mumbai bridge range.
- **Per-service login roles replace the shared `admin` user**, passwords injected from the
  environment. The Python service takes a plain environment variable because its config loader does
  not resolve Spring placeholders.
- **Client tooling is a deliverable, not an assumption**: a MySQL 8.4 client, a PostgreSQL 18.6
  client and the copy tool installed and version-recorded on the jumphost and the database VM.
  Oracle Linux ships none of them by default, and every wave and the decommission gate need them.

### Version pinning, and why it is a correctness control here

**Pin `postgres:18.6` on one base variant, by image digest, in one place, and have every other
site reference that one value.** Not the major, not the floating minor tag. This is not
housekeeping; on a collated database the image identity is part of the data's correctness, for
three verified reasons.

**The floating tag already moved under a live database on this machine.** Two containers started
from `postgres:18` months ago are still running 18.2, while `postgres:18` now resolves to 18.6.
Same file, same tag, different server, and the drift is invisible until something is recreated. And `postgres:18` will become 18.7 on or
after the next quarterly release date with no change to any file we control. Worse, the
operational pattern this plan proposes to copy contains the trigger: the analytics host's keepup
script runs `docker compose pull` and then `up -d` unconditionally on its deploy verb. A floating
tag plus that script is an unattended engine upgrade of a production database, and given the
previous paragraph it would be an upgrade **across** the versions whose collation behaviour
differs. **The database keepup script must omit the pull path entirely, or refuse to start when
the running digest differs from the pinned one.**

**The first `initdb` must happen on 18.6, not on an older minor later upgraded.** One of the
18.3 fixes, to the volatility marking of a jsonb function, reaches only newly initialised
clusters, and this plan converts all 67 json columns to `jsonb`. A cluster created on 18.2 and
patched to 18.6 does not get it.

**The base variant is part of the pin, not a detail.** `postgres:18.6` and
`postgres:18.6-alpine` are the same PostgreSQL minor but ship different ICU libraries, and they
record **different collation versions for the identical collation**: 153.128 against 153.136.
Since an ICU change under a collated index silently changes index ordering, mixing variants
anywhere in the chain, including in Testcontainers or a restore target, is a corruption vector on
its own. The analytics precedent is Alpine, so it is an operational precedent only, never a
version or variant one.

**The collation version mismatch is a `WARNING`, not an error, and the query still answers.** With
a deliberately mismatched recorded version, an equality query emitted the warning and returned
rows anyway, and `CREATE INDEX` emitted it and then built the index, so indexes built under two
different ICU versions can coexist in one table. `ALTER COLLATION ... REFRESH VERSION` completes
instantly and **rebuilds nothing**, so it is only ever safe after a reindex and must never be the
response to an alert. The correct procedure is: stop writes, `REINDEX` every index touching the
collation, then refresh the collation and database versions, then confirm that the query comparing
`collversion` against `pg_collation_actual_version` returns zero rows. **That query, not a log
grep, is the alert.**

Two things shrink this blast radius, and both are cheap enough to take now:

- Create `modlix` with the **builtin locale provider** and `C.UTF-8` as the database default, which
  records the database collation version as the constant `1` rather than a library version.
  Only the columns explicitly carrying the ICU collation then need reindexing on a library change,
  instead of every text index in the database. It also makes the `lower(col) COLLATE "C"` search
  indexes this plan mandates version-free.
- Give the ops role `MAINTAIN`, or membership of the `pg_maintain` predefined role, so somebody
  other than a superuser can run `VACUUM`, `ANALYZE` and `REINDEX`. The per-service login roles in
  this plan otherwise leave reindexing to a superuser, and reindexing is the recovery action for a
  collation bump, so that role has to exist before the first `initdb`.

**Thirteen sites need the same value**, and today none of them agree because most do not exist
yet: the plan text, the three new database VM composes, the new local compose, the standby, the
weekly restore drill, the decommission restore test, the nine code generation configurations, the
Testcontainers library version (pinned three times in two different styles, on a release that
predates PostgreSQL 18), and the container image inside security's integration test base, which is
a hardcoded string literal today. Enforce it with a CI check that greps the poms and every compose
and fails on divergence, plus a pre-promotion check comparing the primary's and the standby's
version and running digest.

**Who bumps it.** One named platform owner, changing one line, with the digest frozen from the
spike phase through the last cutover and the first bump landing afterwards. A bump is its own
change: never inside a wave, never between the pre-load and post-load schema buckets, and
rehearsed on stage first.

One related correction. The plan says the copy uses a staging MySQL container of the "same major
version". For a production dump that means **8.4**, not the 8.0 pinned everywhere in this
repository, and the command line cannot be copied from the local compose: `mysql:8.4` rejects the
`default-authentication-plugin` flag that the local file passes.

---

## 6. Schema and data

Schemas are **generated, not hand-written**, from the live MySQL information schema, and never
hand-patched: when a rule changes, the generator runs again.

The output is **three** files per schema, not two:

1. **Pre-load**: tables, types, columns, defaults, identity, generated columns, comments.
2. **Post-load**: unique constraints, indexes, foreign keys and their indexes, tsvector columns and
   their GIN indexes, the trigger function and its 98 triggers, and the sequence resets. The
   trigger belongs here so the bulk load runs untriggered and stored values compute once. Add an
   assertion after every GIN build that the table's row estimate is finite and non-zero, so a
   silently empty index cannot pass as a successful build.
3. **Seeds, for a fresh database only**, with its history row inserted as already applied on
   migrated environments.

The three-way split exists because a data-only load with truncate would wipe seed rows placed in
the pre-load file, and because the migrated data already carries every seed row.

Lint in CI rejects the known failure modes: any `bpchar` column; duplicate index names; uppercase
identifiers; a listed identity column missing its collation, or that collation on a token column;
**any trigram or pattern-ops index on a collated column**; **any `tsvector` over a collated column
without the unaccent-bearing text search configuration**; **any boolean column generating a Java
`Byte` or `Short`**; and any `created_at` or `updated_at` whose nullability differs from MySQL.

One lint rule from an earlier draft was wrong and is dropped: adding an enum value in the same
file that creates the type is fine, and I confirmed it works inside a single transaction on 18.6.
The restriction applies only when the type already existed before the transaction, which is the
case the rule should actually catch, and it matters for the 27 unruled enum columns.

The copy is `mysqldump --single-transaction` with a client at least as new as the server, restored
into a throwaway MySQL container **of the same release series**, meaning 8.4 for production, on the
database VM, then a data-only
load with truncate, triggers disabled, sequences reset and explicit cast rules, then the post-load
file. The staging container sidesteps an authentication problem against the managed instance and
leaves a retained artifact. **The copy tool is unproven**, is not in the base OS repositories, and
its releases predate PostgreSQL 18, so it is proven in P0 with a named fallback, not in P10.

Verification is in the migration script, not a checklist. Row counts and a max id cannot see a
timezone offset, a mis-cast tinyint, a trimmed char or a changed json null, so the checks are
per-column non-null counts, min and max of every column including every timestamp, numeric sums,
distinct counts, a sampled hash over normalised rows, and a foreign key orphan scan. Plus shadow
reads of about forty endpoints between a MySQL colour and a PostgreSQL colour, a fixture comparing
analytics buckets across a daylight-saving boundary, and the index-versus-sequential row-set
harness from 3.2.

---

## 7. Code changes and release mechanics

**Release mechanics first, because this is not obvious.** `commons-jooq` is a path trigger for
five production workflows and `commons2-jooq` for three more. Releases 1 and 2 are both commons
changes, so on merge they land on **five to eight production services at once**, in whatever order
the runners finish. Release 2 makes `@Transactional` real for the first time in the platform's
history. That cannot go out as "normal CI". Ship those two from a branch that is not the
production branch and fan them out per service, or narrow the path filters temporarily so one
service builds at a time. Also: security's build runs its full integration suite, so a failure in
the dual-dialect work turns every deploy of five services red, including unrelated hotfixes.

**All migration work happens in a separate clone on a long-lived branch**, and the two-hourly job
that auto-commits and pushes the working tree is stopped or confirmed before any code generation
run. Regenerating leaves hundreds of modified generated files in the tree, and a single sweep on
the wrong branch is a dev deployment.

**Release 1, behaviour pins, on MySQL.** Deterministic `ORDER BY` at every `LIMIT` without one.
Explicit `onConflict(columns)` at all 6 update sites, foreign key pre-checks or SQLSTATE handling
at all 8 ignore sites, enumerated by file and line. Case-insensitive field resolution, including
the six `reset` sites that throw. `containsIgnoreCase` replacing all six case-insensitive
comparison sites, which also fixes the unescaped substring filter. Explicit nulls clauses at the
hand-written nullable sorts, rendered under PostgreSQL only. The two update-count booleans.

**Release 2, wiring, on MySQL.** jOOQ onto the Spring-managed connection factory in the reactive
stack and onto a transaction-aware proxy in the imperative stack. Hikari keys under the bound
prefix. The three catch-and-continue sites converted. UTC pinned on every JVM and the Python
container. Connection counts then measured, and `max_connections` derived from them.

**Release 3, dual dialect.** Both driver sets in every pom. Protocol read from the URL. The
PostgreSQL Flyway module alongside the MySQL one, with MySQL scripts under a vendor directory. The
hand-written `ULong` binding, the jsonb converters, the boolean forced types. Plain `LIKE` in the
filter translators. Code regenerated from PostgreSQL and committed.

**Per service.** Security is the largest single item: type retypes across eight files, a
derived-table rewrite, enum bindings, identifier trimming, and a test harness rewrite covering
roughly 770 raw statements. Entity-processor and message need date bucketing rewritten with a
timezone shift, json type checks, full-text search moved to tsvector, and a row-capped delete
rewritten as a subquery.

**Worker needs more care than the plan first assumed.** It does rebuild triggers from its own task
table at boot, so job state need not be copied, but the mechanism is a `SmartInitializingSingleton`
in the task service, not the method previously named, and it only re-registers tasks in the
`NORMAL` state. Paused, complete, errored and blocked tasks are **not** re-registered, so
**group-level pause state is discarded**, and because the tenant schedule-control initialiser runs
before the rebuild, a standby tenant's pause is applied to an empty store and its jobs come up
firing. Local cannot reproduce this. Worker also pins Quartz 2.5.2 over the framework's 2.3.2
while its job store schema still carries a 2.3.x header, so the replacement DDL must come from the
version actually on the classpath.

**nocode-ai** moves to asyncpg behind a thin adapter, with about 90 statements hand-ported. Five
places read `lastrowid` and store it as a row id, which on asyncpg does not exist, so those are
real bugs waiting to happen. It ships a PostgreSQL-only image and rolls back by image tag.

**Tests.** Security's integration tests move to a PostgreSQL container. Entity-processor and
message have no database tests at all, so the upserts, bucket boundaries, outbox ordering, trigger
behaviour and transaction-abort flows get new ones, including an updated-at movement test on both a
nullable and a non-nullable column.

**int-test already exists and is the cheapest high-signal gate available.** Seventeen black-box
REST scenarios, needing no code change because they go over HTTP, including one for sort-order
rendering and one for analytics buckets, which are precisely the two riskiest semantic areas. It is
a mandatory gate: green on MySQL after releases 1 and 2, green against a PostgreSQL-backed stack at
the end of the port, and part of every dev and stage wave's smoke.

---

## 8. Release and cutover sequence

| Phase | Days | What it delivers |
|---|---|---|
| P0 | 8 | Spikes on every claim that could force a redesign, each with a chosen and costed fallback |
| P1 | 6 | Release 1: behaviour pins, on MySQL, fanned out per service |
| P2 | 5 | Release 2: pool and transaction wiring, on MySQL, fanned out per service |
| P3 | 9 | Schema generator, three-bucket baselines, collision report, lint, migration script |
| P4 | 8 | Dual-dialect commons, poms, bindings, code generation |
| P5 | 28 | Per-service ports |
| P6 | 10 | Tests on PostgreSQL containers, plus int-test wired in |
| P7 | 8 | nocode-ai on asyncpg |
| P8 | 12 | Database VMs, `postgresql.conf`, `pg_hba`, TLS, backups, monitoring, client tooling |
| P9 | 3 | Release 3: dual-dialect soak on MySQL for a week |
| P10 | 7 | Rehearsals and a frozen runbook |
| P11 | 9 | Cutovers in waves, dev then stage then prod |
| P12 | 8 | Follow-up release, documentation, decommission |

P0 comes first and it is a gate, not a formality. It must include: the `ULong` binding and an
`EXPLAIN` assertion that primary key lookups still use the index, on **both** drivers; the copy
tool installing and completing a full local load on PostgreSQL 18.6; PostgreSQL-generated code
compiling and passing security's integration suite against MySQL; the reactive driver decoding
native enums and collated varchar, and its parameter type for a `ULong`; the collation harness
comparing index and sequential row sets; and the trigger behaviour on a nullable and a
non-nullable column.

Waves per environment: **A** is notification, worker, core, files and the AI service; **B** is
message and entity-processor; **C** is security, alone, in an announced window.

Each wave: **record the OCIR digest tag per service in the runbook** (the deploy cron prunes
unused local images, so a local retag is not a rollback artifact); take and retain a manual DB
System backup **before wave A**, not at the decommission gate; disable the root cron; stop the
wave's services; confirm nothing is still connected; dump; load; verify; commit the environment
yml; deploy the config server; **assert the config server is actually serving the PostgreSQL URL
before deploying any service**; deploy the services on the same image; **clear the cache on every
running container of every service**, in a fixed order, verifying a known key afterwards; run the
smoke checklist; re-enable the cron; watch for 48 hours with an hourly connection audit and queue
depth on the watch list.

Three mechanical corrections to that sequence, each learned from reading the deploy tooling.
There is **no config-branch mechanism**: the config repository has one branch and the config server
sets no label, so use one commit per environment file and assert what is being served. The **real
abort deadline is a 180 second health gate per container**, after which the tooling destroys the
new colour, and since the cutover has already stopped the other colour the service is left with
zero containers, so the recovery command must be pre-agreed rather than improvised. And a Redis
`FLUSHDB` is **not** the right cache step: the cache is two-level with pub/sub invalidation and a
platform-wide prefix, so a flush neither scopes to a wave nor clears the in-process layer, leaving
containers that were not restarted serving stale rows.

**RabbitMQ needs a decision before wave B.** Entity-processor publishes onto TTL holding queues
that dead-letter into an outbox. Those TTLs keep running while consumers are stopped, so a 15
minute window expires every held message at once and the restart fires a burst against external
rate limits. Separately, listeners use automatic acknowledgement with no dead-letter policy, so
the first PostgreSQL hard error inside a listener becomes an unbounded redelivery loop rather than
a single failure. Drain or hold the queues, and add a dead-letter policy before the first wave.

The smoke checklist is behavioural: sign in with an email typed in a different case; sort a deal
list by a nullable column; search and page a WhatsApp thread; fetch a mixed-case asset; re-grant
app access; draw two invoice numbers; run a metrics sync twice; fire a Quartz job and confirm a
paused group is still paused; confirm an AI chat persists; confirm an ETag moves after a URL
update; compare the daylight-saving bucket fixture; and **one mobile pass** through the WebView
shell covering sign-in, a deep link and the SSO callback, because that client exercises host-based
app resolution and cannot be rolled back with the backend.

Dev runs all waves and soaks two weeks. Stage runs two full rehearsals, the second performed by a
different engineer working only from the runbook. Prod follows one release behind stage with at
least five days between waves.

Rollback inside a window is lossless: revert the yml, redeploy, restart the stopped colour. MySQL
was never written to. After a wave is declared good, rollback becomes a reverse copy, rehearsed
once on dev and treated as an incident.

Writes to a migrated MySQL schema **cannot be revoked** per schema with the managed instance's
admin user, so the guard is the hourly connection audit plus rotating the admin password after the
final wave, with the old value sealed while rollback is still possible.

Also confirm after each wave that the datastores nobody is migrating are healthy: every service
owning Mongo, RabbitMQ and Redis is stopped and restarted three times per environment, and nobody
currently owns checking that the Mongo backup job and the RabbitMQ volume came back.

---

## 9. Verification of the plan's own assumptions

Thirty-four load-bearing claims. **Fifteen are verified**, and the rest are asserted. Being
explicit about which is the point: a plan whose assumptions are all "probably" is not a plan.

### Verified

| Claim | Verdict | How |
|---|---|---|
| PostgreSQL 18 supports `=`, `IN`, `LIKE`, `LIKE ... ESCAPE`, unique indexes and joins on a nondeterministic ICU collation | **Confirmed** | Ran each on 18.2 and again on 18.6; the unique index correctly rejected a case-variant duplicate |
| A later 18 minor lifts any of these restrictions | **Refuted** | Re-run on 18.6, 18.4, 18.3 and 18.2. All four restrictions and the trigram unsoundness are identical on every one. Minor releases carry only fixes by policy. 18.5 was never released |
| 18.2 and 18.6 are therefore interchangeable | **Refuted** | They are not. A mixed-collation `EXISTS` semijoin returns 4 rows on 18.2 and 18.3 against a correct 2 on 18.4 and 18.6, and a backslash in a `LIKE` pattern matched nothing before 18.6. Both reproduced locally. The first claim is about the restrictions, this one is about correctness around them |
| `postgres:18` is a safe pin | **Refuted** | The tag moved from 18.2 to 18.6 under a live container on this machine, and the two base variants record different collation versions for the same collation |
| A `tsvector` over a collated column agrees with that collation | **Refuted** | It is accent-sensitive while the column is not: 3 rows and 1 row for two spellings against a true 4. A copied text search configuration with `unaccent` fixes it; a generated column over `unaccent` is rejected as not immutable |
| An index on `lower(col) COLLATE "C"` over a collated column is sound | **Confirmed** | Index and sequential scan agree, which makes it the correct escape hatch for indexed case-insensitive search |
| MERGE, `UNIQUE NULLS NOT DISTINCT`, stored generated columns, jsonb equality and `timestamp(0)` all behave as the plan needs on 18.6 | **Confirmed** | Ran each; `timestamp(0)` rounds rather than truncating |
| `ILIKE`, regex, `starts_with` and `text_pattern_ops` work on that collation | **Refuted** | All four fail with a hard error. The plan's ILIKE decision was wrong and is corrected |
| A trigram index is simply unavailable on that collation | **Refuted, and worse** | It builds, the planner uses it, and it returns a subset. Equality returned 0 rows where the answer is 1 |
| PostgreSQL 17 would do instead of 18 | **Refuted** | Plain `LIKE` errors outright on 17.10 |
| A `numeric` parameter against a `bigint` column still uses the index | **Refuted** | 200,000-row test: `bigint` gives an index scan, `numeric` gives a parallel sequential scan |
| jOOQ's MySQL-style upsert preserves secondary-unique-key semantics on PostgreSQL | **Refuted** | Read jOOQ 3.20.5 sources: `MERGE` in some conditions, primary-key fallback in others |
| MySQL strips `CHAR` padding on read and PostgreSQL does not | **Confirmed with a twist** | Confirmed on both; expressions coercing to `text` silently strip it, so one column reads two ways |
| MySQL and PostgreSQL place nulls oppositely in both directions | **Confirmed** | Ran both directions on both engines |
| The schema census and the JDBC `currentSchema` property and framework-pinned versions | **Confirmed** | Queried the live schema and read the driver and the dependency list; several source figures were wrong and are corrected here |

### Asserted, and gating

**Failure forces a redesign.** That a hand-written binding keeps `ULong` on the wire as `bigint`
on both drivers, and that code generation keeps the existing enum classes, so 481 and 250 files
compile untouched. That PostgreSQL-shaped generated code passes security's suite against MySQL,
which is the entire basis of the dual-dialect image and whose failure reddens five services'
deploys. That the reactive driver, **which nobody has run at all**, decodes native enums and
collated varchar without codec registration and does not repeat the `numeric` mistake on its own
`ULong` path. Five of eleven services are reactive, including security.

**Failure costs time with a known fallback.** Flyway resolving a vendor placeholder with both
database modules on one classpath; a command-line-written history validating at boot; the copy tool
honouring cast rules and finishing inside the window; a production dump restoring into a
same-major container; the Quartz PostgreSQL delegate under clustering; Testcontainers running 18
with ICU; asyncpg's parameter typing against a collated column.

**Failure is an operational surprise.** That the connection total after both fixes leaves room;
that the trigger fires exactly when intended across both nullabilities; that the cache clear leaves
nothing stale; that worker's rebuild does not lose schedule state beyond the pause state already
identified.

**The collision gate has never been run against production data.** Everything known about email
collisions, source spellings and file name case comes from the local copy. If production is
materially dirtier, the gate blocks the cutover pending data cleanup owned by people outside the
programme. That is calendar risk, and there is no stated rule for proceeding with known unresolved
collisions. Decide the rule before P3.

Nine further claims need console access or production data and are listed in section 11.

---

## 10. Decommission gate

The three MySQL DB Systems may be stopped, then deleted, only when all of the following hold for
that environment.

1. Every migrated schema has been live on PostgreSQL for at least 14 days after the final wave,
   with no open incident attributed to PostgreSQL and an audit showing no application connections
   to any migrated MySQL schema throughout.
2. A retained manual DB System backup exists, marked to survive deletion. (The pre-wave-A backup
   from section 8 is separate and also retained.)
3. A final per-schema dump, taken with a client at least as new as the server, compressed with a
   checksum manifest, **restore-tested into a throwaway container of the same release series** with
   matching row counts, uploaded to a retention-tagged bucket: 365 days for prod, 90 elsewhere.
4. The verification artifacts for every schema archived alongside the dump and signed off by each
   service's owner.
5. A pgBackRest full and differential restore tested within the previous 7 days, and for prod the
   standby promotion drill executed once on stage.
6. The old MySQL admin password **revoked at the DB System, not merely unused**, and the rotation
   recorded with who performed it and when.
7. The unmigrated `entity_collector` schema and the retired message tables present in the final
   dump, and the adzump decision from section 2 applied consistently.
8. **A named holder for the console identity** that performs the irreversible steps, with a second
   reviewer required for the stop and delete.

Order is dev, stage, prod, with at least one release between stage and prod deletion. Per
environment: stop, wait 7 days, delete with backups retained, remove the port 3306 ingress rules,
then grep every repository **and every operator note** for the MySQL hostname. Only after all three
are gone does the follow-up release remove the MySQL drivers, the Flyway MySQL module, the vendor
directories and the local MySQL compose, and only if the product decision in section 11 says so.

---

## 11. What needs a decision from you

Not engineering choices. The first eight block the schema generator or the cutover.

**1. Case policy, and which account survives.** The plan keeps today's behaviour through
collation. Either way, 52 email addresses collide once case is ignored and **5 collide inside a
single client**, where uniqueness is enforced, so somebody must say which is the real account.
This gates the collision report.

**2. Ticket source spellings.** 34 spellings for 21 real values, and 261 tickets that match their
source row only case-insensitively. Somebody picks the canonical spelling per tenant, or accepts
that reports keep splitting.

**3. File name case.** 31 files differ from a sibling only by case, with no unique constraint on
name within a folder today. Collation preserves current behaviour; case sensitivity means uploads
that used to overwrite start creating siblings.

**4. Should transactions become real?** They do nothing for jOOQ today, so the wallet ledger and
the profile-role rewrite are already non-atomic. The plan fixes it because the pool defect must be
fixed anyway and they share a root cause. The alternative is to delete the annotations and document
autocommit honestly. Fixing it changes error semantics in four named flows, so it should be a
decision, not a side effect.

**5. What data actually moves.** The core token table holds roughly 7920 rows of third-party OAuth
secrets of which about 1859 are live: carry the history or only the active ones. The AI database
holds user-visible chat history and curated knowledge: copy or start fresh. The retired message
tables and `entity_collector`: archive only, which is what the plan assumes.

**6. Does adzump migrate?** It is deployed nowhere, carries a large share of the type work, and has
a pre-existing write-path defect. Descope it, or schedule it and fix that defect on MySQL first.

**7. Does MySQL remain a supported self-host engine?** The dual-dialect image is the plan's central
safety property and, for free, a two-engine product. The follow-up release deletes it, which makes
PostgreSQL 18.6 with an ICU collation a hard prerequisite for every self-hoster and every contributor
build. That is a positioning and support-load decision, and right now it is being made implicitly
by a cleanup ticket. Decide explicitly: PostgreSQL only, and rewrite the self-host book; or keep
dual dialect as supported, and put MySQL in the CI matrix permanently instead of deleting it.

**8. Recovery objectives.** The plan proposes a standby with **manual** promotion. If the real
recovery time objective is tighter than a human noticing, that means automatic failover, more VMs
and a different design. Also, every bucket is in Ashburn alongside the VCN: decide whether the
backup repository needs replication to a second region.

**9. Secrets, with an owner and a date each.** One shared `admin` password, committed in plaintext,
used by all eleven schemas in all three environments. Beyond it: a git-tracked settings file in
`nocode-ai` carrying about 48 entries with the production password, a live GitHub token committed
in all three compose files (the very files this migration edits), bridge secrets in two of them,
and the production password in an operator note. The likely failure is that the credential is
"rotated" by being replaced in the same shape and the old one never revoked, which is why that is a
gate item.

**10. Cost shape.** Dev and stage can share one VM with two clusters, or get one each. Four VMs
including the production standby, and quota needs confirming.

**11. Monitoring.** There is no database observability today: the Grafana config directory is empty
and the monitoring folder is a desktop port pinger. Prometheus and Grafana on the analytics hosts,
or OCI Monitoring. New build either way, and it must exist before the first cutover.

### Facts I could not check, which need the console or a host shell

- The shapes, storage and backup retention of the three app hosts and the three DB Systems.
  Nothing in any repository records them, and they size the new VMs and the final-dump bucket.
- Whether the production `multi` database is really empty, as local is. The plan deletes that
  service's relational stack on that basis.
- Whether production MySQL 8.4 runs the same `sql_mode`, `explicit_defaults_for_timestamp` and
  timezone as local. Every semantic finding assumes it does.
- Whether the pending WhatsApp branch migrations are in production yet. The baseline cannot be cut
  until every environment's Flyway head is known, and cutting from the wrong head silently omits or
  invents columns.
- Whether quota allows four more VMs with block volumes in the app VCN and a second fault domain.
- The performance of collated equality on a realistic token table, which decides whether the
  collation list needs to shrink.

---

## 12. Documentation and operator notes

This is a deliverable with an inventory, not a tidy-up. Twenty-one published pages under the docs
site mention MySQL, two with `mysql` in the public slug, so redirects are a decision. The
self-host book lists MySQL as required infrastructure, which is the external instruction for
running Modlix and therefore a product artifact. Beyond it: roughly twenty files under the
contribution docs, the MCP agent context directories, the monorepo instructions file, and the
operator memory notes, one of which contains a production MySQL password in plaintext and another
of which documents the local MySQL workflow. Those notes are the fastest route by which someone
connects to a database that no longer exists, or leaks a credential that should already have been
rotated, so they belong in the decommission grep rather than in an afterthought.

---

## 13. Effort

About **130 engineer-days**, three engineers, roughly four to five months to the final production
wave plus two weeks of retention before decommissioning. The phase table sums to 121; the balance
is contingency for the P0 fallbacks, which is deliberate given that three claims could force a
redesign rather than a patch.

The table below is a second cut of the same work, by area rather than by phase, and the two are
not meant to reconcile line by line. It sums to between 119 and 125 because it counts engineering
per subsystem and excludes the work that belongs to no single one: the spikes, the rehearsals, the
cutover execution itself and the decommission. Areas also overlap, most obviously the semantic
layer with security and with the messaging pair.

| Subsystem | Days |
|---|---|
| Security service | 28 |
| Infrastructure, configuration, monitoring and tooling | 17 |
| Semantic layer: collation, time, nulls, char | 15 |
| Entity-processor and message | 12 to 15 |
| Shared persistence layer, bindings and code generation | 10 |
| Non-Java consumers, mostly nocode-ai | 8 to 11 |
| Identity, upserts, transactions | 8 |
| Schema generator and rulebook | 8 |
| Imperative services including worker and Quartz | 7 |
| Core, multi and the remaining modules | 6 |

The single largest line is security, and it is also the last thing to move, so it carries the most
soak time.
