package com.github.guigumua.annotation;

import com.github.guigumua.excel.LocalDateFormatter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface LocalDateFormat {
  String value();

  Class<? extends LocalDateFormatter> formatter() default LocalDateFormatter.class;
}
