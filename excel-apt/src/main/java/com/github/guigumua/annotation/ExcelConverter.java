package com.github.guigumua.annotation;

import com.github.guigumua.excel.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({
  ElementType.METHOD,
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.RECORD_COMPONENT
})
public @interface ExcelConverter {
  Class<? extends ReadConverter> reader() default DefaultReadConverter.class;

  Class<? extends WriteConverter> writer() default DefaultWriteConverter.class;
}
