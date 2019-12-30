#include "nativeh.h"

#include <CoreServices/CoreServices.h>
#include <pthread.h>
#include <memory>

static const int FLAG_FILE_TREE = 1 << 0;
static const int FLAG_EVENT_CREATE = 1 << 1;
static const int FLAG_EVENT_MODIFY = 1 << 2;
static const int FLAG_EVENT_DELETE = 1 << 3;
static const int FLAG_EVENT_OVERFLOW = 1 << 4;

class WatcherService;

struct ThreadInitializerParam{
	WatcherService* watcher;
	dispatch_semaphore_t semaphore;
	bool initSuccessful = false;
};

class WatcherService{
public:
	class WatchKey {
	private:
		WatcherService* service;
		jobject keyObject;
		long pathLen;
		const UniChar* keyPath;
		FSEventStreamRef stream = nullptr;
		bool rootInstall;
	public:
		WatchKey(WatcherService* service, jobject keyobject, CFStringRef path)
		: service(service), keyObject(keyobject) {
			CFIndex len = CFStringGetLength(path);
			UniChar* buf = new UniChar[len];
			CFStringGetCharacters(path, CFRangeMake(0, len), buf);

			this->pathLen = len;
			this->keyPath = buf;

			rootInstall = pathLen == 1 && keyPath[0] == '/';
		}

		WatchKey(const WatchKey&) = delete;
		WatchKey(WatchKey&&) = delete;
		WatchKey& operator=(const WatchKey&) = delete;
		WatchKey& operator=(WatchKey&&) = delete;

		~WatchKey() {
			delete[] keyPath;
		}

		void initStream(FSEventStreamRef stream){
			this->stream = stream;
		}
		FSEventStreamRef getStream() const{
			return this->stream;
		}

		jobject getKeyObject() const{
			return keyObject;
		}

		static void eventCallbackFunction(ConstFSEventStreamRef streamRef, void * __nullable clientCallBackInfo, size_t numEvents, void *eventPaths, const FSEventStreamEventFlags eventFlags[], const FSEventStreamEventId eventIds[]){
			CFArrayRef cfpaths = reinterpret_cast<CFArrayRef>(eventPaths);

			//this fits on the stack just fine, and will hold probably all paths
			UniChar buf[1024 * 16];

			WatchKey* key = reinterpret_cast<WatchKey*>(clientCallBackInfo);
			JNIEnv* env = key->service->threadData->env;

			for(int i = 0; i < numEvents; ++i){
				auto path = (CFStringRef)CFArrayGetValueAtIndex(cfpaths, i);
				CFIndex len = CFStringGetLength(path);

				CFStringGetCharacters(path, CFRangeMake(0, len), buf);
				if(len < key->pathLen){
					//can this happen? received path event that is not under our watch path
					key->notifyOverflow(env);
					continue;
				}
				int cmp = memcmp(key->keyPath, buf, key->pathLen);
				if(cmp != 0){
					//received some path that is not under watched path
					//  if the watched directory changed, it is okay to notify overflow
					key->notifyOverflow(env);
					continue;
				}
				UniChar* bufptr;
				int createstrlen;
				if(len == key->pathLen) {
					//We received event for exactly the same path as the watched one
					createstrlen = 0;
					bufptr = buf;
				} else {
					// if the key was installed for the root, then do not check for a separator as it wont be present
					if(key->rootInstall){
						bufptr = buf + 1;
						createstrlen = (int)(len - 1);
					}else{
						if(buf[key->pathLen] != '/'){
							//the event is not under our watched path, as the next char after should be a separator
							key->notifyOverflow(env);
							continue;
						}
						bufptr = buf + key->pathLen + 1;
						createstrlen = (int)(len - key->pathLen - 1);
					}
				}

				//we cannot reliably detect the correct event flag
				//this is because the OS coalesces events which happen quickly after each other
				//    this is not even affected by the latency parameter at creaton
				// if one creates a file, and waits for the creaton events
				//    and then deletes this file (maybe quickly), one can receive the next event
				//    with flags that signal both the creation and the deletion in the same event
				//    one can not determine the order of the events when one receives the second event
				//    as multiple scenarios possible:
				// 1:
				//   create file
				//     receive event for create
				//   delete file
				//     receive event for create and delete both with the same event
				// 2:
				//   create file
				//     receive event for create
				//   delete file
				//   create file
				//     receive event for create and delete both with the same event
				// The same events received in both scenarios and we cannot be sure
				//    if the file exists in the end or not.
				// We cannot query the file system do determine, as of the inherent concurrency
				//    model of the file system, multiple events could've arrived meanwhile
				// Therefore we make a best effor to determine the nature of the event
				//    and strongly advise the user to always recheck the denoted path
				int notifyeventflag;
				FSEventStreamEventFlags ef = eventFlags[i];
				if((ef & (kFSEventStreamEventFlagUserDropped | kFSEventStreamEventFlagKernelDropped | kFSEventStreamEventFlagMustScanSubDirs)) != 0){
					key->notifyOverflow(env);
					continue;
				}else if((ef & kFSEventStreamEventFlagRootChanged) != 0){
					//the watched path or any parent of it has been modified/changed/deleted
					//signal this as an overflow
					key->notifyOverflow(env);
					continue;
				}else if((ef & kFSEventStreamEventFlagItemCreated) != 0){
					//make create event the first we check as it requires the user to actually
					//  recheck the path
					//the user needs to actively determine what kind of file got created
					//   so it will force a file system access from the user and therefore
					//   requiring him to determine the changes for his own
					notifyeventflag = FLAG_EVENT_CREATE;
				}else if((ef & (kFSEventStreamEventFlagItemInodeMetaMod | kFSEventStreamEventFlagItemModified | kFSEventStreamEventFlagItemFinderInfoMod | kFSEventStreamEventFlagItemChangeOwner | kFSEventStreamEventFlagItemXattrMod)) != 0){
					//we prioritize modification event over deletion
					//  as we don't want to tell the user that the file was deleted, but its still there
					//this will still require a recheck, as semantically modification can include deletion
					notifyeventflag = FLAG_EVENT_MODIFY;
				}else if((ef & (kFSEventStreamEventFlagItemRemoved | kFSEventStreamEventFlagItemRenamed)) != 0){
					notifyeventflag = FLAG_EVENT_DELETE;
				}else{
					//commented out because all of these set the same flag, but kept for explanation
					//unknown flags, notify overflow
					key->notifyOverflow(env);
					continue;
				}


				//no need to allocate path string

				jstring s = env->NewString(bufptr, createstrlen);
				env->CallStaticVoidMethod(key->service->serviceClassRef, key->service->notifyMethodId, key->keyObject, notifyeventflag, s);
				env->DeleteLocalRef(s);
			}
		}
		void notifyOverflow(JNIEnv* env){
			env->CallStaticVoidMethod(service->serviceClassRef, service->notifyMethodId, keyObject, FLAG_EVENT_OVERFLOW, nullptr);
		}
	};
private:
	struct ThreadLocalData{
		bool exit = false;
		dispatch_semaphore_t keyerSemaphore = dispatch_semaphore_create(0);
		dispatch_semaphore_t accessSemaphore = dispatch_semaphore_create(1);
		CFRunLoopRef threadRunLoop = NULL;
		bool threadAlive = false;
		pthread_t threadHandle;
		JNIEnv* env;

