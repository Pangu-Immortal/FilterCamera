# consumer-rules.pro
# ProGuard rules for core:filter module
#
# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI classes
-keep class com.qihao.filtercamera.core.filter.** { *; }
