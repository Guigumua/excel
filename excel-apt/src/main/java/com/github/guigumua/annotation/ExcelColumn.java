package com.github.guigumua.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD})
public @interface ExcelColumn {
  String name() default "";
}
