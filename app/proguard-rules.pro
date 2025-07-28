-dontobfuscate
-keep class com.hujiayucc.hook.** { *; }
-keep class com.highcapable.yukihookapi.** { *; }

-dontwarn **

-keepattributes *Annotation*
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.annotation.** { *; }
-keep class androidx.tracing.** { *; }
-keep class androidx.core.math.MathUtils { *; }

# 保留 JJWT 相关类
-keep class io.jsonwebtoken.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.json.** { *; }

# 保留签名算法实现
-keep class io.jsonwebtoken.impl.** { *; }
