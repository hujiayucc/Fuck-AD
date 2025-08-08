package com.hujiayucc.hook.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Run {
    /** 应用名 */
    String appName();
    /** 应用包名 */
    String packageName();
    /** 启动动作 */
    String action();
    /** 适配版本，留空为通用 */
    String[] versions() default {};
}
