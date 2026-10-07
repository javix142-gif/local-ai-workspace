# Metadata-only fixture

qwen35-header-metadata.bin contains bytes [0,17113) of the Apache-2.0 published
Qwen3.5-2B_int8.litertlm at litert-community/Qwen3.5-2B revision
4327b7425533c26da2aec1d7b915e9742eead2cb. Original model SHA-256:
8ae5027136e0b89039ed76eedfb61f0cf4b7277024d99bf765359264295b86b1.
Only header/padding and the 729-byte LlmMetadata are present. No weights or
tokenizer are present. This is not an importable/executable model and is used
solely to verify bounded metadata parsing and the actual assistant template role.
