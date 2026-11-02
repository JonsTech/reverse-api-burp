# Changelog

All notable changes to Reverse API are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

## [1.0.1] - 2026-11-02

### Fixed

- Import Proxy history now includes requests made after the extension loaded, allowing recovery of traffic missed while capture was paused or scope filtering was enabled.
- Clarified import status and scope-filter guidance.
- Capture now defaults to all Proxy hosts, with in-scope-only filtering available as an explicit option.
- Obfuscated export no longer mistakes absent bearer-token placeholders, cookie attributes or credential policy/state fields for secrets. Actual credential values and credential arrays remain checked.
- Multipart request bodies use an OpenAPI object schema without an invalid raw MIME example. Raw captures remain available in the unobfuscated capture extension. This fixes Bruno's numeric multipart field-name import error.
- Latest request/response samples continue updating after 25 observations, while preserving the first 24 samples and enforcing the retained-data budget.
- Clear and unload complete cancelled history-import futures instead of leaving the import button stuck.
- Generated output and errors open their preview tab, and status text wraps and can be copied.
- Date and timestamp path segments, including percent-encoded values, normalize into distinct date parameters.

## [1.0.0] - 2026-09-12

### Added

- Passive API discovery from new Burp Proxy responses.
- Explicit import of Proxy history captured before the extension loaded.
- Confidence-based detection for JSON, GraphQL, XML/SOAP and API-style paths.
- Endpoint review, inclusion controls and editable normalized paths.
- Per-server generation to keep development, staging and production observations separate.
- OpenAPI 3.1 YAML and JSON generation with observed schema inference.
- Postman Collection 2.1 generation with paired response examples.
- Obfuscated generation by default, with an explicit sensitive replay mode.
- Burp-native read-only request and response viewers.
- Bounded capture storage, background processing and clean extension shutdown.

[1.0.1]: https://github.com/JonsTech/reverse-api-burp/releases/tag/v1.0.1
[1.0.0]: https://github.com/JonsTech/reverse-api-burp/releases/tag/v1.0.0
