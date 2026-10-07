---
license: apache-2.0
base_model: Qwen/Qwen3.5-2B
tags:
  - litert
  - litert-lm
  - litertlm
  - on-device
  - edge
  - hybrid
  - gated-deltanet
  - qwen3_5
  - vlm
  - image-text-to-text
pipeline_tag: text-generation
library_name: litert-lm
base_model_relation: quantized
---

LiteRT is Google's on-device runtime, the new name for TensorFlow Lite (Android: `com.google.ai.edge.litert:litert`), and `litert-torch`, the renamed `ai-edge-torch`, is its PyTorch converter: a PyTorch model converted unmodified with `litert_torch.convert` matched the original to 4e-7 on a Galaxy S26 ([measured](https://github.com/john-rocky/apple-silicon-llm-bench/blob/main/results/android/tinyhybridnet-three-runtimes.md), LiteRT 2.2.0, Android 16, 2026-09-05).

Measured on device (edge-compat): Galaxy S26 · LiteRT-LM 0.16.0 · GPU · decode 17.5 tok/s · prefill 450 tok/s · TTFT 520 ms · all 25703 ops delegated (2026-09-05); Galaxy S26 · LiteRT-LM 0.16.0 · CPU · decode 19.0 tok/s · prefill 181 tok/s · TTFT 1.20 s (2026-09-05); Raspberry Pi 5 · LiteRT-LM 0.16.1 · CPU, 4 threads · decode 4.2 tok/s · prefill 56 tok/s · TTFT 5.04 s (2026-09-01). Record: https://github.com/john-rocky/edge-compat/blob/main/cards/qwen35-2b-vl-int8/CARD.md

# Qwen3.5-2B — LiteRT-LM

[Qwen/Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) converted to the **LiteRT-LM** (`.litertlm`) format for on-device inference with Google's [LiteRT-LM](https://github.com/google-ai-edge/litert-lm) runtime. **Requires litert-lm ≥ 0.15** (both backends gated on 0.15.0 and 0.16.0). Same conversion rail as our [Qwen3.5-0.8B](https://huggingface.co/litert-community/Qwen3.5-0.8B) and [Qwen3.5-4B](https://huggingface.co/litert-community/Qwen3.5-4B) — the GPU-delegable rank-≤4 gated-delta kernel.

Qwen3.5 is Alibaba's hybrid architecture: GatedDeltaNet (gated delta rule linear attention) blocks interleaved with a few gated full-attention blocks (this 2B model has 18 linear-attention + 6 attention layers). The linear-attention blocks carry constant-size per-layer conv + recurrent state instead of a growing KV cache, so memory stays nearly flat with context length — only the 6 attention layers keep KV (4096-token budget here). The upstream checkpoint is multimodal, and this repo now carries **both halves**: the original text-only build (vision tower and MTP heads dropped exactly as upstream's own `Qwen3_5ForCausalLM` text-only load contract does), and a **vision build** that adds the checkpoint's own 24-layer ViT. See *Vision build* below.

| File | Recipe | Size |
|---|---|---|
| `Qwen3.5-2B_int8.litertlm` | text only — int8 dynamic on linears + embedding (convs and the delta rule stay float), fp32 activations declared | 1.97 GB |
| `Qwen3.5-2B-VL_int8.litertlm` | **text + image** — same decoder recipe, plus the checkpoint's own ViT (fp16 encoder, int8 adapter), static 512×512, six-signature prefill ladder | 3.15 GB |

2026-09-21: chat template updated to accept the 0.18 content-parts form (string form unchanged); weights, tokenizer and executor metadata byte-identical.

## Correctness

All gates below were run on this exact file (or its float parent, same graph rail):

