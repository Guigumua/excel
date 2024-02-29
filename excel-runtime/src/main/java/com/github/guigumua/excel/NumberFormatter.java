package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;

import java.text.DecimalFormat;
import java.text.ParseException;

public class NumberFormatter extends Formatter<Number> {
  private final DecimalFormat decimalFormat;

  public NumberFormatter(String pattern) {
    super(pattern);
    this.decimalFormat = new DecimalFormat(pattern);
  }

  @Override
  public Number convert(Cell value) {
    try {
      if (value.getType() == CellType.EMPTY) {
        return null;
      }
      return decimalFormat.parse(value.asString());
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String convert(Number value) {
    return decimalFormat.format(value);
  }
}
