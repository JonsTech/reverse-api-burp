# Contributing to Reverse API

Bug reports and focused pull requests are welcome. Please use only traffic and fixtures you are authorized to share.

## Development

Requirements:

- JDK 17
- The included Gradle Wrapper

Run the complete build before opening a pull request:

```shell
./gradlew clean build
```

On Windows, use `./gradlew.bat clean build` from PowerShell. The build runs the tests and verifies that the loadable fat JAR contains its runtime dependencies without bundling Burp's Montoya API.

Keep capture processing off Burp's callback and Swing event threads, preserve the existing resource bounds, and treat every request and response as untrusted input. New detection, normalization, schema or export behavior should include regression tests.

## Sensitive data

Never commit real credentials, cookies, API keys, customer traffic, target names, Burp project files or generated specifications containing engagement data. Use obviously synthetic values in tests and screenshots.

## Pull requests

- Keep changes narrow and explain their user-facing effect.
- Update `CHANGELOG.md` for notable changes.
- Update the README when controls, output or limitations change.
- Confirm that `./gradlew clean build` succeeds.

By contributing, you agree that your contribution is licensed under the Apache License 2.0.
