# Local RAG and citations

Ingestion is:

```text
ContentResolver → app-private copy + SHA-256 → parser → page-aware chunks
→ Room document segments + FTS4 → local hashed vectors → RRF fusion
```

Each segment retains document ID, page range, character offsets, normalized text, section path and content hash. Retrieval assigns a stable `LCL-XXXXXXXX` ID for the current project/segment. The prompt encloses each passage in an `<EVIDENCE>` block with an untrusted trust label.

`CitationValidator` accepts only IDs included in the current turn and resolves the authoritative document/page/excerpt from storage. Unknown IDs are reported and ignored. The chat UI exposes resolved evidence chips and a local excerpt dialog.

The embedding implementation is a deterministic hashed bag-of-terms baseline. It provides a real offline vector signal and a replaceable interface, but it is not a semantic neural model; the product must not describe it as one.
