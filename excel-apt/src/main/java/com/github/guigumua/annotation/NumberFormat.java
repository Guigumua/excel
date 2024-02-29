package com.github.guigumua.annotation;

import com.github.guigumua.excel.NumberFormatter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface NumberFormat {
  String value();

  Class<? extends NumberFormatter> formatter() default NumberFormatter.class;
}
