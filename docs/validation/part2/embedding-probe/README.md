Host-only probe, no Android inference claim. Uses the production LocalEmbedding core with three public test sentences. Requires CMake, a C++17 compiler and the separately imported pinned GGUF from the main report.

```bash
cmake -S docs/validation/part2/embedding-probe -B /tmp/local-embedding-probe -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/local-embedding-probe --target probe -j2
/tmp/local-embedding-probe/probe /absolute/path/embeddinggemma-300M-Q8_0.gguf
```

Recorded output: ../embedding-real-linux.json. Exit0 requires 768 dimensions, norm≈1 and relevant similarity exceeding unrelated similarity. This is a minimal neural sanity check, not a broad accuracy evaluation.
