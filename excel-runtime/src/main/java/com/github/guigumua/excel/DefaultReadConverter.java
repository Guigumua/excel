package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;

import java.math.BigDecimal;
import java.math.BigInteger;

public record DefaultReadConverter<T>(Class<T> clazz) implements ReadConverter<T> {
  @SuppressWarnings("unchecked")
  public static <R> R convert(Cell value, Class<R> clazz) {
    switch (value.getType()) {
      case EMPTY, ERROR -> {
        return null;
      }
      case NUMBER -> {
        if (clazz == BigDecimal.class) {
          return  (R) value.asNumber();
        }
        if (clazz == Byte.class || clazz == byte.class) {
          return  (R) Byte.valueOf(value.asNumber().byteValue());
        }
        if (clazz == Short.class || clazz == short.class) {
          return (R) Short.valueOf(value.asNumber().shortValue());
        }
        if (clazz == Integer.class || clazz == int.class) {
          return (R) Integer.valueOf(value.asNumber().intValue());
        }
        if (clazz == Long.class || clazz == long.class) {
          return (R) Long.valueOf(value.asNumber().longValue());
        }
        if (clazz == Double.class || clazz == double.class) {
          return (R) Double.valueOf(value.asNumber().doubleValue());
        }
        if (clazz == Float.class || clazz == float.class) {
          return (R) Float.valueOf(value.asNumber().floatValue());
        }
        if (clazz == BigInteger.class) {
          return (R) value.asNumber().toBigInteger();
        }
        if (clazz == String.class) {
          return (R) value.getText();
        }
        if (clazz == Object.class) {
          return (R) value.asNumber();
        }
      }
      case BOOLEAN -> {
        if (clazz == Boolean.class || clazz == boolean.class) {
          return (R) value.asBoolean();
        }
        if (clazz == String.class) {
          return (R) value.getText();
        }
        if (clazz == Object.class) {
          return (R) value.asBoolean();
        }
        throw new IllegalArgumentException("Unsupported type: " + clazz);
      }
      case STRING -> {
        if (clazz == String.class || clazz == Object.class) {
          return (R) value.getText();
        }
      }
      case FORMULA -> {
        if (clazz == String.class || clazz == Object.class) {
          return (R) value.getFormula();
        }
      }
    }
    throw new IllegalArgumentException("Unsupported type: " + clazz + " for " + value.getType());
  }
  @Override
  public T convert(Cell value) {
    return convert(value, clazz);
  }
}
