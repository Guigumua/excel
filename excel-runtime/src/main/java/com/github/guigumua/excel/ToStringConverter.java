package com.github.guigumua.excel;


import java.util.Objects;

public class ToStringConverter implements WriteConverter<Object, String> {
  @Override
  public String convert(Object value) {
    return Objects.toString(value);
  }
}
