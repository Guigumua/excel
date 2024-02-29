package com.github.guigumua.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.RECORD_COMPONENT,
  ElementType.PARAMETER
})
public @interface ExcelColumn {

  String value() default "";

  int order() default Integer.MAX_VALUE;

  boolean flat() default false;

  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
  @interface Ignore {}
}
