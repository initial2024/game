#include <jni.h>
#include <llama.h>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>

static llama_model * model=nullptr;
static llama_context * ctx=nullptr;
static std::mutex guard;
static std::atomic<bool> stop{false};
static std::atomic<int64_t> deadline{0};
static int64_t nowms() { return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count(); }
static bool abort_run(void*) { return stop.load() || (deadline.load()>0 && nowms()>deadline.load()); }
static void fail(JNIEnv* env,const char* text) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),text); }
static void clear() { if(ctx) {llama_free(ctx);ctx=nullptr;} if(model) {llama_model_free(model);model=nullptr;} }
extern "C" JNIEXPORT void JNICALL Java_cn_chronicle_game_NativeBridge_cancel(JNIEnv*,jobject) { stop.store(true); }
extern "C" JNIEXPORT void JNICALL Java_cn_chronicle_game_NativeBridge_unload(JNIEnv*,jobject) { stop.store(true);std::lock_guard<std::mutex> lock(guard);clear(); }
extern "C" JNIEXPORT jboolean JNICALL Java_cn_chronicle_game_NativeBridge_load(JNIEnv* env,jobject,jstring path,jint threads,jint context) {
 std::lock_guard<std::mutex> lock(guard);clear();stop.store(false);deadline.store(nowms()+120000);llama_backend_init();
 const char* p=env->GetStringUTFChars(path,nullptr);
 auto mp=llama_model_default_params();mp.n_gpu_layers=0;mp.use_mmap=true;mp.progress_callback=[](float,void*) {return !abort_run(nullptr);};
 model=llama_model_load_from_file(p,mp);env->ReleaseStringUTFChars(path,p);
 if(!model) {fail(env,"Model load failed or cancelled");return false;}
 auto cp=llama_context_default_params();cp.n_ctx=context;cp.n_batch=128;cp.n_ubatch=64;cp.n_threads=threads;cp.n_threads_batch=threads;cp.flash_attn=false;cp.abort_callback=abort_run;
 ctx=llama_init_from_model(model,cp);deadline.store(0);
 if(!ctx) {clear();fail(env,"Context allocation failed");return false;}
 return true;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_cn_chronicle_game_NativeBridge_generate(JNIEnv* env,jobject,jbyteArray input,jint limit,jint timeout) {
 std::lock_guard<std::mutex> lock(guard);
 if(!ctx||!model) {fail(env,"No model loaded");return nullptr;}
 stop.store(false);deadline.store(nowms()+timeout);llama_kv_self_clear(ctx);
 const jsize length=env->GetArrayLength(input);std::string prompt(length,'\0');env->GetByteArrayRegion(input,0,length,reinterpret_cast<jbyte*>(prompt.data()));
 const auto* vocab=llama_model_get_vocab(model);
 int count=-llama_tokenize(vocab,prompt.c_str(),prompt.size(),nullptr,0,true,true);
 if(count<=0||count+limit+8>static_cast<int>(llama_n_ctx(ctx))) {deadline.store(0);fail(env,"Prompt exceeds context limit");return nullptr;}
 std::vector<llama_token> tokens(count);llama_tokenize(vocab,prompt.c_str(),prompt.size(),tokens.data(),count,true,true);
 bool ok=true;
 for(int offset=0;offset<count;offset+=128) {
  int n=std::min(128,count-offset);
  if(abort_run(nullptr)||llama_decode(ctx,llama_batch_get_one(tokens.data()+offset,n))!=0) {ok=false;break;}
 }
 auto* sampler=llama_sampler_init_greedy();std::string output;
 for(int i=0;ok&&i<std::min(256,static_cast<int>(limit))&&!abort_run(nullptr);++i) {
  auto token=llama_sampler_sample(sampler,ctx,-1);if(llama_vocab_is_eog(vocab,token)) break;
  char buf[256];int n=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);
  if(n>0)output.append(buf,n);
  if(llama_decode(ctx,llama_batch_get_one(&token,1))!=0) {ok=false;break;}
 }
 llama_sampler_free(sampler);bool aborted=abort_run(nullptr);deadline.store(0);llama_kv_self_clear(ctx);
 if(!ok||aborted) {fail(env,"Inference interrupted or timed out");return nullptr;}
 auto result=env->NewByteArray(output.size());env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return result;
}
