# GitHub Actions Workflows

This directory contains automated workflows for building, testing, and releasing the Quarkus EasyNATS extension.

## Workflows

### 🔨 Build and Test (`build.yml`)

**Trigger:** Runs on every push to `main`/`master` and on all pull requests

**Purpose:** Validates that the project builds correctly and all tests pass

**Steps:**
1. Checkout code
2. Set up JDK 21 (with Maven cache)
3. Build project (`./mvnw clean install -DskipTests`)
4. Run unit tests (`./mvnw test`)
5. Run integration tests (`./mvnw verify -Pit`)
6. Upload test results and build artifacts

**Matrix:** Currently tests on JDK 21 (can be extended to test on multiple Java versions)

---

### ✅ PR Validation (`pr-validation.yml`)

**Trigger:** Runs on pull request events (opened, synchronized, reopened)

**Purpose:** Comprehensive validation of pull requests with automated feedback

**Steps:**
1. Checkout code
2. Set up JDK 21
3. Build project
4. Run all tests (unit + integration)
5. Check code formatting
6. Generate PR report summary
7. Post automated comment on PR with validation status

---

### 🚀 Release (`release.yml`)

**Trigger:** Manual workflow dispatch via GitHub UI

**Purpose:** Create versioned releases and publish artifacts

**Required Inputs:**
- `version`: Release version (e.g., `1.0.0`)
- `next-version`: Next development version (e.g., `1.1.0-SNAPSHOT`)

**Steps:**
1. Set release version in POMs
2. Build and test with integration tests
3. Commit version change
4. Create Git tag (`v{version}`)
5. Push tag to GitHub
6. Create GitHub Release with artifacts (runtime + deployment JARs)
7. Set next development version in POMs
8. Commit and push development version
9. Publish artifacts to GitHub Packages

**Artifacts Published:**
- `runtime/target/quarkus-easy-nats-{version}.jar`
- `deployment/target/quarkus-easy-nats-deployment-{version}.jar`

---

## How to Use

### Running a Release

1. Go to **Actions** tab in GitHub
2. Select **Release** workflow
3. Click **Run workflow**
4. Fill in the inputs:
   - **version**: `1.0.0` (example)
   - **next-version**: `1.1.0-SNAPSHOT` (example)
5. Click **Run workflow**

The workflow will:
- Create a Git tag `v1.0.0`
- Create a GitHub Release with release notes
- Publish artifacts to GitHub Packages
- Update POM versions to `1.1.0-SNAPSHOT` for next development

### Consuming Published Artifacts

To use artifacts published to GitHub Packages, add to your Maven `settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
  </server>
</servers>
```

And add the repository to your project POM:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/OWNER/REPO</url>
  </repository>
</repositories>
```

### Local Testing

You can test the build steps locally:

```bash
# Build and test (same as CI)
./mvnw clean install -DskipTests
./mvnw test
./mvnw verify -Pit

# Test release build (without publishing)
./mvnw versions:set -DnewVersion=1.0.0-TEST
./mvnw clean install -Pit
./mvnw versions:revert
```

---

## Workflow Permissions

The workflows require the following permissions:

- **build.yml**: Read access to repository
- **pr-validation.yml**: Read repository + Write issues (for PR comments)
- **release.yml**: Write contents + Write packages

These are configured via `permissions:` in each workflow file.

---

## Secrets Required

### For GitHub Packages

No additional secrets needed - uses `GITHUB_TOKEN` automatically provided by GitHub Actions.

### For Maven Central (future)

If publishing to Maven Central, you'll need:
- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_TOKEN`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`

Add these via **Settings → Secrets and variables → Actions**.

---

## Troubleshooting

### Build fails on integration tests

The integration tests require Docker for NATS containers. GitHub Actions runners have Docker pre-installed, but if tests fail:

1. Check `docker-compose-devservices.yml` is present
2. Verify NATS container starts correctly
3. Check test logs in workflow run artifacts

### Release workflow fails on version set

Ensure:
- Version format is valid (e.g., `1.0.0`, not `v1.0.0`)
- Next version includes `-SNAPSHOT` suffix
- No uncommitted changes in repository

### GitHub Packages publish fails

Ensure:
- Repository has Packages enabled
- `GITHUB_TOKEN` has `write:packages` permission
- Distribution management in POM is correct

---

## Maintenance

### Adding New Workflows

1. Create workflow file in `.github/workflows/`
2. Use YAML syntax (`.yml` or `.yaml`)
3. Test locally with [act](https://github.com/nektos/act) if possible
4. Document in this README

### Updating Java Version

To change Java version:

1. Update `matrix.java` in `build.yml`
2. Update JDK setup version in all workflows
3. Update `maven.compiler.release` in `pom.xml`

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Maven Release Plugin](https://maven.apache.org/maven-release/maven-release-plugin/)
- [GitHub Packages for Maven](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Quarkus CI/CD Guide](https://quarkus.io/guides/continuous-testing)
