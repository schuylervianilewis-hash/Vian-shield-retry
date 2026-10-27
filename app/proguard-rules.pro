# Vianshield ProGuard/R8 rules.
# Keep native sing-box bridge members if reflection-based integration is enabled.
-keep class io.nekohasekai.** { *; }
-keep class com.example.engine.** { *; }
-dontwarn io.nekohasekai.**
