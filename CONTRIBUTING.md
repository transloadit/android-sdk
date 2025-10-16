# Contributing to the Transloadit Android SDK

This document is put together to provide maintenance hints for the
[Transloadit Java SDK](https://github.com/transloadit/java-sdk) and
[Android SDK](https://github.com/transloadit/android-sdk).

## Publishing Releases

At the moment, releases for the Java SDK are done with a
[GH - Actions job](https://github.com/transloadit/java-sdk/blob/17823ed8d86fd09aded95884e9c4e9a2bb2ea1af/.github/workflows/release.yml).
The releases are published to
[Maven-Central](https://search.maven.org/artifact/com.transloadit.sdk/transloadit). As of the time
of writing, @cdr-chakotay and @Acconut are maintaining the Java-SDK. Here are the steps (_It is
assumed that you already have your dev environment setup for the Java SDK before attempting to
release_):

1. Verify, that the ENV - variables in the GH-Action are matching:

- The [Sonatype](https://oss.sonatype.org/) username and
  password[environment variables](<[https://github.com/transloadit/java-sdk/blob/1da83b8ac34df160bc3edf5f521f146d41533f82/build.gradle#L98-L99](https://github.com/transloadit/java-sdk/blob/17823ed8d86fd09aded95884e9c4e9a2bb2ea1af/build.gradle#L124-L125)](https://github.com/transloadit/java-sdk/blob/17823ed8d86fd09aded95884e9c4e9a2bb2ea1af/build.gradle#L124-L125)](https://github.com/transloadit/java-sdk/blob/17823ed8d86fd09aded95884e9c4e9a2bb2ea1af/build.gradle#L124)>)

- The
  [signing enviroment variables](https://github.com/transloadit/java-sdk/blob/17823ed8d86fd09aded95884e9c4e9a2bb2ea1af/build.gradle#L114-L116):
  GPG Key, the Key ID and the Signing-key's password. You can find the GPG-Key IDs in the
  [how-tos](https://github.com/transloadit/team-internals/blob/74e417fa2e568cceeb105a1ee1bd942a3273b2fb/_howtos/2021-05-17-handling-relase-signing-keys-javaSDKs.md).
  The secrets are currently held by @cdr-chakotay and @Acconut.

2. Update the release files where necessary
   ([here](https://github.com/transloadit/java-sdk/commit/2a87dec02b6caf778a563abe1e008ce9c6ad0480)
   and
   [here](https://github.com/transloadit/java-sdk/commit/1f54b3ce4fd202659953fe062985f8c7e43c40b4)).
3. Create a new Tag, mathching the SDK's semantic version numbers and draft a new release in order
   to start the release workflow. Please add the Changelog notes to the release. Draft a new release
   [here](https://github.com/transloadit/java-sdk/releases)
4. Wait a few hours until Sonatype lists the new release.
