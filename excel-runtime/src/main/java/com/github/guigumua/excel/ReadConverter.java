package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;

@FunctionalInterface
public interface ReadConverter<T> {
  T convert(Cell value);
}
