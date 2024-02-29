package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;

public abstract class Formatter<T> implements WriteConverter<T, String>, ReadConverter<T> {
  private final String pattern;

  protected Formatter(String pattern) {
    this.pattern = pattern;
  }

  public abstract T convert(Cell value);

  public abstract String convert(T value);
}
