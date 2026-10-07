#include <jni.h>
#include "local_embedding.h"
#include <mutex>
#include <atomic>
static std::mutex gate;
static std::atomic<int> load_error{0};
// Capture only recognized native causes. Never retain arbitrary native text, paths or tokens.
static void native_log(ggml_log_level level,const char *text,void*) {
 if(level!=GGML_LOG_LEVEL_ERROR)return;
 const std::string s(text);
 if(s.find("no CPU backend found")!=std::string::npos)load_error=1;
 else if(s.find("no backends are loaded")!=std::string::npos)load_error=5;
 else if(s.find("failed to allocate")!=std::string::npos || s.find("cannot allocate")!=std::string::npos)load_error=2;
 else if(s.find("unsupported")!=std::string::npos)load_error=3;
 else if(s.find("failed to open")!=std::string::npos)load_error=4;
}
static void fail(JNIEnv *env,const char *what){env->ThrowNew(env->FindClass("com/arm/aichat/EmbeddingNativeException"),what);}
extern "C" JNIEXPORT jlong JNICALL Java_com_arm_aichat_EmbeddingGemma_nativeLoad(JNIEnv *e,jobject,jstring path,jstring){
 std::lock_guard<std::mutex>lock(gate);const char *p=e->GetStringUTFChars(path,nullptr);if(!p)return 0;jlong result=0;
 llama_log_set(native_log,nullptr);load_error=0;
 try{local_embedding_cpu();llama_backend_init();result=reinterpret_cast<jlong>(new LocalEmbedding(p));}
 catch(const std::exception &x){std::string message=dynamic_cast<const EmbeddingFailure*>(&x)?x.what():"MODEL_LOAD|NATIVE_EXCEPTION|Native loader threw an exception; raw text withheld";if(message.find("MODEL_LOAD_NULL")!=std::string::npos) {
  switch(load_error.load()) {case 1:message+="; no CPU backend found";break;case 2:message+="; native allocation failed";break;case 3:message+="; unsupported native model data";break;case 4:message+="; native file open failed";break;case 5:message+="; no native backends are loaded";break;default:break;}
 }fail(e,message.c_str());}
 e->ReleaseStringUTFChars(path,p);return result;
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_arm_aichat_EmbeddingGemma_nativeEmbed(JNIEnv *e,jobject,jlong handle,jbyteArray text){
 std::lock_guard<std::mutex>lock(gate);if(!handle){fail(e,"EMBEDDING_PROBE|SESSION_LOST|Embedding engine is not loaded");return nullptr;}
 const jsize n=e->GetArrayLength(text);std::string s(n,0);e->GetByteArrayRegion(text,0,n,reinterpret_cast<jbyte*>(s.data()));jfloatArray result=nullptr;
 try{auto v=reinterpret_cast<LocalEmbedding*>(handle)->embed(s);result=e->NewFloatArray(v.size());if(result)e->SetFloatArrayRegion(result,0,v.size(),v.data());}catch(const std::exception &x){fail(e,dynamic_cast<const EmbeddingFailure*>(&x)?x.what():"EMBEDDING_PROBE|NATIVE_EXCEPTION|Native embedding threw an exception; raw text withheld");}
 return result;
}
extern "C" JNIEXPORT jint JNICALL Java_com_arm_aichat_EmbeddingGemma_nativeDimension(JNIEnv *,jobject,jlong handle){return handle?reinterpret_cast<LocalEmbedding*>(handle)->dimension():0;}
extern "C" JNIEXPORT jstring JNICALL Java_com_arm_aichat_EmbeddingGemma_nativeArchitecture(JNIEnv *e,jobject,jlong handle){char arch[64]={};if(handle)llama_model_meta_val_str(reinterpret_cast<LocalEmbedding*>(handle)->model,"general.architecture",arch,sizeof(arch));return e->NewStringUTF(arch);}
extern "C" JNIEXPORT void JNICALL Java_com_arm_aichat_EmbeddingGemma_nativeClose(JNIEnv *,jobject,jlong handle){std::lock_guard<std::mutex>lock(gate);delete reinterpret_cast<LocalEmbedding*>(handle);}
