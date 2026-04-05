# EasyAndroidPermissions consumer rules
# Keep the public API surface for minified consumers
-keep public class com.vamsi.easyandroidpermissions.** { public *; }

# Keep coroutines related classes
-dontwarn kotlinx.coroutines.**