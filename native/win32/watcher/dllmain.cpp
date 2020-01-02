/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// dllmain.cpp : Defines the entry point for the DLL application.
#include <Windows.h>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <string>
#include <utility>

#include <jni.h>
#include "nativeh.h"

//we could use FILE_NOTIFY_EXTENDED_INFORMATION if we are above the version "Minimum supported client Windows 10, version 1709[desktop apps only]"
//    https://msdn.microsoft.com/en-us/library/windows/desktop/mt843499(v=vs.85).aspx

#define ALL_NOTIFICATIONS (FILE_NOTIFY_CHANGE_FILE_NAME | FILE_NOTIFY_CHANGE_DIR_NAME | FILE_NOTIFY_CHANGE_ATTRIBUTES | FILE_NOTIFY_CHANGE_SIZE |\
						   FILE_NOTIFY_CHANGE_LAST_WRITE | FILE_NOTIFY_CHANGE_CREATION | FILE_NOTIFY_CHANGE_SECURITY)

static const int FLAG_FILE_TREE = 1 << 0;
static const int FLAG_EVENT_CREATE = 1 << 1;
static const int FLAG_EVENT_MODIFY = 1 << 2;
static const int FLAG_EVENT_DELETE = 1 << 3;
static const int FLAG_EVENT_OVERFLOW = 1 << 4;

class WatcherService {
public:
	class DirectoryWatch {
	public:
		//around 64 kb
		//based on some internet comment:
		//    I have found that the buffer which is populated needs to be declared as a DWORD array(this gets the alignment right) 
		//    and then the size needs to be < 16384 DWORDs.
		//    It can be larger - but this breaks for network drives.
		//    Making it about 16380 DWORDs seems to be the sweet spot for local and network drives.
		static const unsigned int BUFFERSIZE = 16380 * sizeof(DWORD);

		DWORD* buffer = nullptr;
		HANDLE fileHandle = INVALID_HANDLE_VALUE;
		OVERLAPPED overlapped{ 0 };
		BOOL fileTree;
		HANDLE pollEvent = NULL;
		DWORD anyErrorCode = ERROR_SUCCESS;
		jobject keyObject;

		DirectoryWatch(HANDLE fileHandle, BOOL fileTree, jobject keyobject)
			: fileHandle(fileHandle), fileTree(fileTree), keyObject(keyobject) {
			//TODO error check
			pollEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
		}
		~DirectoryWatch() {
			delete buffer;
			closeHandles();
		}

		void closeHandles() {
			if (fileHandle != INVALID_HANDLE_VALUE) {
				CloseHandle(fileHandle);
				fileHandle = INVALID_HANDLE_VALUE;
			}
			if (pollEvent != NULL) {
				SetEvent(pollEvent);
				CloseHandle(pollEvent);
				pollEvent = NULL;
			}
		}

		DWORD readDirectoryChanges(WatcherService* service) {
		start:
			overlapped = { 0 };
			BOOL success = ReadDirectoryChangesW(
				fileHandle,
				buffer,
				BUFFERSIZE,
				fileTree,
				ALL_NOTIFICATIONS,
				NULL,
				&overlapped,
				NULL
			);
			if (!success) {
				DWORD err = GetLastError();
				if (err == ERROR_NOTIFY_ENUM_DIR) {
					service->postOverflowEvent(this);
					goto start;
				}
				return err;
			}
			return ERROR_SUCCESS;
		}
	};
private:
	std::unordered_set<DirectoryWatch*> addedWatches;
	std::unordered_set<DirectoryWatch*> toDeleteWatches;
	std::vector<OVERLAPPED_ENTRY> overlappedEntries;
	std::unordered_set<HANDLE> pollEventNotifies;
	OVERLAPPED pollingOverlapped;
	JavaVM* vm;
	JNIEnv* env = nullptr;
	HANDLE addFileEvent = NULL;

	struct AddFileCallbackParam {
		DirectoryWatch* watch;
		DWORD err = ERROR_ACCESS_DENIED;

		AddFileCallbackParam(DirectoryWatch* watch)
			: watch(watch) {
		}
	};

