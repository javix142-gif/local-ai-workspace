# Performance

The runtime records model-load duration, prompt/output token estimates, TTFT, prefill/decode rates, total generation duration, selected context size, backend and error category. The metrics are kept with generated messages when available.

`ContextBudgetManager` reserves output space, preserves policy and the current user message when possible, ranks history/memory/evidence by priority and excludes whole evidence blocks rather than silently truncating citation-bearing text.

The current baseline deliberately builds a single portable CPU variant per ABI. Native device benchmarks, thermal/battery observations, KV/prefix-cache measurements and model-size matrix results are not marked as passing until they run on target hardware.
