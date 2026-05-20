# Changelog

All notable changes to this project will be documented in this file.

## [0.0.1.0] - 2026-05-19

### Changed
You can now use this workshop with the latest java-webauthn-server 2.9.0 library. This upgrade brings compatibility with modern WebAuthn implementations and ensures the workshop aligns with current Yubico standards.

- Upgraded java-webauthn-server from 1.2.0 to 2.9.0 across all workshop modules
- Updated Spring Boot from 2.1.4.RELEASE to 2.7.18 (latest Java 8 compatible version)
- Updated supporting dependencies: Lombok 1.18.46, logback-classic 1.2.13, azure-webapp-maven-plugin 2.13.0
- Migrated deprecated APIs to java-webauthn-server 2.x standards:
  - Removed `.icon()` usage (field removed in WebAuthn Level 2)
  - Changed `.requireResidentKey(boolean)` to `.residentKey(ResidentKeyRequirement)`
  - Removed `.allowUnrequestedExtensions()` (now always enabled)
  - Removed manual attestation framework initialization (now handled internally by RelyingParty)

### Added
- BouncyCastle 1.70 dependency for EdDSA cryptographic support on Java 8
- Comprehensive migration documentation in MIGRATION_CHANGES.md - review this if you're upgrading from v1.x
- Code comments explaining v2.x API changes for educational purposes

### Fixed
- Completed v2.x API migration for workshop modules 2 (Credential Repository) and 4 (Authentication)
- Preserved all Lombok annotations critical for workshop build process
