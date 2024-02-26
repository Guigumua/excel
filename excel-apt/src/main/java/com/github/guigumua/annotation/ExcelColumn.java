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

  Class<?> converter() default Object.class;
  
  ExcelColumn DEFAULT =
      new ExcelColumn() {
        @Override
        public String value() {
          return "";
        }

        @Override
        public int order() {
          return Integer.MAX_VALUE;
        }

        @Override
        public Class<ExcelColumn> annotationType() {
          return ExcelColumn.class;
        }

        @Override
        public boolean flat() {
          return false;
        }

        @Override
        public Class<?> converter() {
          return Object.class;
        }
      };

  @interface Ignore {}
}
