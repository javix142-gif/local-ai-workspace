# LiteRT-LM / EmbeddingGemma 2 discovery

Baseline source archive: `/workspace/baselines/local-ai-workspace-0.2.4-source.tar.gz`.
SHA256: `f145f5649d5eccd31e1b6b18ff18e38495dcd03fd1161337cdf8447ff182fa69`.
No `.git` exists in the supplied workspace. No commit/tag can truthfully be cited.

Dependency remains exactly `com.google.ai.edge.litertlm:litertlm-android:0.17.1`.
Vendored official Kotlin source: v0.17.1, commit
`5e58e9a0aef7abf7091207a8b1d1063a1c800f08` (UPSTREAM.json).

Public Kotlin APIs actually present:

- `EmbeddingEngine(EmbeddingEngineConfig(...))`, initialize, computeEmbedding,
  computeEmbeddingBatch, close, isInitialized.
- `EmbeddingOptions(normalize, insertSpecialTokens, outputSize, visionTokensPerImage)`.
- `InputData.Text`, `InputData.Image(bytes)`, `InputData.Audio(bytes)`.
- `Capabilities.inputModalities()`.
- Backend.CPU/GPU with explicit visionBackend/audioBackend.

Native Android arm64 exports were checked with `nm -D`; recorded in
android-embedding-symbols.txt. No invented JNI bindings or vendor modifications.

Limitations of Kotlin API 0.17.1:

- No public signature enumeration, family descriptor, embedding length discovery,
  encoder-loaded telemetry, embedding benchmark/timing fields or native video input.
- Signature selection is performed inside native EmbeddingEngine initialization.
  Successful 768/256 probes demonstrate usable lengths; they do not enumerate every
  supported length. Only 768/256 are enabled by this app integration.
- Capabilities for the exact generic bundle report text=true, vision=true,
  audio=true, video=false. This is separate from MODEL-level video support.
- Null vision/audio backends are supplied in text-only configuration; no claim
  about internal encoder residency is made without native observability.
- EmbeddingResponse contains only FloatArray. All recorded durations are measured
  wall-clock app calls, not native prefill/throughput metrics.
- Native computeEmbedding is synchronous and has no public cancel method. Cancellation
  is cooperative before/after the native call; a hard native timeout is not claimed.

Generic artifact: HF revision `24d962e906c7d332c6428e71c9676855024569e2`.
Local byte size: 484622336. Locally calculated SHA256:
`e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`.
Matches official LFS metadata. No SoC-specific bundle was used.

Linux CPU real-native probes: model opens; 768D and 256D finite/unit vectors;
image bytes and PCM WAV also produce actual 768D finite/unit vectors. See JSON files.
These are HOST observations. NOT YET DEVICE VALIDATED on Moto G86.
No runtime update is necessary for the verified host paths.

Model cards copied from official HF repositories; no third-party blog is the source
of truth. External performance tables are not app performance measurements.

Storage uses an additive sidecar Room database, leaving the version-8 workspace and EG1 vector table intact. EG2 modelVersion identifies the pinned Hugging Face revision; runtime version is stored separately.

GPU initialization may be requested experimentally, but public SDK 0.17.1 does not expose execution-backend telemetry. Successful initialization is reported as GPU_REQUEST_ACCEPTED_EXECUTION_UNVERIFIED with effective backend null; explicit CPU fallback is visible. No claim of Mali execution is made.
