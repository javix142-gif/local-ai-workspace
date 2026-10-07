# LiteRT-LM Kotlin history-role compatibility

This module preserves the official LiteRT-LM **0.17.1 native AAR libraries** and
compiles the 16 public/internal Kotlin client source files from upstream tag
`v0.17.1`, commit `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`. The original Apache-2.0
license and copyright headers are retained; UPSTREAM.json records source hashes.
This is an application compatibility client, not a new native backend version.

The only upstream source change is in Message.kt: the additive Role.ASSISTANT
and Message.assistant factories serialize the native role `assistant`. Existing
Role.MODEL / Message.model remain unchanged. The official Kotlin factory
otherwise serializes `model`, while GenericDataProcessor and Qwen3DataProcessor
produce `assistant` and Qwen3.5's actual embedded ChatML template expects it.
This matters for restored/injected history; live native conversation history is
already written by the native processor. No model file or template is patched.

All other upstream APIs, callbacks, cancellation, handles and capabilities are
preserved. The app chooses the restored model-turn role from bounded actual
bundle metadata rather than the filename. Native libraries are extracted from
the pinned Google Maven artifact at build time; its classes.jar is not packaged,
so there are no duplicate SDK classes. No code from another application is used.