	static const DWORD COMMAND_KEY_EXIT = -1;
	static const DWORD COMMAND_KEY_POLL = 1;
	static const DWORD COMMAND_KEY_ADD_FILE = 2;
	static const DWORD COMMAND_KEY_REMOVE_FILE = 3;
	static const DWORD COMMAND_KEY_DELETE_FILE = 4;
	static const DWORD COMMAND_KEY_POLL_DONE = 5;

	void executeExitThread(DWORD exitcode) {
		if (this->env != nullptr) {
			//if it was attached
			this->vm->DetachCurrentThread();
		}
		for (auto&& e : pollEventNotifies) {
			SetEvent(e);
		}
		ExitThread(exitcode);
	}

	void handleCompletionEntries(ULONG entriesremoved, bool polling) {
		//iterate in reverse, as handling the result can pop the entry from the vector
		//    i < entriesremoved condition suits as size_t is unsigned
		bool exit = false;
		for (size_t i = entriesremoved - 1; i < entriesremoved; i--) {
			//can be null for poll signaling
			auto&& entry = overlappedEntries[i];
			if (entry.lpOverlapped == NULL) {
				switch (entry.dwNumberOfBytesTransferred) {
					case COMMAND_KEY_EXIT: {
						//defer exit, to make sure all the waiters are notified
						exit = true;
						break;
					}
					case COMMAND_KEY_POLL: {
						DirectoryWatch* watch = reinterpret_cast<DirectoryWatch*>(entry.lpCompletionKey);
						if (overlappedEntries.size() == 1 || watch->anyErrorCode != ERROR_SUCCESS) {
							//no user keys or watch is in invalid state
							SetEvent(watch->pollEvent);
							break;
						}
						pollEventNotifies.insert(watch->pollEvent);
						PostQueuedCompletionStatus(port, COMMAND_KEY_POLL_DONE, (ULONG_PTR)watch->pollEvent, NULL);
						break;
					}
					case COMMAND_KEY_POLL_DONE: {
						HANDLE pollevent = reinterpret_cast<HANDLE>(entry.lpCompletionKey);
						if (pollEventNotifies.erase(pollevent)) {
							SetEvent(pollevent);
						}
						break;
					}
					case COMMAND_KEY_ADD_FILE: {
						AddFileCallbackParam* param = reinterpret_cast<AddFileCallbackParam*>(entry.lpCompletionKey);
						DirectoryWatch* watch = param->watch;
						static_assert(DirectoryWatch::BUFFERSIZE % sizeof(DWORD) == 0, "invalid buffer size");
						watch->buffer = new DWORD[DirectoryWatch::BUFFERSIZE / sizeof(DWORD)];
						DWORD reseterr = watch->readDirectoryChanges(this);
						param->err = reseterr;
						if (reseterr == ERROR_SUCCESS) {
							this->addedWatches.insert(watch);
							this->overlappedEntries.push_back(OVERLAPPED_ENTRY{ 0 });
						}
						SetEvent(this->addFileEvent);
						break;
					}
					case COMMAND_KEY_REMOVE_FILE: {
						DirectoryWatch* watch = reinterpret_cast<DirectoryWatch*>(entry.lpCompletionKey);
						this->overlappedEntries.pop_back();

						this->addedWatches.erase(watch);
						this->toDeleteWatches.insert(watch);
						auto elemsremovedcount = this->pollEventNotifies.erase(watch->pollEvent);
						if (elemsremovedcount) {
							SetEvent(watch->pollEvent);
						}
						CancelIoEx(watch->fileHandle, &watch->overlapped);
						PostQueuedCompletionStatus(port, COMMAND_KEY_DELETE_FILE, (ULONG_PTR)watch, NULL);
						//if postres fails, watch is going to be cleaned up in destructor

						watch->closeHandles();
						break;
					}
					case COMMAND_KEY_DELETE_FILE: {
						DirectoryWatch* watch = reinterpret_cast<DirectoryWatch*>(entry.lpCompletionKey);
						if (this->toDeleteWatches.erase(watch)) {
							delete watch;
						}
						break;
					}
					default:
						break;
				}
			} else {
				handleNotifyInformations(entry);
			}
		}
		if (exit) {
			executeExitThread(0);
		}
		/*if (!polling && !pollEventNotifies.empty()) {
		runPolling();
		}*/
	}

