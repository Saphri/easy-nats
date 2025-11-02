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

### 🏗️ Native Build (`native-build.yml`)

**Trigger:** Runs on push to `main`/`master`, pull requests, and manual dispatch

**Purpose:** Build and test native executables using Mandrel GraalVM

**Steps:**
1. Checkout code
2. Set up Mandrel GraalVM (23.1.5.0-Final for Java 21)
3. Verify GraalVM installation
4. Build project in JVM mode
5. Build native executables (`./mvnw install -Dnative -Pit`)
6. Run native integration tests
7. Upload native executables and build logs

**Matrix:** Tests on Ubuntu with Mandrel 23.1.5.0-Final

**Note:** Native builds take significantly longer (10-30 minutes) than JVM builds due to ahead-of-time compilation.

**Benefits:**
- Fast startup time (~0.1s vs several seconds for JVM)
- Low memory footprint
- Ideal for containerized deployments and serverless
- Production-ready validation

---

### 🚀 Release (`release.yml`)

**Trigger:** Manual workflow dispatch via GitHub UI

**Purpose:** Create versioned releases and publish artifacts

**Required Inputs:**
- `version`: Release version (e.g., `1.0.0`)
- `next-version`: Next development version (e.g., `1.1.0-SNAPSHOT`)

**Optional Inputs:**
- `build-native`: Build native executables (default: `false`)
  - When `false`: Builds JVM artifacts only (faster, ~5 minutes)
  - When `true`: Builds JVM + native artifacts with Mandrel (slower, ~20-30 minutes)

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

JVM Artifacts (always):
- `runtime/target/quarkus-easy-nats-{version}.jar`
- `deployment/target/quarkus-easy-nats-deployment-{version}.jar`

Native Artifacts (if `build-native` is `true`):
- `integration-tests/target/*-runner` (native executable)

**JDK Options:**
- **JVM Build:** Uses Eclipse Temurin JDK 21
- **Native Build:** Uses Mandrel GraalVM 23.1.5.0-Final (Red Hat's downstream distribution of GraalVM, optimized for Quarkus)

---

## How to Use

### Running a Release

#### Standard Release (JVM artifacts only, ~5 minutes)

1. Go to **Actions** tab in GitHub
2. Select **Release** workflow
3. Click **Run workflow**
4. Fill in the inputs:
   - **version**: `1.0.0` (example)
   - **next-version**: `1.1.0-SNAPSHOT` (example)
   - **build-native**: Leave unchecked (default)
5. Click **Run workflow**

#### Native Release (JVM + Native artifacts, ~20-30 minutes)

1. Go to **Actions** tab in GitHub
2. Select **Release** workflow
3. Click **Run workflow**
4. Fill in the inputs:
   - **version**: `1.0.0` (example)
   - **next-version**: `1.1.0-SNAPSHOT` (example)
   - **build-native**: ✅ **Check this box**
5. Click **Run workflow**

The workflow will:
- Create a Git tag `v1.0.0`
- Create a GitHub Release with release notes
- Publish artifacts to GitHub Packages (JARs and optionally native executables)
- Update POM versions to `1.1.0-SNAPSHOT` for next development

### Running Native Builds Manually

To trigger a native build without creating a release:

1. Go to **Actions** tab in GitHub
2. Select **Native Build** workflow
3. Click **Run workflow**
4. Select branch to build from
5. Click **Run workflow**

This is useful for:
- Testing native compilation before release
- Validating native compatibility of changes
- Downloading native executables for testing

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

#### Native Builds

To build and test natively on your local machine:

**Prerequisites:**
- Install Mandrel GraalVM or GraalVM Community Edition
- Set `GRAALVM_HOME` environment variable

**Build Commands:**

```bash
# Build native executable (requires GraalVM/Mandrel)
./mvnw clean install -Dnative -Pit

# Build native executable without tests
./mvnw clean install -Dnative -DskipTests -Pit

# Test native executable
./mvnw verify -Dnative -Pit

# Run the native executable directly
./integration-tests/target/*-runner
```

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

### Native build fails

Common issues and solutions:

**Out of memory during native compilation:**
- Native builds require significant memory (~4-8GB)
- GitHub Actions provides 7GB RAM for Ubuntu runners
- Reduce native build complexity or split tests

**Native image compilation errors:**
- Check build logs in workflow artifacts
- Verify all dependencies are native-compatible
- Look for reflection or JNI configuration issues
- Consult Quarkus native build guides

**Tests pass in JVM but fail in native:**
- Native mode has stricter requirements
- May need additional reflection configuration
- Check for dynamic class loading or proxies
- Review `application.properties` for native-specific settings

**Workflow timeout:**
- Native builds can take 20-30 minutes
- Default timeout is 360 minutes (6 hours)
- If timing out, check for infinite loops or resource issues

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
