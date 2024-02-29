package com.github.guigumua.excel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

public class DefaultWriteConverter<S, R> implements WriteConverter<S, R> {
  private final Class<S> clazz;

  public DefaultWriteConverter(Class<S> clazz) {
    this.clazz = clazz;
  }

  @SuppressWarnings("unchecked")
  public static <S, R> R convert(S value, Class<S> clazz) {
    if (value == null) {
      return null;
    }
    if (String.class == clazz
        || Number.class.isAssignableFrom(clazz)
        || Boolean.class == clazz
        || LocalDate.class == clazz
        || LocalDateTime.class == clazz
        || LocalTime.class == clazz
        || Date.class == clazz) {
      return (R) value;
    }
    return (R) Objects.toString(value);
  }

  @Override
  public R convert(S value) {
    return convert(value, clazz);
  }
}
