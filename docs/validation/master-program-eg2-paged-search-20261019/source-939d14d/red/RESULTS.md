# Expected-red page-boundary regression

At test-only source `939d14d`, `SemanticContextPostfixTest.searchReadsSelectedSemanticCorpusInBoundedPages` ran once and failed 1/1 as intended: the old implementation produced one full-corpus DAO read where the new regression required two bounded page reads for 257 rows. This red run establishes the missing paging behavior; it is not a product failure after the implementation. See the exact command, environment, Gradle log and XML in this directory.