	void runPolling() {
		//SwitchToThread();
		while (true) {
			ULONG entriesremoved;
			BOOL success = GetQueuedCompletionStatusEx(
				port,
				overlappedEntries.data(),
				(ULONG)overlappedEntries.size(),
				&entriesremoved,
				0,
				FALSE
			);
			if (!success) {
				//doesnt matter why we failed, break it
				break;
			}
			handleCompletionEntries(entriesremoved, true);
		}
		for (auto&& e : pollEventNotifies) {
			SetEvent(e);
		}
		pollEventNotifies.clear();
	}

	void runThread() {
		vm->AttachCurrentThreadAsDaemon((void**)&env, NULL);
		while (true) {
			ULONG entriesremoved;
			BOOL success = GetQueuedCompletionStatusEx(
				port,
				overlappedEntries.data(),
				(ULONG)overlappedEntries.size(),
				&entriesremoved,
				INFINITE,
				FALSE
			);
			if (!success) {
				DWORD err = GetLastError();
				for (auto&& w : addedWatches) {
					w->anyErrorCode = err;
					w->closeHandles();
					postOverflowEvent(w);
				}
				executeExitThread(-1);
			}
			handleCompletionEntries(entriesremoved, false);
		}
		//we never reach this
	}

	void handleNotifyInformations(DirectoryWatch* watch, DWORD bytestransferred, const BYTE* p, LPOVERLAPPED ol) {
		if (bytestransferred > 0) {
			while (true) {
				const FILE_NOTIFY_INFORMATION& info = *reinterpret_cast<const FILE_NOTIFY_INFORMATION*>(p);

				switch (info.Action) {
					case FILE_ACTION_MODIFIED: {
						jstring pathstr = env->NewString((const jchar*)info.FileName, info.FileNameLength / 2);
						env->CallStaticVoidMethod(javaServiceClassRef, notifyMethodKeyID, watch->keyObject, FLAG_EVENT_MODIFY, pathstr);
						env->DeleteLocalRef(pathstr);
						break;
					}
					case FILE_ACTION_ADDED:
					case FILE_ACTION_RENAMED_NEW_NAME: {
						jstring pathstr = env->NewString((const jchar*)info.FileName, info.FileNameLength / 2);
						env->CallStaticVoidMethod(javaServiceClassRef, notifyMethodKeyID, watch->keyObject, FLAG_EVENT_CREATE, pathstr);
						env->DeleteLocalRef(pathstr);
						break;
					}
					case FILE_ACTION_REMOVED:
					case FILE_ACTION_RENAMED_OLD_NAME: {
						jstring pathstr = env->NewString((const jchar*)info.FileName, info.FileNameLength / 2);
						env->CallStaticVoidMethod(javaServiceClassRef, notifyMethodKeyID, watch->keyObject, FLAG_EVENT_DELETE, pathstr);
						env->DeleteLocalRef(pathstr);
						break;
					}
					default:
						break;
				}
				if (info.NextEntryOffset == 0) {
					break;
				}
				p += info.NextEntryOffset;
			}
		} else {
			if (addedWatches.find(watch) == addedWatches.end()) {
				//leftover completion from a cancelled IO
				return;
			}
			//other error for a valid key
			postOverflowEvent(watch);
		}
		DWORD reseterr = watch->readDirectoryChanges(this);
		if (reseterr != ERROR_SUCCESS) {
			watch->anyErrorCode = reseterr;
		}
	}

	void postOverflowEvent(DirectoryWatch* watch) {
		env->CallStaticVoidMethod(javaServiceClassRef, notifyMethodKeyID, watch->keyObject, FLAG_EVENT_OVERFLOW, nullptr);
	}

	void handleNotifyInformations(DirectoryWatch* watch, DWORD bytestransferred) {
		handleNotifyInformations(watch, bytestransferred, (BYTE*)watch->buffer, &watch->overlapped);
	}
	void handleNotifyInformations(const OVERLAPPED_ENTRY& opentry) {
		DirectoryWatch* watch = reinterpret_cast<DirectoryWatch*>(opentry.lpCompletionKey);
		handleNotifyInformations(watch, opentry.dwNumberOfBytesTransferred, (BYTE*)watch->buffer, opentry.lpOverlapped);
	}
public:
	HANDLE port = NULL;
	jclass javaServiceClassRef;
	jmethodID notifyMethodKeyID;
	HANDLE thread = NULL;

