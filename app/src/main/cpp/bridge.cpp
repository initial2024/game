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
static jbyteArray generate(JNIEnv* env,jbyteArray input,jint limit,jint timeout,jobject sink) {
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
 auto* sampler=llama_sampler_init_greedy();std::string output;int64_t last_progress=0;
 jmethodID progress=nullptr;if(sink){auto cls=env->GetObjectClass(sink);progress=env->GetMethodID(cls,"onProgress","([B)V");env->DeleteLocalRef(cls);}
 for(int i=0;ok&&i<std::min(256,static_cast<int>(limit))&&!abort_run(nullptr);++i) {
  auto token=llama_sampler_sample(sampler,ctx,-1);if(llama_vocab_is_eog(vocab,token)) break;
  char buf[256];int n=llama_token_to_piece(vocab,token,buf,sizeof(buf),0,true);
  if(n>0)output.append(buf,n);
  if(sink&&progress&&nowms()-last_progress>=80){auto data=env->NewByteArray(output.size());env->SetByteArrayRegion(data,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));env->CallVoidMethod(sink,progress,data);env->DeleteLocalRef(data);last_progress=nowms();if(env->ExceptionCheck()){ok=false;break;}}
  if(llama_decode(ctx,llama_batch_get_one(&token,1))!=0) {ok=false;break;}
 }
 llama_sampler_free(sampler);bool aborted=abort_run(nullptr);deadline.store(0);llama_kv_self_clear(ctx);
 if(env->ExceptionCheck())return nullptr;
 if(!ok||aborted) {fail(env,"Inference interrupted or timed out");return nullptr;}
 auto result=env->NewByteArray(output.size());env->SetByteArrayRegion(result,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_cn_chronicle_game_NativeBridge_generate(JNIEnv* env,jobject,jbyteArray input,jint limit,jint timeout) {return generate(env,input,limit,timeout,nullptr);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_cn_chronicle_game_NativeBridge_generateStreaming(JNIEnv* env,jobject,jbyteArray input,jint limit,jint timeout,jobject sink) {return generate(env,input,limit,timeout,sink);}
extern "C" JNIEXPORT jint JNICALL Java_cn_chronicle_game_NativeBridge_countTokens(JNIEnv* env,jobject,jbyteArray input) {
 std::lock_guard<std::mutex> lock(guard);if(!model){fail(env,"No model loaded");return 0;}const int length=env->GetArrayLength(input);if(length>65536){fail(env,"Prompt too large");return 0;}std::string text(length,'\0');env->GetByteArrayRegion(input,0,length,reinterpret_cast<jbyte*>(text.data()));return -llama_tokenize(llama_model_get_vocab(model),text.data(),text.size(),nullptr,0,true,true);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_cn_chronicle_game_NativeBridge_formatChat(JNIEnv* env,jobject,jobjectArray roleArray,jobjectArray contentArray,jstring overrideName) {
 std::lock_guard<std::mutex> lock(guard);if(!model){fail(env,"No model loaded");return nullptr;}
 int size=env->GetArrayLength(roleArray);if(size<1||size>16||env->GetArrayLength(contentArray)!=size){fail(env,"Invalid chat messages");return nullptr;}
 std::vector<std::string> roles,contents;roles.reserve(size);contents.reserve(size);size_t total=0;
 for(int i=0;i<size;i++){auto role=(jstring)env->GetObjectArrayElement(roleArray,i);auto bytes=(jbyteArray)env->GetObjectArrayElement(contentArray,i);const char* r=env->GetStringUTFChars(role,nullptr);roles.emplace_back(r);env->ReleaseStringUTFChars(role,r);env->DeleteLocalRef(role);int n=env->GetArrayLength(bytes);total+=n;if(total>32768){env->DeleteLocalRef(bytes);fail(env,"Chat context too large");return nullptr;}contents.emplace_back(n,'\0');env->GetByteArrayRegion(bytes,0,n,reinterpret_cast<jbyte*>(contents.back().data()));env->DeleteLocalRef(bytes);if(roles.back()!="user"&&roles.back()!="assistant"&&roles.back()!="system"){fail(env,"Invalid chat role");return nullptr;}}
 const char* requested=env->GetStringUTFChars(overrideName,nullptr);std::string selected(requested);env->ReleaseStringUTFChars(overrideName,requested);
 const char* tmpl=selected=="auto"?llama_model_chat_template(model,nullptr):selected.c_str();if(!tmpl){fail(env,"GGUF has no chat template; select an Instruct model or a supported template");return nullptr;}
 std::vector<llama_chat_message> messages;for(int i=0;i<size;i++)messages.push_back({roles[i].c_str(),contents[i].c_str()});std::vector<char> buffer(total*3+4096);
 int count=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,buffer.data(),buffer.size());if(count>int(buffer.size())&&count<=65536){buffer.resize(count);count=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,buffer.data(),buffer.size());}
 if(count<0||count>int(buffer.size())){fail(env,"Chat template unsupported by this inference engine; choose a supported template");return nullptr;}
 auto result=env->NewByteArray(count);env->SetByteArrayRegion(result,0,count,reinterpret_cast<const jbyte*>(buffer.data()));return result;
}
