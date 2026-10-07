#pragma once
#include "llama.h"
#include "ggml-backend.h"
#include "ggml-cpu.h"
#include "gguf.h"
#include <vector>
#include <string>
#include <stdexcept>
#include <cmath>
#include <memory>

struct EmbeddingFailure : std::runtime_error {
 EmbeddingFailure(const char *stage,const char *code,const std::string &safe):std::runtime_error(std::string(stage)+"|"+code+"|"+safe){}
};
// Directly linked CPU registration works with Android's APK-backed .so loader.
inline void local_embedding_cpu() {
 if(!ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU))ggml_backend_register(ggml_backend_cpu_reg());
 if(!ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU))throw EmbeddingFailure("NATIVE_LIBRARY_LOAD","CPU_BACKEND_UNAVAILABLE","CPU backend registration failed");
}
struct LocalEmbedding {
 llama_model *model=nullptr;
 explicit LocalEmbedding(const char *path) {
  gguf_init_params header_params={true,nullptr};
  std::unique_ptr<gguf_context,decltype(&gguf_free)>header(gguf_init_from_file(path,header_params),gguf_free);
  if(!header)throw EmbeddingFailure("GGUF_OPEN","GGUF_OPEN_FAILED","Native GGUF header could not be opened");
  auto key=gguf_find_key(header.get(),"general.architecture");
  if(key<0 || gguf_get_kv_type(header.get(),key)!=GGUF_TYPE_STRING || std::string(gguf_get_val_str(header.get(),key))!="gemma-embedding")
   throw EmbeddingFailure("ARCHITECTURE_VALIDATE","WRONG_ARCHITECTURE","Expected GGUF gemma-embedding architecture");
  auto params=llama_model_default_params();params.n_gpu_layers=0;params.load_mode=LLAMA_LOAD_MODE_MMAP;
  model=llama_model_load_from_file(path,params);
  if(!model)throw EmbeddingFailure("MODEL_LOAD","MODEL_LOAD_NULL","GGUF architecture=gemma-embedding; native model loader returned null");
  if(llama_model_n_embd_out(model)!=768) { llama_model_free(model);model=nullptr;throw EmbeddingFailure("DIMENSION_VALIDATE","WRONG_DIMENSION","Native model output is not 768 dimensions"); }
 }
 ~LocalEmbedding(){if(model)llama_model_free(model);}
 int dimension()const{return llama_model_n_embd_out(model);}
 std::vector<float> embed(const std::string &text) {
  const auto *vocab=llama_model_get_vocab(model);
  int n=llama_tokenize(vocab,text.data(),text.size(),nullptr,0,true,true);if(n<0)n=-n;
  if(n<=0||n>512)throw EmbeddingFailure("EMBEDDING_PROBE","TOKEN_LIMIT","Embedding input exceeds the 512 token limit");
  std::vector<llama_token>tokens(n);n=llama_tokenize(vocab,text.data(),text.size(),tokens.data(),tokens.size(),true,true);
  if(n<=0)throw EmbeddingFailure("EMBEDDING_PROBE","TOKENIZATION_FAILED","Embedding tokenization failed");
  auto p=llama_context_default_params();p.n_ctx=512;p.n_batch=512;p.n_ubatch=512;p.embeddings=true;p.n_threads=4;p.n_threads_batch=4;p.pooling_type=LLAMA_POOLING_TYPE_UNSPECIFIED;
  std::unique_ptr<llama_context,decltype(&llama_free)>ctx(llama_init_from_model(model,p),llama_free);
  if(!ctx)throw EmbeddingFailure("CONTEXT_CREATE","CONTEXT_CREATE_FAILED","Embedding context creation failed");
  auto batch=llama_batch_get_one(tokens.data(),n);
  int status=llama_model_has_encoder(model)?llama_encode(ctx.get(),batch):llama_decode(ctx.get(),batch);
  const float *v=status==0?llama_get_embeddings_seq(ctx.get(),0):nullptr;
  if(!v)throw EmbeddingFailure("EMBEDDING_PROBE",status==0?"EMBEDDING_MISSING":"INFERENCE_FAILED","Native embedding inference failed; returnCode="+std::to_string(status));
  std::vector<float>out(v,v+dimension());double norm=0;
  for(float x:out){if(!std::isfinite(x))throw EmbeddingFailure("EMBEDDING_PROBE","NONFINITE_VECTOR","Native embedding contains NaN or infinity");norm+=double(x)*x;}
  if(norm<=0)throw EmbeddingFailure("EMBEDDING_PROBE","ZERO_VECTOR","Native embedding is zero");
  norm=std::sqrt(norm);for(float &x:out)x/=norm;return out;
 }
};
