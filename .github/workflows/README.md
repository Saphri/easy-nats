# GitHub Actions Workflows

This directory contains automated workflows for building, testing, and releasing the Quarkus EasyNATS extension.

## Important: This is a Quarkus Extension, Not an Application

**Key Distinction:**
- **Extension** = Library that other Quarkus applications depend on (this project)
- **Application** = Executable Quarkus app that uses extensions

**What this means for CI/CD:**
- ✅ We build and publish extension JARs (runtime + deployment modules)
- ✅ We validate that applications using our extension can be compiled to native
- ❌ We do NOT distribute native executables (users build their own)
- ❌ The integration-tests native executable is for validation only, not distribution

**Artifacts:**
- **Published:** `quarkus-easy-nats-{version}.jar` and `quarkus-easy-nats-deployment-{version}.jar`
- **Not Published:** Native executables from integration-tests (testing only)

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

### 🏗️ Native Compatibility Validation (`native-build.yml`)

**Trigger:** Runs on push to `main`/`master`, pull requests, and manual dispatch

**Purpose:** Validate that the extension is compatible with GraalVM native image compilation

**What it does:**
- Builds the extension JARs (runtime + deployment)
- Validates the extension descriptor is present
- Compiles a test application (integration-tests) that uses the extension to native
- Runs integration tests in native mode
- Confirms the extension works correctly when included in native applications

**Steps:**
1. Checkout code
2. Set up Mandrel GraalVM (23.1.5.0-Final for Java 21)
3. Build extension JARs
4. Validate extension descriptor exists
5. Build test app as native image (validates extension compatibility)
6. Run native integration tests
7. Upload extension JARs (validated for native) and logs

**Matrix:** Tests on Ubuntu with Mandrel 23.1.5.0-Final

**Note:** Native validation takes significantly longer (10-30 minutes) than JVM builds.

**Why this matters for extensions:**
- Ensures the extension doesn't use reflection or dynamic features incompatible with native
- Validates build-time processing works correctly in native mode
- Confirms CDI beans are properly registered for native compilation
- Catches native-specific issues before users encounter them

**Important:** The native executable created is a test application, not a distributable artifact.

---

### 🚀 Release (`release.yml`)

**Trigger:** Manual workflow dispatch via GitHub UI

**Purpose:** Create versioned releases and publish artifacts

**Required Inputs:**
- `version`: Release version (e.g., `1.0.0`)
- `next-version`: Next development version (e.g., `1.1.0-SNAPSHOT`)

**Optional Inputs:**
- `validate-native`: Validate native compatibility (default: `false`)
  - When `false`: Builds and tests extension in JVM mode only (faster, ~5 minutes)
  - When `true`: Validates extension works with native compilation via Mandrel (slower, ~20-30 minutes)

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

**Artifacts Published (Extension JARs):**
- `quarkus-easy-nats-{version}.jar` (runtime module)
- `quarkus-easy-nats-deployment-{version}.jar` (deployment module)

**Note:** Native validation does NOT produce distributable native executables. It validates that applications using this extension can be compiled to native. The release always contains only the extension JARs.