		~ThreadLocalData(){
			dispatch_release(keyerSemaphore);
			dispatch_release(accessSemaphore);
		}
	};

	JavaVM* vm;
	jclass serviceClassRef;
	jmethodID notifyMethodId;

	std::shared_ptr<ThreadLocalData> threadData;
	dispatch_semaphore_t blockFinishSemaphore = dispatch_semaphore_create(0);

	void runThread(JNIEnv* env, CFRunLoopRef runloop, std::shared_ptr<ThreadLocalData> threadlocal){
		while(!threadlocal->exit) {
			CFRunLoopRunResult runres = CFRunLoopRunInMode(kCFRunLoopDefaultMode, 10000, false);
			switch (runres) {
				case kCFRunLoopRunFinished:{
					//no timers or sources
					dispatch_semaphore_wait(threadlocal->keyerSemaphore, DISPATCH_TIME_FOREVER);
					break;
				}
				case kCFRunLoopRunStopped:{
					goto after_loop;
				}
				case kCFRunLoopRunTimedOut:{
					//timed out, wait continue loop
					continue;
				}
				default:{
					//unknown run result
					break;
				}
			}
		}
	after_loop:

		vm->DetachCurrentThread();

		dispatch_semaphore_wait(threadlocal->accessSemaphore, DISPATCH_TIME_FOREVER);
		threadlocal->threadRunLoop = NULL;
		dispatch_semaphore_signal(threadlocal->accessSemaphore);
	}
public:
	static void* watcherThreadRunnable(void* param){
		ThreadInitializerParam* initer = reinterpret_cast<ThreadInitializerParam*>(param);
		WatcherService* thiz = initer->watcher;
		JNIEnv* env;
		jint attacherr = thiz->vm->AttachCurrentThreadAsDaemon((void**)&env, NULL);
		if(attacherr != JNI_OK){
			dispatch_semaphore_signal(initer->semaphore);
			return 0;
		}
		std::shared_ptr<ThreadLocalData> threadlocal = thiz->threadData;
		CFRunLoopRef runloop = CFRunLoopGetCurrent();
		threadlocal->threadRunLoop = runloop;
		threadlocal->threadHandle = pthread_self();
		threadlocal->threadAlive = true;
		threadlocal->env = env;
		initer->initSuccessful = true;
		dispatch_semaphore_signal(initer->semaphore);

		thiz->runThread(env, runloop, std::move(threadlocal));
		return 0;
	}
	WatcherService(JavaVM* vm, jclass serviceClassRef, jmethodID notifymethodid)
	: vm(vm), serviceClassRef(serviceClassRef), notifyMethodId(notifymethodid) {
		threadData = std::make_shared<ThreadLocalData>();
	}
	~WatcherService() {
		threadData->exit = true;
		dispatch_semaphore_signal(threadData->keyerSemaphore);
		dispatch_semaphore_wait(threadData->accessSemaphore, DISPATCH_TIME_FOREVER);
		if(threadData->threadRunLoop != NULL){
			CFRunLoopStop(threadData->threadRunLoop);
		}
		dispatch_semaphore_signal(threadData->accessSemaphore);
		if(threadData->threadAlive){
			pthread_join(threadData->threadHandle, nullptr);
		}

	}

