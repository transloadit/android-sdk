# Contributing to the Transloadit Android SDK

Thanks for your interest in contributing! This document explains how to get set up, run tests, and how releases are produced for the Android library.

## Getting Started

1. Fork the repository and clone your fork.
2. Install JDK 11+ (CI currently uses JDK 19 via `actions/setup-java`).
3. Install [Android SDK command line tools](https://developer.android.com/studio#command-tools) if you plan to run Gradle directly outside of Docker.
4. Install [Docker](https://docs.docker.com/get-docker/) if you want to mirror the CI environment.
5. Run `./gradlew assemble` to make sure the project builds.

## Running Tests

We rely on standard Gradle tasks and an optional Docker wrapper:

- **Host JVM:** `./gradlew check` runs unit tests for both the library and example app.
- **Docker (CI parity):** `./scripts/test-in-docker.sh` executes the same Gradle tasks inside the image used in CI. This is helpful before pushing changes to ensure a clean environment.

End-to-end tests hit the live Transloadit API. To enable them locally, create a `.env` file with:

```
TRANSLOADIT_KEY=your-key
TRANSLOADIT_SECRET=your-secret
```

Without these variables the integration tests are skipped automatically.

## Pull Requests

- Keep changes focused. For larger efforts, please open an issue first to discuss the approach.
- Add or update tests alongside code changes.
- Run `./gradlew check` (and optionally the docker script) before submitting the PR.
- Provide context in the PR description, including any manual testing performed.

## Publishing Releases

Releases are handled by the Transloadit maintainers through the [release workflow](./.github/workflows/release.yml), which publishes artifacts to Maven Central under `com.transloadit.android:transloadit-android`.

High-level checklist for maintainers:

1. Update version information in `transloadit-android/src/main/resources/android-sdk-version/version.properties` and refresh `CHANGELOG.md`.
2. Merge the release branch into `main`.
3. Create a git tag for `main` that matches the new version
4. Publish a GitHub release (include the changelog). This triggers the release workflow.
5. Wait for Sonatype to sync the artifact (this can take a few hours).

The required signing keys and credentials are stored as GitHub secrets. If you need access or spot an issue with the release automation, please reach out to the Transloadit team via the issue tracker or support channels.