**JDK Options:**
- **JVM Build:** Uses Eclipse Temurin JDK 21
- **Native Validation:** Uses Mandrel GraalVM 23.1.5.0-Final (Red Hat's downstream distribution of GraalVM, optimized for Quarkus)

---

## How to Use

### Running a Release

#### Standard Release (JVM mode only, ~5 minutes)

1. Go to **Actions** tab in GitHub
2. Select **Release** workflow
3. Click **Run workflow**
4. Fill in the inputs:
   - **version**: `1.0.0` (example)
   - **next-version**: `1.1.0-SNAPSHOT` (example)
   - **validate-native**: Leave unchecked (default)
5. Click **Run workflow**

#### Native-Validated Release (~20-30 minutes)

For releases where you want to guarantee native compatibility:

1. Go to **Actions** tab in GitHub
2. Select **Release** workflow
3. Click **Run workflow**
4. Fill in the inputs:
   - **version**: `1.0.0` (example)
   - **next-version**: `1.1.0-SNAPSHOT` (example)
   - **validate-native**: ✅ **Check this box**
5. Click **Run workflow**

The workflow will:
- Create a Git tag `v1.0.0`
- Create a GitHub Release with extension JARs
- Publish extension JARs to GitHub Packages
- Update POM versions to `1.1.0-SNAPSHOT` for next development
- (If validate-native enabled) Confirm the extension works in native mode

### Running Native Validation Manually

To validate native compatibility without creating a release:

1. Go to **Actions** tab in GitHub
2. Select **Native Compatibility Validation** workflow
3. Click **Run workflow**
4. Select branch to validate
5. Click **Run workflow**

This is useful for:
- Testing native compatibility before release
- Validating that extension changes don't break native compilation
- Ensuring reflection/CDI configuration is correct for native mode
- Getting validated extension JARs as artifacts

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

#### JVM Builds

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

#### Native Validation (Extension Development)

To validate native compatibility on your local machine:

**Prerequisites:**
- Install Mandrel GraalVM or GraalVM Community Edition
- Set `GRAALVM_HOME` environment variable

**Validation Commands:**

```bash
# Validate extension works in native mode
./mvnw clean install -Dnative -Pit

# Build extension and validate without tests
./mvnw clean install -Dnative -DskipTests -Pit

# Run only native integration tests
./mvnw verify -Dnative -Pit

# Run the test application (integration-tests module) natively
./integration-tests/target/*-runner
```

**Important:** The native executable in `integration-tests/target/` is a test application that uses your extension. It's for validation only - you don't distribute this to users. Users will include your extension JARs in their own Quarkus applications and build their own native executables.

**Install Mandrel (recommended for Quarkus):**

```bash
# Using SDKMAN (Linux/macOS)
sdk install java 21.0.5-mandrel
sdk use java 21.0.5-mandrel

# Using package manager (Fedora/RHEL)
sudo dnf install mandrel-java21-devel

# Verify installation
java -version
native-image --version
```

**Performance:**
- JVM build: ~1-2 minutes
- Native build: ~10-15 minutes (first time)
- Native build: ~5-8 minutes (with cache)

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

### Native validation fails

Common issues for Quarkus extensions:

**Extension descriptor missing:**
- Ensure `runtime/src/main/resources/META-INF/quarkus-extension.yaml` exists
- Check that `quarkus-extension-maven-plugin` is configured
- Verify extension metadata is correct

**Out of memory during native compilation:**
- Native builds require significant memory (~4-8GB)
- GitHub Actions provides 7GB RAM for Ubuntu runners
- Integration tests may need memory tuning

**Reflection or CDI registration issues:**
- Extension's build steps may need to register classes for reflection
- Check `@RegisterForReflection` annotations
- Review QuarkusEasyNatsProcessor build steps
- Consult Quarkus extension development guide

**Tests pass in JVM but fail in native:**
- Extension may use features not compatible with native (reflection, dynamic proxies)
- Check deployment module's build-time processing
- Add reflection configuration via BuildStep methods
- Review native-image.properties if needed

**Native image compilation errors:**
- Check build logs in workflow artifacts
- Verify all extension dependencies are native-compatible
- Look for unsupported JDK features
- Test with a minimal Quarkus app using the extension

**Workflow timeout:**
- Native validation can take 20-30 minutes
- Default timeout is 360 minutes (6 hours)
- If timing out, check for infinite loops in tests

### Release workflow fails on version set

Ensure:
- Version format is valid (e.g., `1.0.0`, not `v1.0.0`)
- Next version includes `-SNAPSHOT` suffix
- No uncommitted changes in repository
- If using native builds, ensure adequate timeout

### GitHub Packages publish fails

Ensure:
- Repository has Packages enabled
- `GITHUB_TOKEN` has `write:packages` permission
- Distribution management in POM is correct

### Mandrel vs GraalVM

**Mandrel (Recommended for Quarkus):**
- Red Hat's downstream distribution of GraalVM
- Optimized specifically for Quarkus applications
- Fully supported by Red Hat
- Smaller download size
- Available in CI: `graalvm/setup-graalvm@v1` with `distribution: 'mandrel'`

**GraalVM Community Edition:**
- Oracle's upstream distribution
- More general-purpose native image support
- Larger feature set (Polyglot, LLVM, etc.)
- Use if you need non-Quarkus features

**For this project:** We use Mandrel 23.1.5.0-Final which corresponds to GraalVM 23.1 and supports Java 21.

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
