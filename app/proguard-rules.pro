-dontobfuscate
-keep class com.hujiayucc.hook.HookEntry** { *; }
-keep class com.hujiayucc.hook.data.Item { *; }
-keep class com.hujiayucc.hook.hooker.AppList { *; }
-keep class com.hujiayucc.hook.hooker.ItemTypeReference { *; }

-keep class com.highcapable.yukihookapi.** { *; }

-dontwarn **

-keepattributes *Annotation*
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class androidx.tracing.** { *; }
-keep class androidx.core.math.MathUtils { *; }
