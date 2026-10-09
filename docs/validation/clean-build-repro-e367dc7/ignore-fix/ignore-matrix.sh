#!/usr/bin/env bash
set -euo pipefail
repo_root="${1:-$(git rev-parse --show-toplevel)}"
cd "$repo_root"
expect_trackable() {
  local path="$1"
  if git check-ignore -q --no-index -- "$path"; then
    printf 'FAIL TRACKABLE %s (matched %s)\n' "$path" "$(git check-ignore -v --no-index -- "$path")"
    return 1
  fi
  printf 'PASS TRACKABLE %s\n' "$path"
}
expect_ignored() {
  local path="$1"
  if git check-ignore -q --no-index -- "$path"; then
    printf 'PASS IGNORED %s (%s)\n' "$path" "$(git check-ignore -v --no-index -- "$path")"
  else
    printf 'FAIL IGNORED %s (no ignore rule)\n' "$path"
    return 1
  fi
}
expect_trackable third_party/llama.cpp/src/models/models.h
expect_trackable third_party/llama.cpp/src/models/afmoe.cpp
for ext in bin gguf litertlm onnx tflite safetensors pt pth ckpt ggml so a; do
  expect_ignored "third_party/llama.cpp/src/models/probe.$ext"
done
count="$(git ls-files -- third_party/llama.cpp/src/models | wc -l | tr -d ' ')"
test "$count" = 158
printf 'PASS TRACKED_MODEL_SOURCE_COUNT %s\n' "$count"
if git ls-files -- third_party/llama.cpp/src/models | grep -Ev '\.(cpp|h)$'; then
  printf 'FAIL unexpected tracked extension in model registry\n'
  exit 1
fi
printf 'PASS TRACKED_MODEL_SOURCE_EXTENSIONS cpp=157 h=1\n'
test -z "$(git status --porcelain)"
printf 'PASS CHECKOUT_UNMODIFIED\n'
