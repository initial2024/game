# JNI exports use the binary class and method names in bridge.cpp.
-keep,allowoptimization class cn.chronicle.game.NativeBridge { *; }

# C++ calls onProgress([B)V on the actual SAM implementation, including lambdas.
-keep interface cn.chronicle.game.NativeTokenSink { void onProgress(byte[]); }
-keepclassmembers class * implements cn.chronicle.game.NativeTokenSink {
    public void onProgress(byte[]);
}

# kotlinx.serialization and AndroidX WebKit ship their own consumer rules.
# Do not keep the whole game/core/icon packages: normal reachability must shrink them.
