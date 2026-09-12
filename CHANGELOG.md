# Changelog

All notable changes to Reverse API are documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

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

[1.0.0]: https://github.com/JonsTech/reverse-api-burp/releases/tag/v1.0.0
