# Permitron Consumer ProGuard Rules
# Keep the public API
-keep public class com.vamsi.permitron.** { public *; }

# Keep coroutines related classes
-dontwarn kotlinx.coroutines.**