- **Logits-level parity vs PyTorch**: the float export matches the HF model teacher-forced across 48 positions — top-1 and top-5 agreement 100%, Pearson 1.0000, KL ≈ 0.
- **8-question sanity gate: 8/8 on CPU and GPU, on both litert-lm 0.15.0 and 0.16.0** (Mac).
- **iPhone 17 Pro (Metal GPU)**: on the composite 8-question probe (all 8 questions in one 138-token prompt), the GPU output matches the HF fp32 reference **word-for-word through answer 7** (including reproducing the model's own arithmetic slip on question 1 — faithful conversion includes the model's mistakes). At that point fp32 ends its turn without attempting question 8; the int8 path diverges by that one end-of-turn token and adds the correct 8th answer before stopping.
- **Prompt-length robustness**: first-token check against the runtime's real prefill chunk plans, fresh engine per length — 40/40 lengths clean on CPU, 20/20 on GPU.
- Multi-turn conversations carry state correctly across turns.

## Usage

```bash
litert-lm run ./Qwen3.5-2B_int8.litertlm --prompt "What is the capital of France? Answer in one word."

# GPU
litert-lm run ./Qwen3.5-2B_int8.litertlm --backend gpu --cache no --prompt "..."
```

**Prefill signatures: the full 1–1024 ladder** (1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1). Every exported signature is charged engine memory even if never called; unlike the 4B (which needs a reduced ladder to stay under a 12 GB iPhone's jetsam ceiling), the 2B's full ladder peaks at ~5.3 GB on iPhone 17 Pro GPU.

**Chat template note:** the bundle ships a simplified ChatML template rather than the stock Qwen3.5 template. Thinking is disabled the way the stock template's non-thinking mode does it (an empty `<think>\n\n</think>` block opens each assistant turn), and — deliberately — that block is kept in *history* renders too: the stock template strips it from past turns, which breaks LiteRT-LM's incremental conversation rendering (the engine requires each turn's render to be a string-extension of the previous one) and kills multi-turn on turn 2. Tool-calling and vision sections are not included.

## Performance

`litert-lm benchmark` (litert-lm 0.16.0), Apple M4 Max, `-p 256 -d 256 --runs 3 --cache no`, quiet machine:

| Backend | Prefill (256) | Decode | TTFT |
|---|---|---|---|
| GPU | 1486 tok/s | 114.3 tok/s | 0.18 s |
| CPU | 592 tok/s | 37.6 tok/s | 0.46 s |

On device (cold start, unplugged, thermal-nominal start, single runs, 138-token prompt, quality-gate harness):

| Device | Backend | Prefill | Decode | TTFT | Peak memory |
|---|---|---|---|---|---|
| iPhone 17 Pro | **GPU (Metal)** | 237.7 tok/s | **24.3 tok/s** | **0.73 s** | 5.33 GB |
| iPhone 17 Pro | CPU | 206.5 tok/s | 16.2 tok/s | 0.77 s | 1.52 GB |

(iPhone GPU: model load ~60 s on first launch while Metal programs compile.)

Honest notes:
- **Where GPU execution is verified.** macOS (Metal), iPhone 17 Pro (Metal) and Pixel 8a (Arm Mali, OpenCL). **We have not verified GPU execution on Qualcomm Adreno devices** — if you are on a Snapdragon phone, use the CPU backend unless you have confirmed the GPU path on your own device.
- **GPU inference runs with fp32 activations** (declared in the bundle; the fp16-activation formulation is unfinished for this family). That is where the GPU memory multiple over CPU comes from.
- **Pixel 8a (8 GB-class Android): GPU does not fit.** The OpenCL delegate accepts the whole graph (zero rejections, full delegation on every compiled signature), but engine creation ran the phone out of memory before finishing — the GPU path's fp32-expanded weight buffers are several times the 1.97 GB file. A CPU-backend measurement was also blocked on our test phone, by storage rather than RAM: the runtime's XNNPACK weight cache needs roughly another file-size of free disk, which a nearly-full phone doesn't have. Treat this release as Apple-hardware-first; on Android, prefer 12 GB+ devices for GPU.
- On low-end Android GPUs, decode is memory-bandwidth-bound and does not beat the CPU anyway; the GPU win is on Apple hardware (and, generally, prefill/TTFT).
- On quality: individual questions gate 8/8 on every backend and both runtime versions. On the harder composite probe (8 questions in one prompt), **GPU reproduces the fp32 reference; the CPU int8 path degrades** (answers 3 of the 8, deterministically, identical on Mac and iPhone). For complex multi-part prompts, prefer the GPU backend or ask for a float variant.

## Vision build (`Qwen3.5-2B-VL_int8.litertlm`)

The upstream checkpoint ships a 24-layer, 1024-dim ViT that the text-only package drops. This build keeps it, wired to LiteRT-LM's `fast_vlm` contract: one 512×512 image per turn, 1024 patches merged 2×2 into 256 soft tokens injected at the image position. There is no DeepStack in this tower (`deepstack_visual_indexes` is empty upstream), so the single image embedding is the whole visual signal — nothing is folded away.

```bash
litert-lm run ./Qwen3.5-2B-VL_int8.litertlm \
  --prompt "What is in this image?" --attachment ./photo.png

# vision on the GPU as well as the decoder
litert-lm run ./Qwen3.5-2B-VL_int8.litertlm --backend gpu --vision-backend gpu \
  --prompt "Where is this scene?" --attachment ./photo.png --cache no
```

Text-only prompts work on this file too; it is a superset of the text build, at a larger download and a larger GPU footprint.

### Measured

Mac, `litert-lm benchmark` (0.16.0), M4 Max, `-p 256 -d 256 --runs 3 --cache no`, quiet machine — text path:

| Backend | Prefill (256) | Decode | TTFT |
|---|---|---|---|
| GPU | 1594 tok/s | 109.1 tok/s | 0.18 s |
| CPU | 598 tok/s | 40.1 tok/s | 0.47 s |

iPhone 17 Pro, cold start, single runs, quality-gate harness — the vision rows are real image turns:

| Backend | Task | Decode | TTFT | Peak memory | Load |
|---|---|---|---|---|---|
| **GPU (Metal)** | image + question | **33.9 tok/s** | 4.9 s | 4.64 GB | 30.7 s |
| GPU (Metal) | image + question | 32.3 tok/s | 5.5 s | 4.65 GB | 31.8 s |
| GPU (Metal) | text (8-question probe) | 23.2 tok/s | 6.0 s | 4.45 GB | 34.7 s |
| CPU | text (8-question probe) | 14.1 tok/s | 5.1 s | 1.20 GB | 13.0 s |

Grounding on device was checked with two probes whose answers are opposite: an image containing text ("does this image contain text?" → "Yes, the image…") and one without ("No, the image…"). Both correct on Metal, with the vision encoder on the GPU.

### Honest notes

- **Six prefill signatures, not eleven.** Every exported signature is charged engine memory whether or not it is called. The text build's full 1–1024 ladder peaks at ~5.3 GB on iPhone GPU and fits; adding the vision tower pushes the same ladder past a 12 GB phone's jetsam ceiling — all three Metal legs were killed after initialising 11 of 12 signatures. This build ships `1024, 256, 64, 16, 4, 1`. Prompts longer than a signature are chunked as usual, so the only cost is a slightly coarser chunk plan. Cutting the ladder also **tripled CPU decode** (3.8 → 14.1 tok/s) and took first-token latency from 23.7 s to 5.1 s, because the runtime's XNNPACK repack is charged per signature too.
- **Positions are 1-D.** The `fast_vlm` contract feeds sequential positions, so the checkpoint's M-RoPE collapses to plain RoPE. Against the full M-RoPE reference on nine image/prompt pairs, one generation is identical and the other eight are fluent same-content paraphrases that diverge deep in the answer. Expect that class of difference — counting and dense-layout questions are where it shows.
- **The vision graph is faithful; the int8 damage is in the encoder.** The exported tower matches the model's own vision path at correlation 0.9999999992 in fp32. Quantizing the encoder to int8 drops that to 0.97–0.98 on real photographs, so this build ships the encoder in **fp16** and only the adapter in int8. An int8-encoder variant is 300 MB smaller and still answers correctly, but it is not what ships here.
- **Android is not gated for this build.** The text build's notes apply (Pixel-class 8 GB GPU does not fit), and on Mali the fp16 vision encoder is known to crash the device on other models of this shape — an int8-vision build would be the Android path, and we have not measured one on a phone.
- **Quality on the composite 8-question probe, separated by cause.** On Mac this file answers 7 of 8, missing only `17 + 25` — and it misses it identically on GPU and CPU, so that one is the model, not the conversion (the text build's card records the same slip). On iPhone Metal two further answers degrade that do **not** degrade on Mac. Individual questions asked one at a time gate 8/8 on both Mac backends.

## Conversion notes

Converted with [`litert-torch`](https://github.com/google-ai-edge/litert-torch) plus a hybrid-cache patch (reproduction script + patch: [hf-to-litertlm `qwen35_work/`](https://github.com/john-rocky/hf-to-litertlm)):

- **Rank-4 chunk kernel** (shared with the 0.8B/4B): the reference chunked delta rule spells its contractions as broadcast-multiply-reduce over high-rank intermediates. The vendored kernel re-expresses them as batched matmuls with chunk and head axes folded into the batch axis (all tensors rank ≤ 4, no `BROADCAST_TO`, no int64 index math), and writes every tail-pad as a concat with a zeros constant rather than a `PAD` op (the GPU delegate miscomputes rank-3 non-final-axis `PAD` — [LiteRT#9272](https://github.com/google-ai-edge/LiteRT/issues/9272)).
- **Export cache for GatedDeltaNet layers**: conv `[B, conv_dim, K]` + recurrent `[B, heads, k_dim, v_dim]` cache layers registered for `layer_types == "linear_attention"`, so `torch.export` traces the model's own state contract.
- **Vision tower (VL build only)**: the upstream tower is dynamic-resolution, and its `grid_thw` preprocessing aborts `torch.export` outright. It is re-authored for one static 512×512 image — Conv3d patch-embed folded to Conv2d over the duplicated temporal frame, learned position embeddings bilinearly resampled to the static grid ahead of time, 2-D rope precomputed, explicit full attention instead of the variable-length split. Patches stay in raster order through the encoder and the 2×2 merge happens in the adapter as four strided slices and a concat, so no `GATHER_ND` reaches the mobile GPU delegate. Two numerical guards matter: every activation keeps a leading batch dim (Metal computes rank-2 elementwise against a rank-2 constant wrong, silently), and LayerNorm is pre-scaled by a calibrated power of two because from block 11 on the residual stream carries values large enough to overflow fp16 accumulation.
- **Pad guard on the externalised-embedder graph**: `fast_vlm` moves the embedder into its own tflite, which removes token ids from the decoder graph — so the gated-delta prefill-pad guard, which keyed off `input_ids != 0`, silently stopped arming. It now derives the valid mask from position monotonicity instead, the same fallback upstream's own exportable module uses. The 40/40 prompt-length sweep is what proves it armed.
- Unlike the 4B, the 2B's linear-attention heads are ungrouped (16 key / 16 value), so the 4B's head-interleave rewrite never traces, and the full prefill signature ladder fits on-phone.

## Raspberry Pi 5 (CPU)

Measured on a Raspberry Pi 5 Model B Rev 1.1 (8 GB, Raspberry Pi OS 64-bit) with [`litert-lm benchmark`](https://github.com/google-ai-edge/LiteRT-LM) 0.16.1: CPU backend, 4 threads, 256 prefill + 256 decode tokens, `--cache memory` (the compile cache lives and dies with the process, so every invocation compiles the model from scratch; nothing is reused between runs), one warm-up plus one timed iteration per invocation, 3 invocations per file with cooldown in between. Values are the median across invocations (min–max in parentheses). No thermal throttling occurred during these runs (`vcgencmd get_throttled` stayed `0x0`). Every file listed produced coherent text in a real generation on this backend before its numbers were recorded.

| File | Prefill (tok/s) | Decode (tok/s) | TTFT | Peak RSS |
|---|---:|---:|---:|---:|
| `Qwen3.5-2B-VL_int8.litertlm` | 55.9 (55.4–56.3) | 4.2 (4.2–4.3) | 5.0 s | 3.2 GB |
| `Qwen3.5-2B_int8.litertlm` | 55.1 (55.0–55.3) | 4.2 (4.2–4.3) | 4.9 s | 3.8 GB |
