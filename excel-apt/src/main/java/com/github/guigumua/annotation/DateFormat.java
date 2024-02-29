package com.github.guigumua.annotation;

import com.github.guigumua.excel.DateFormatter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface DateFormat {
  String value();

  Class<? extends DateFormatter> formatter() default DateFormatter.class;
}
