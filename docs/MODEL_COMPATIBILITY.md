# Model compatibility

The first backend is llama.cpp with GGUF input. Import reads the GGUF magic/header and structured metadata before the model is stored in the library. The UI records format, architecture, quantization, parameter label, declared context and a memory-risk warning.

Compatibility is intentionally conservative:

- `COMPATIBLE` means the architecture is known to the adapter and the file is structurally readable.
- `COMPATIBLE_WITH_WARNING` means the format is readable but the architecture needs native-runtime validation.
- `INSUFFICIENT_MEMORY_RISK` is a warning state based on file size versus the device memory class; it is not a guarantee of success.
- corrupt/incomplete and explicitly unsupported states are never offered as successful local inference.

The checked-in native build targets `arm64-v8a` and `x86_64`, with one portable CPU variant per ABI. Device-specific native load/generate metrics still require a real Android device/emulator and a user-supplied GGUF fixture.
