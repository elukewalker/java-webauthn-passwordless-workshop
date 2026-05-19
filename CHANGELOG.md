# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Changed
- Updated `java-webauthn-server` dependency from 1.x to 2.9.0
- Migrated to java-webauthn-server 2.x API following official migration guide
- Updated minimum Java requirement from JDK 1.8 to JDK 17
- Updated Spring Boot to 2.7.18 for Java 17 compatibility
- Replaced deprecated `RelyingParty` builder patterns with 2.x equivalents
- Removed deprecated `icon` property from `RelyingPartyIdentity` (no longer supported in 2.x)
- Simplified attestation and assertion verification using new 2.x APIs
- Updated `CredentialRegistration` to use `RegisteredCredential.builder()` pattern
- Removed deprecated `SimpleTrustResolverWithEquality` class (trust anchors handled differently in 2.x)
- Fixed README typo: "also know" → "also known"
- Updated README developer video links to working YouTube playlist
- Removed outdated trust store references from README (aligns with 2.x architecture changes)

### Fixed
- Fixed compilation errors from deprecated 1.x APIs
- Fixed test configuration for Spring Boot 2.7.x
- Fixed Dockerfile configurations to use correct Java version
