-dontobfuscate
-keep class com.hujiayucc.hook.hook.** { *; }
-keep class com.hujiayucc.hook.data.** { *; }
-keep class com.highcapable.yukihookapi.** { *; }

-dontwarn **

-keepattributes *Annotation*
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class androidx.tracing.** { *; }
-keep class androidx.core.math.MathUtils { *; }