	WatcherService(JavaVM* vm, jclass javaServiceClassRef, HANDLE port, jmethodID notifymethodkeyid)
		: vm(vm), javaServiceClassRef(javaServiceClassRef), port(port), notifyMethodKeyID(notifymethodkeyid) {
		//TODO error check this
		addFileEvent = CreateEvent(NULL, FALSE, FALSE, NULL);
		overlappedEntries.push_back(OVERLAPPED_ENTRY{ 0 });
	}
	~WatcherService() {
		if (thread != NULL) {
			BOOL postres = PostQueuedCompletionStatus(port, COMMAND_KEY_EXIT, NULL, NULL);
			if (!postres) {
				CloseHandle(port);
				port = NULL;
			}
			WaitForSingleObject(thread, INFINITE);
			CloseHandle(thread);
		}
		if (port != NULL) {
			CloseHandle(port);
		}
		if (addFileEvent != NULL) {
			SetEvent(addFileEvent);
			CloseHandle(addFileEvent);
		}
		for (auto&& w : toDeleteWatches) {
			delete w;
		}
		for (auto&& w : addedWatches) {
			delete w;
		}
	}

	static DWORD WINAPI threadRunnable(_In_ LPVOID lpParameter) {
		WatcherService* thiz = reinterpret_cast<WatcherService*>(lpParameter);
		thiz->runThread();
		return 0;
	}

	DirectoryWatch* addKey(std::wstring filename, int flags, jobject keyobject) {
		HANDLE file = CreateFile(filename.data(),
			FILE_LIST_DIRECTORY,
			FILE_SHARE_READ | FILE_SHARE_DELETE | FILE_SHARE_WRITE,
			NULL,
			OPEN_EXISTING,
			FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED,
			NULL);
		if (file == INVALID_HANDLE_VALUE) {
			return nullptr;
		}
		DirectoryWatch* watch = new DirectoryWatch(file, (flags & FLAG_FILE_TREE) == FLAG_FILE_TREE, keyobject);
		HANDLE fileport = CreateIoCompletionPort(
			file,
			port,
			(ULONG_PTR)watch,
			1);
		if (port == NULL) {
			delete watch;
			return nullptr;
		}
		AddFileCallbackParam param{ watch };

		BOOL postres = PostQueuedCompletionStatus(port, COMMAND_KEY_ADD_FILE, (ULONG_PTR)&param, NULL);
		if (!postres) {
			delete watch;
			return nullptr;
		}
		WaitForSingleObject(addFileEvent, INFINITE);
		//we can't do much if the waiting fails
		if (param.err != ERROR_SUCCESS) {
			delete watch;
			return nullptr;
		}
		return watch;
	}

	BOOL poll(DirectoryWatch* watch) {
		BOOL postres = PostQueuedCompletionStatus(port, COMMAND_KEY_POLL, (ULONG_PTR)watch, NULL);
		if (!postres) {
			return FALSE;
		}
		DWORD wres = WaitForSingleObject(watch->pollEvent, INFINITE);
		if (wres == WAIT_OBJECT_0) {
			return TRUE;
		}
		return FALSE;
	}

	void removeKey(DirectoryWatch* watch) {
		BOOL postres = PostQueuedCompletionStatus(port, COMMAND_KEY_REMOVE_FILE, (ULONG_PTR)watch, NULL);
		if (!postres) {
			return;
		}
		DWORD wres = WaitForSingleObject(watch->pollEvent, INFINITE);
		if (wres == WAIT_OBJECT_0) {
			return;
		}
	}
};
/*
* Class:     saker_osnative_watcher_windows_SakerWindowsWatchService
* Method:    OpenWatcher_native
* Signature: ()J
*/
JNIEXPORT jlong JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_OpenWatcher_1native(JNIEnv * env, jclass serviceclass) {
	JavaVM* vm;
	if (env->GetJavaVM(&vm) != 0) {
		return 0;
	}
	jmethodID notifymethodkeyid = env->GetStaticMethodID(serviceclass, "notifyEvent", "(Lsaker/osnative/watcher/base/SakerNativeWatchKey;ILjava/lang/String;)V");
	if (notifymethodkeyid == NULL) {
		return 0;
	}
	HANDLE port = CreateIoCompletionPort(
		INVALID_HANDLE_VALUE,
		NULL,
		0,
		1);
	if (port == NULL) {
		return NULL;
	}
	jobject globalref = env->NewGlobalRef(serviceclass);
	if (globalref == NULL) {
		return 0;
	}
	auto* result = new WatcherService(vm, (jclass)globalref, port, notifymethodkeyid);
	HANDLE threadhandle = CreateThread(NULL, 0, WatcherService::threadRunnable, result, 0, NULL);
	if (threadhandle == NULL) {
		delete result;
		env->DeleteGlobalRef(globalref);
		return NULL;
	}
	result->thread = threadhandle;
	return (jlong)result;
}

