# Release builds are minified with R8. Libraries bring their own keep rules
# (Anthropic SDK: META-INF/proguard/anthropic-java-core.pro; Hilt, Room, Compose likewise),
# and manifest components (services, activity) are kept automatically.

# Keep file and line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable

# OpenTelemetry (pulled in by the Anthropic SDK) references the compile-time-only @AutoValue
# annotation, which isn't on the classpath at runtime. Suggested by R8's missing_rules.txt.
-dontwarn com.google.auto.value.AutoValue
