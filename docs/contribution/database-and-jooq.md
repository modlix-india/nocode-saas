# Database & JOOQ

This document covers database architecture, JOOQ code generation, Flyway migrations, and the DAO layer patterns.

## Database Architecture

Each microservice owns its own MySQL schema. No schema is shared between services.

| Service | Schema Name | Access Type |
|---------|-------------|-------------|
| security | `security` | R2DBC (reactive) |
| core | `core` | R2DBC + MongoDB |
| files | `files` | R2DBC |
| message | `message` | R2DBC |
| notification | `notification` | JDBC |
| entity-collector | `entity_collector` | R2DBC |
| entity-processor | `entity_processor` | R2DBC |
| multi | `multi` | R2DBC |
| ui | — | MongoDB only |

### Dual Connection Pattern

Most services use **two** database connections:
- **R2DBC** (`r2dbc:mysql://...`) — For reactive application queries via JOOQ
- **JDBC** (`jdbc:mysql://...`) — For Flyway migrations (Flyway doesn't support R2DBC)

Configuration example from `configfiles/application-default.yml`:
```yaml
security:
  db:
    url: r2dbc:mysql://localhost:3306/security?serverTimezone=UTC
    username: root
    password: <password>
    flyway:
      url: jdbc:mysql://localhost:3306/security
```

## JOOQ Code Generation

[JOOQ](https://www.jooq.org/) generates type-safe Java classes from the MySQL schema. These generated classes provide compile-time safety for all database queries.

### Generated Code Location

Each service has JOOQ-generated code in:
```
{module}/src/main/java/com/fincity/{module}/jooq/
├── DefaultCatalog.java
├── Keys.java                    # Primary keys and unique keys
├── Indexes.java                 # Database indexes
├── Security.java                # Schema reference
├── Tables.java                  # All table references
├── enums/                       # Database enums as Java enums
│   ├── SecurityClientStatusCode.java
│   ├── SecurityUserStatusCode.java
│   └── ...
├── tables/                      # Table definitions
│   ├── SecurityClient.java      # Table class with typed fields
│   ├── SecurityUser.java
│   └── ...
└── tables/records/              # Record classes
    ├── SecurityClientRecord.java
    ├── SecurityUserRecord.java
    └── ...
```

**Do not manually edit files in the `jooq/` package.** They are regenerated from the database schema.

### Running JOOQ Generation

```bash
./runmvn.sh jooq
```

Prerequisites:
1. MySQL must be running with all schemas created
2. Flyway migrations must have been applied (they run automatically during the JOOQ build)

The JOOQ build connects to your local MySQL to introspect schemas. Connection settings are in each module's `pom.xml` under the `jooq` profile.

### JOOQ Maven Profile

From `security/pom.xml`, the `jooq` profile configures code generation:

```xml
<profile>
    <id>jooq</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.jooq</groupId>
                <artifactId>jooq-codegen-maven</artifactId>
                <configuration>
                    <jdbc>
                        <driver>com.mysql.cj.jdbc.Driver</driver>
                        <url>jdbc:mysql://localhost:3306/security</url>
                        <user>root</user>
                        <password>Kiran@123</password>
                    </jdbc>
                    <generator>
                        <database>
                            <includes>security_.*</includes>
                            <inputSchema>security</inputSchema>
                            <excludes>
                                security_package | security_package_role | ...
                            </excludes>
                        </database>
                        <generate>
                            <records>true</records>
                            <fluentSetters>true</fluentSetters>
                        </generate>
                        <target>
                            <packageName>com.fincity.security.jooq</packageName>
                            <directory>src/main/java</directory>
                        </target>
                    </generator>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Key settings:
- `includes: security_.*` — Only tables matching this pattern are generated
- `excludes` — Tables that should NOT be generated (deprecated/removed tables)
- `fluentSetters: true` — Generated records have fluent setter methods
- Target package: `com.fincity.security.jooq`

### Custom Type Converters

JSON columns in MySQL are mapped to Java `Map` using a custom converter:

```xml
<forcedTypes>
    <forcedType>
        <userType>java.util.Map</userType>
        <converter>com.fincity.saas.commons.jooq.convertor.JSONMysqlMapConvertor</converter>
        <includeExpression>ARRANGEMENT|TOKEN_METADATA|...</includeExpression>
        <includeTypes>JSON</includeTypes>
    </forcedType>
</forcedTypes>
```

## Flyway Migrations

### Location

Each service stores migrations in:
```
{module}/src/main/resources/db/migration/
```

### Naming Convention

```
V{n}__{Description}.sql
```

- `V` — Version prefix (required)
- `{n}` — Sequential version number (1, 2, ..., 71)
- `__` — Double underscore separator (required)
- `{Description}` — Human-readable description with spaces

Examples:
```
V1__Initial script.sql
V34__Create Client Hierarchy.sql
V71__Add Client Manager.sql
```

### Writing a New Migration

1. Find the latest version number in `src/main/resources/db/migration/`
2. Create a new file with the next version number
3. Write your SQL

Example migration (`V71__Add Client Manager.sql`):
```sql
USE `security`;

CREATE TABLE `security`.`security_client_manager`
(
    `ID`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `CLIENT_ID`  BIGINT UNSIGNED NOT NULL COMMENT 'Client ID',
    `MANAGER_ID` BIGINT UNSIGNED NOT NULL COMMENT 'Manager user ID',
    `CREATED_BY` BIGINT UNSIGNED DEFAULT NULL COMMENT 'ID of the user who created this row',
    `CREATED_AT` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',

    PRIMARY KEY (`ID`),
    UNIQUE KEY `UK1_CLIENT_MANAGER_CLIENT_ID_MANAGER_ID` (`CLIENT_ID`, `MANAGER_ID`),

    CONSTRAINT `FK1_CLIENT_MANAGER_CLIENT_ID`
        FOREIGN KEY (`CLIENT_ID`) REFERENCES `security`.`security_client` (`ID`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `FK2_CLIENT_MANAGER_USER_ID`
        FOREIGN KEY (`MANAGER_ID`) REFERENCES `security`.`security_user` (`ID`)
        ON DELETE CASCADE ON UPDATE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = `utf8mb4`;
```

### Migration Conventions

- Always start with `USE \`{schema}\`;`
- Table names: `{schema}_{entity}` (e.g., `security_client_manager`)
- Column names: `UPPER_SNAKE_CASE`
- Use `BIGINT UNSIGNED` for IDs
- Include `CREATED_BY`, `CREATED_AT` audit columns
- Include `UPDATED_BY`, `UPDATED_AT` for updatable tables
- Add `COMMENT` to every column
- Use `ENGINE = InnoDB` and `DEFAULT CHARSET = utf8mb4`
- Add foreign key constraints with meaningful names: `FK{n}_{TABLE}_{COLUMN}`
- Add unique key constraints: `UK{n}_{TABLE}_{COLUMNS}`

### After Adding a Migration

1. Run the JOOQ build to regenerate classes:
   ```bash
   ./runmvn.sh jooq
   ```
2. Create/update the corresponding DTO, DAO, Service, and Controller
3. Update tests

## DAO Layer

### Base Classes

```
AbstractDAO<R, I, D>                    # Read-only data access
└── AbstractUpdatableDAO<R, I, D>       # + create/update/delete
```

### Creating a New DAO

```java
@Service
public class ClientManagerDAO extends AbstractUpdatableDAO<SecurityClientManagerRecord, ULong, ClientManager> {

    protected ClientManagerDAO() {
        super(ClientManager.class, SECURITY_CLIENT_MANAGER, SECURITY_CLIENT_MANAGER.ID);
    }
}
```

Constructor arguments:
1. DTO class (e.g., `ClientManager.class`)
2. JOOQ table reference (static import from generated code)
3. JOOQ ID field reference

### Using JOOQ DSLContext

The `dslContext` field (inherited from `AbstractDAO`) provides the JOOQ query builder:

```java
// Simple read by ID
public Mono<Client> readInternal(ULong id) {
    return Mono.from(this.dslContext.selectFrom(this.table)
            .where(this.idField.eq(id))
            .limit(1))
            .map(e -> e.into(this.pojoClass));
}

// Read by custom field
public Mono<Client> getClientBy(String clientCode) {
    return Flux.from(this.dslContext.select(SECURITY_CLIENT.fields())
            .from(SECURITY_CLIENT)
            .where(SECURITY_CLIENT.CODE.eq(clientCode))
            .limit(1))
            .singleOrEmpty()
            .map(e -> e.into(Client.class));
}

// Insert with conditional existence check
public Mono<Integer> createIfNotExists(ULong clientId, ULong managerId, ULong createdBy) {
    return Mono.from(this.dslContext
            .insertInto(SECURITY_CLIENT_MANAGER)
            .columns(SECURITY_CLIENT_MANAGER.CLIENT_ID,
                     SECURITY_CLIENT_MANAGER.MANAGER_ID,
                     SECURITY_CLIENT_MANAGER.CREATED_BY)
            .values(clientId, managerId, createdBy)
            .onDuplicateKeyIgnore())
            .map(Record1::value1);
}

// Join query
public Flux<ULong> getManagerIds(ULong clientId) {
    return Flux.from(this.dslContext
            .select(SECURITY_CLIENT_MANAGER.MANAGER_ID)
            .from(SECURITY_CLIENT_MANAGER)
            .where(SECURITY_CLIENT_MANAGER.CLIENT_ID.eq(clientId)));
}
```

### Static Imports

Always use static imports for JOOQ-generated tables and fields:

```java
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityUser.SECURITY_USER;
import static com.fincity.security.jooq.tables.SecurityClientManager.SECURITY_CLIENT_MANAGER;
```

### Reactive Wrapping

JOOQ queries return Publisher types. Wrap them with Project Reactor:
- `Mono.from(...)` — For single-result queries
- `Flux.from(...)` — For multi-result queries
- `.map(e -> e.into(PojoClass.class))` — Convert JOOQ Record to DTO

## Adding a New Database Entity (End-to-End)

1. **Write Flyway migration** — Create `V{n}__Description.sql` in `src/main/resources/db/migration/`

2. **Run JOOQ generation** — `./runmvn.sh jooq` to generate record, table, and field classes

3. **Create DTO** — Extend `AbstractUpdatableDTO<ULong, ULong>`:
   ```java
   @Data
   @Accessors(chain = true)
   @EqualsAndHashCode(callSuper = true)
   @ToString(callSuper = true)
   public class MyEntity extends AbstractUpdatableDTO<ULong, ULong> {
       private String name;
       private ULong clientId;
   }
   ```

4. **Create DAO** — Extend `AbstractUpdatableDAO`:
   ```java
   @Service
   public class MyEntityDAO extends AbstractUpdatableDAO<SecurityMyEntityRecord, ULong, MyEntity> {
       protected MyEntityDAO() {
           super(MyEntity.class, SECURITY_MY_ENTITY, SECURITY_MY_ENTITY.ID);
       }
   }
   ```

5. **Create Service** — Extend appropriate base service:
   ```java
   @Service
   public class MyEntityService
           extends AbstractJOOQUpdatableDataService<SecurityMyEntityRecord, ULong, MyEntity, MyEntityDAO> {
       // Add @PreAuthorize to public methods
   }
   ```

6. **Create Controller** — Extend base controller:
   ```java
   @RestController
   @RequestMapping("api/security/myentities")
   public class MyEntityController
           extends AbstractJOOQUpdatableDataController<SecurityMyEntityRecord, ULong, MyEntity, MyEntityDAO, MyEntityService> {
   }
   ```

7. **Write tests** — See [testing-guide.md](testing-guide.md)