/*
* Class:     saker_osnative_watcher_windows_SakerWindowsWatchService
* Method:    CloseWatcher_native
* Signature: (J)V
*/
JNIEXPORT void JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_CloseWatcher_1native(JNIEnv * env, jclass serviceclass, jlong service) {
	WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
	jobject globalref = realservice->javaServiceClassRef;
	delete realservice;
	env->DeleteGlobalRef(globalref);
}

JNIEXPORT jlong JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_CreateKeyObject_1native
(JNIEnv * env, jclass serviceclass, jlong service, jstring path, jint flags, jobject keyobject) {
	WatcherService* realservice = reinterpret_cast<WatcherService*>(service);

	const auto* pathchars = env->GetStringChars(path, nullptr);
	std::wstring wpath = (const wchar_t*)pathchars;
	env->ReleaseStringChars(path, pathchars);

	jobject keyref = env->NewGlobalRef(keyobject);

	auto* result = realservice->addKey(std::move(wpath), flags, keyref);
	if (result == nullptr) {
		env->DeleteGlobalRef(keyref);
	}
	return reinterpret_cast<jlong>(result);
}

/*
* Class:     saker_osnative_watcher_windows_SakerWindowsWatchService
* Method:    CloseKey_native
* Signature: (JJ)V
*/
JNIEXPORT void JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_CloseKey_1native(JNIEnv * env, jclass serviceclass, jlong service, jlong key) {
	WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
	WatcherService::DirectoryWatch* realkey = reinterpret_cast<WatcherService::DirectoryWatch*>(key);
	jobject keyref = realkey->keyObject;
	realservice->removeKey(realkey);
	env->DeleteGlobalRef(keyref);
}

/*
* Class:     saker_osnative_watcher_windows_SakerWindowsWatchService
* Method:    PollKey_native
* Signature: (JJ)V
*/
JNIEXPORT void JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_PollKey_1native(JNIEnv * env, jclass serviceclass, jlong service, jlong key) {
	WatcherService* realservice = reinterpret_cast<WatcherService*>(service);
	WatcherService::DirectoryWatch* realkey = reinterpret_cast<WatcherService::DirectoryWatch*>(key);
	realservice->poll(realkey);
}

/*
* Class:     saker_osnative_watcher_windows_SakerWindowsWatchService
* Method:    KeyIsValid_native
* Signature: (JJ)Z
*/
JNIEXPORT jboolean JNICALL Java_saker_osnative_watcher_windows_SakerWindowsWatchService_KeyIsValid_1native(JNIEnv * env, jclass serviceclass, jlong service, jlong key) {
	WatcherService::DirectoryWatch* realkey = reinterpret_cast<WatcherService::DirectoryWatch*>(key);
	return realkey->anyErrorCode == ERROR_SUCCESS;
}

#define IMPLEMENTATION_JAVA_CLASS_NAME u"saker.osnative.watcher.windows.SakerWindowsWatchService"

JNIEXPORT jstring JNICALL Java_saker_osnative_watcher_NativeWatcherService_getImplementationClassName_1native(JNIEnv * env, jclass clazz) {
	return env->NewString((const jchar*)IMPLEMENTATION_JAVA_CLASS_NAME, (sizeof(IMPLEMENTATION_JAVA_CLASS_NAME)) / sizeof(char16_t) - 1);
}

BOOL APIENTRY DllMain(HMODULE hModule,
	DWORD  ul_reason_for_call,
	LPVOID lpReserved
) {
	switch (ul_reason_for_call) {
		case DLL_PROCESS_ATTACH:
		case DLL_THREAD_ATTACH:
		case DLL_THREAD_DETACH:
		case DLL_PROCESS_DETACH:
			break;
	}
	return TRUE;
}

