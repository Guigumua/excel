package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;

import java.util.function.Function;

@FunctionalInterface
public interface ReadConverter<T> extends Function<Cell, T> {
  T convert(Cell value);

  @Override
  default T apply(Cell cell) {
    return convert(cell);
  }
}