	jclass getServiceClassRef() const {
		return serviceClassRef;
	}

	WatchKey* addKey(CFStringRef path, int flags, jobject keyref){
		dispatch_semaphore_wait(threadData->accessSemaphore, DISPATCH_TIME_FOREVER);
		CFRunLoopRef runloop = threadData->threadRunLoop;
		if(runloop == NULL){
			dispatch_semaphore_signal(threadData->accessSemaphore);
			return nullptr;
		}
		WatchKey* result = new WatchKey(this, keyref, path);
		CFRunLoopPerformBlock(runloop, kCFRunLoopDefaultMode, ^{
			CFArrayRef pathstowatch = CFArrayCreate(nullptr, (const void**)&path, 1, nullptr);
			FSEventStreamContext callbackinfo { 0 };
			callbackinfo.info = result;

			FSEventStreamRef stream = FSEventStreamCreate(NULL, WatchKey::eventCallbackFunction, &callbackinfo, pathstowatch, kFSEventStreamEventIdSinceNow, 0, kFSEventStreamCreateFlagUseCFTypes | kFSEventStreamCreateFlagFileEvents | kFSEventStreamCreateFlagWatchRoot | kFSEventStreamCreateFlagNoDefer);
			result->initStream(stream);
			FSEventStreamScheduleWithRunLoop(stream, runloop, kCFRunLoopDefaultMode);
			FSEventStreamStart(stream);

			CFRelease(pathstowatch);

			dispatch_semaphore_signal(blockFinishSemaphore);
		});
		//wake up to get performed right away according to documentation
		CFRunLoopWakeUp(runloop);
		dispatch_semaphore_signal(threadData->keyerSemaphore);
		dispatch_semaphore_wait(blockFinishSemaphore, DISPATCH_TIME_FOREVER);
		dispatch_semaphore_signal(threadData->accessSemaphore);

		CFRelease(path);

		return result;
	}

	void removeKey(WatchKey* key){
		dispatch_semaphore_wait(threadData->accessSemaphore, DISPATCH_TIME_FOREVER);
		CFRunLoopRef runloop = threadData->threadRunLoop;
		dispatch_semaphore_signal(threadData->accessSemaphore);
		FSEventStreamRef stream = key->getStream();
		FSEventStreamStop(stream);
		if(runloop != NULL){
			//if the thread exists before removing the key, runloop can be disposed already
			// skip unscheduling it
			FSEventStreamUnscheduleFromRunLoop(stream, runloop, kCFRunLoopDefaultMode);
		}
		FSEventStreamInvalidate(stream);
		FSEventStreamRelease(stream);
		delete key;
	}
	void poll(WatchKey* key){
		FSEventStreamFlushSync(key->getStream());
	}

	bool isValidKey(WatchKey* key){
		//after the key is created, it doesn't get invalidated
		return true;
	}
};

