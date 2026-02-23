# Git Workflow

This document covers the branching strategy, commit conventions, pull request process, and code review guidelines.

## Branch Strategy

### Main Branch

The primary branch is `master`. All feature and bugfix branches are created from and merged back into `master`.

### Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/{TICKET}-{description}` | `feature/CN-876-add-client-manager-apis-in-security` |
| Bugfix | `bugfix/{description}` | `bugfix/files-security-issues` |
| Bugfix (with ticket) | `bugfix/{TICKET}-{description}` | `bugfix/CX-465-Search-and-Sort-Functionality` |

**Guidelines**:
- Use lowercase with hyphens for descriptions
- Include the ticket number when available (e.g., `CN-876`, `CX-465`, `NCLC2-160`)
- Keep descriptions concise but descriptive

### Deployment Branches

These branches trigger CI/CD pipelines:

| Branch | Environment |
|--------|-------------|
| `oci-development` | Development |
| `oci-staging` | Staging |
| `oci-production` | Production |

Merging into these branches triggers the GitHub Actions workflow for the relevant environment.

## Commit Conventions

### Commit Message Format

Based on recent commit history, the project follows this pattern:

```
<Action verb> <description of change>
```

Examples from recent commits:
```
Add early return for user-client equality check in isUserClientManageClient method
Add isUserPartOfHierarchy method to ClientService and corresponding endpoint in ClientController
Add user authority mock setup in ClientManagerServiceTest and enhance Docker properties comments
Add ClientManager APIs to retrieve manager IDs and update Client DTO for client managers
```

### Guidelines

- **Start with an action verb**: `Add`, `Fix`, `Update`, `Remove`, `Refactor`, `Merge`
- **Be specific**: Mention the class/method name when relevant
- **Single responsibility**: Each commit should address one logical change
- **No period at the end** of the subject line

### Action Verb Reference

| Verb | When to use |
|------|-------------|
| `Add` | New feature, method, class, or file |
| `Fix` | Bug fix |
| `Update` | Enhancement to existing feature |
| `Remove` | Deleting code, features, or files |
| `Refactor` | Code restructuring without behavior change |
| `Merge` | Merge commits (auto-generated) |

## Pull Request Process

### Creating a PR

1. **Create a feature/bugfix branch** from `master`:
   ```bash
   git checkout master
   git pull origin master
   git checkout -b feature/TICKET-description
   ```

2. **Make your changes** following the [coding conventions](coding-conventions.md)

3. **Write tests** following the [testing guide](testing-guide.md)

4. **Build and verify locally**:
   ```bash
   cd <your-service>
   mvn clean install
   ```

5. **Push and create PR**:
   ```bash
   git push -u origin feature/TICKET-description
   ```
   Then create a PR via GitHub targeting `master`.

### PR Description

Include:
- **Summary**: What changed and why
- **Ticket reference**: Link to the related issue/ticket
- **Testing**: How you verified the changes
- **Migration notes**: If a Flyway migration was added, note the schema changes

### PR Checklist

Before requesting review, verify:

- [ ] Code compiles without errors (`mvn clean install`)
- [ ] All existing tests pass (`mvn test`)
- [ ] New tests are written for new functionality
- [ ] `@PreAuthorize` annotations added to new public service methods
- [ ] `FlatMapUtil` chains end with `.contextWrite(Context.of(LogUtil.METHOD_NAME, "..."))`
- [ ] Error handling uses `messageResourceService.throwMessage()`, not raw exceptions
- [ ] DTOs use `ULong` for IDs and `LocalDateTime` for timestamps
- [ ] DTO annotations are complete (`@Data`, `@Accessors(chain=true)`, `@EqualsAndHashCode(callSuper=true)`)
- [ ] If adding a database table: Flyway migration + JOOQ regeneration + DTO + DAO + Service + Controller
- [ ] No secrets or credentials in committed code

## Code Review Guidelines

### For Reviewers

Focus on:
- **Correctness**: Does the reactive chain handle all paths (success, empty, error)?
- **Security**: Are `@PreAuthorize` annotations present? Is multi-tenancy respected?
- **Conventions**: Does the code follow established patterns (see [coding conventions](coding-conventions.md))?
- **Tests**: Are edge cases covered? Are error cases tested?
- **Performance**: Are there unnecessary database calls? Is caching used appropriately?

### Common Review Issues

| Issue | What to look for |
|-------|-----------------|
| Missing `@PreAuthorize` | Public service methods exposed to users must have authorization |
| Blocking calls | `.block()`, `Thread.sleep()`, synchronous I/O in reactive chains |
| Missing `contextWrite` | `FlatMapUtil` chains without logging context |
| Missing `switchIfEmpty` | Chains that could complete empty without proper error handling |
| Wrong ID type | Using `Long` or `Integer` instead of `ULong` |
| Wrong timestamp type | Using `Date` instead of `LocalDateTime` |
| Hardcoded strings | Error messages not using `MessageResourceService` |
| Missing tenant check | DAO queries not filtered by client hierarchy |

## CI Checks

Before a PR can be merged, the following must pass:
- Maven build completes successfully
- All tests pass
- No compilation errors

GitHub Actions workflows automatically trigger based on changed paths. Ensure your changes don't break other services' builds by checking their path filters in `.github/workflows/`.

## Merging

- PRs are merged into `master` after approval
- Use **squash merge** for feature branches with many small commits
- Use **merge commit** for branches with well-structured commit history
- After merging to `master`, merge `master` into the appropriate deployment branch (`oci-development`, `oci-staging`, `oci-production`) to trigger deployment
