# Security and privacy

The current manifest does not request `INTERNET`. Inference, document parsing, retrieval, hashed embeddings and memory are local. The app never substitutes a cloud API when a local model is missing.

Trust boundaries are represented in `domain.model.SourceTrust` and `security.SecurityPolicy`. External documents are `UNTRUSTED_DOCUMENT`; they are evidence only and cannot enable a tool, grant permissions, write privileged memory or authorize a side effect. `ToolPolicy` denies requests originating from untrusted external data and requires a separate deterministic confirmation decision for state-changing tools.

The current regression suite covers:

- injected/untrusted content attempting to invoke an enabled tool;
- fabricated citation IDs;
- path traversal and absolute paths;
- `file:` and `intent:` URL schemes;
- write-tool confirmation gating.

Remaining security work before release includes Android Keystore-backed connector secrets, a full structured tool-argument schema validator, export redaction tests, and instrumented prompt-injection tests through a real model.
