# Search-query embedding reuse

Bounded in-memory EG2 query-vector reuse on the real SemanticLayer.search path. Live index/source/ranking checks rerun on every search. Model space, task, profile, revision, project scope and index generation identify keys. Mutable vectors are copied; media descriptors are not cached.

26/26 integration cases PASS on 9fa0a00, including existing F1/F2 and gate-ordering regressions; initial 2/2 cache unit failures were an invalid synthetic hash, corrected in e473f20 and 2/2 PASS. Original logs/XML preserved, timed offline JDK17 runtime/JDK21 worker. No full suite or physical accuracy/latency improvement claimed. Controlled repeated query native calls 2 to 1, unit ablation 50 to 1, maximum vector payload 24KiB. No milestone closed.
