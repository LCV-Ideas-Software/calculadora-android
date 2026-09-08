# Changelog

All material changes to `calculadora-android` are recorded here.

## [Unreleased]

### Added

- Established the public repository baseline without inventing an Android or
  Gradle project.
- Added organization-standard CodeQL, Code Quality, Dependency Review, Zizmor,
  OpenSSF Scorecard, Pages, Dependabot and Linear Release automation.
- Added a deliberately inert JavaScript probe for GitHub Code Quality until
  real application source exists.
- Added independent contribution, conduct, security, licensing, notice,
  third-party, ownership and sponsorship records.

### Security

- Pinned every external GitHub Action to an immutable full commit SHA.

### Fixed

- Removed the obsolete Actions dependency lock and its workflow onboarding
  markers to restore workflow startup after Dependabot updates. Direct SHA
  pins, workflow behavior and repository security settings are unchanged.
