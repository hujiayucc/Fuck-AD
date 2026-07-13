-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep,allowoptimization public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-keep @interface com.hujiayucc.hook.annotation.Run { *; }
-keep @interface com.hujiayucc.hook.annotation.RunJiaGu { *; }
-keep @com.hujiayucc.hook.annotation.Run class * { *; }
-keep @com.hujiayucc.hook.annotation.RunJiaGu class * { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*