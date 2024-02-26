package com.github.guigumua.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(java.lang.annotation.ElementType.CONSTRUCTOR)
@Retention(java.lang.annotation.RetentionPolicy.SOURCE)
public @interface ExcelConstructor {}