#ifdef __cplusplus
extern "C" {
#endif
	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    OpenWatcher_native
	 * Signature: ()J
	 */
	JNIEXPORT jlong JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_OpenWatcher_1native
	(JNIEnv * env, jclass serviceclass){
		JavaVM* vm;
		if(env->GetJavaVM(&vm) != 0){
			return 0;
		}
		jmethodID notifymethodid = env->GetStaticMethodID(serviceclass, "notifyEvent", "(Lsaker/osnative/watcher/base/SakerNativeWatchKey;ILjava/lang/String;)V");
		if(notifymethodid == NULL){
			return 0;
		}
		jobject globalref = env->NewGlobalRef(serviceclass);
		if (globalref == NULL) {
			return 0;
		}
		auto* service = new WatcherService(vm, (jclass)globalref, notifymethodid);

		pthread_attr_t threadattrs;
		pthread_attr_init(&threadattrs);
		pthread_attr_setdetachstate(&threadattrs, PTHREAD_CREATE_JOINABLE);
		pthread_t threadhandle;

		ThreadInitializerParam initerparam;
		initerparam.watcher = service;
		initerparam.semaphore = dispatch_semaphore_create(0);

		int threaderr = pthread_create(&threadhandle, &threadattrs, WatcherService::watcherThreadRunnable, &initerparam);
		if(threaderr != 0){
			delete service;
			env->DeleteGlobalRef(globalref);
			dispatch_release(initerparam.semaphore);
			return 0;
		}

		dispatch_semaphore_wait(initerparam.semaphore, DISPATCH_TIME_FOREVER);
		dispatch_release(initerparam.semaphore);
		if(!initerparam.initSuccessful){
			delete service;
			env->DeleteGlobalRef(globalref);
			return 0;
		}
		return (jlong)service;
	}

	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    CloseWatcher_native
	 * Signature: (J)V
	 */
	JNIEXPORT void JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_CloseWatcher_1native
	(JNIEnv * env, jclass serviceclass, jlong service){
		WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
		jobject globalref = realservice->getServiceClassRef();
		delete realservice;
		env->DeleteGlobalRef(globalref);
	}

	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    CreateKeyObject_native
	 * Signature: (JLjava/lang/String;ILsaker/osnative/watcher/base/SakerNativeWatchKey;)J
	 */
	JNIEXPORT jlong JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_CreateKeyObject_1native
	(JNIEnv * env, jclass serviceclass, jlong service, jstring path, jint flags, jobject keyobj){
		WatcherService* realservice = reinterpret_cast<WatcherService*>(service);

		const jchar* pathchars = env->GetStringChars(path, nullptr);
		CFStringRef pathstr = CFStringCreateWithCharacters(NULL, pathchars, env->GetStringLength(path));
		env->ReleaseStringChars(path, pathchars);

		jobject keyref = env->NewGlobalRef(keyobj);

		auto* result = realservice->addKey(pathstr, flags, keyref);
		if(result == nullptr){
			CFRelease(pathstr);
			env->DeleteGlobalRef(keyref);
			return 0;
		}
		return reinterpret_cast<jlong>(result);
	}

	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    CloseKey_native
	 * Signature: (JJ)V
	 */
	JNIEXPORT void JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_CloseKey_1native
	(JNIEnv * env, jclass serviceclass, jlong service, jlong key){
		WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
		WatcherService::WatchKey* realkey = reinterpret_cast<WatcherService::WatchKey*>(key);
		jobject keyobjref = realkey->getKeyObject();
		realservice->removeKey(realkey);
		env->DeleteGlobalRef(keyobjref);
	}

	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    PollKey_native
	 * Signature: (JJ)V
	 */
	JNIEXPORT void JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_PollKey_1native
	(JNIEnv *, jclass serviceclass, jlong service, jlong key){
		WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
		WatcherService::WatchKey* realkey = reinterpret_cast<WatcherService::WatchKey*>(key);
		realservice->poll(realkey);
	}

	/*
	 * Class:     saker_osnative_watcher_macos_SakerMacosWatchService
	 * Method:    KeyIsValid_native
	 * Signature: (JJ)Z
	 */
	JNIEXPORT jboolean JNICALL Java_saker_osnative_watcher_macos_SakerMacosWatchService_KeyIsValid_1native
	(JNIEnv *, jclass serviceclass, jlong service, jlong key){
		WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
		WatcherService::WatchKey* realkey = reinterpret_cast<WatcherService::WatchKey*>(key);
		return realservice->isValidKey(realkey);
	}

#define IMPLEMENTATION_JAVA_CLASS_NAME u"saker.osnative.watcher.macos.SakerMacosWatchService"

	JNIEXPORT jstring JNICALL Java_saker_osnative_watcher_NativeWatcherService_getImplementationClassName_1native
	(JNIEnv * env, jclass clazz){
		return env->NewString((const jchar*)IMPLEMENTATION_JAVA_CLASS_NAME, (sizeof(IMPLEMENTATION_JAVA_CLASS_NAME)) / sizeof(char16_t) - 1);
	}
	
#ifdef __cplusplus
}
#endif
