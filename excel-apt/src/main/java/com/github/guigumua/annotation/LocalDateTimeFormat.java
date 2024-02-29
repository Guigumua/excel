package com.github.guigumua.annotation;

import com.github.guigumua.excel.LocalDateTimeFormatter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface LocalDateTimeFormat {
  String value();

  Class<? extends LocalDateTimeFormatter> formatter() default LocalDateTimeFormatter.class;
}
