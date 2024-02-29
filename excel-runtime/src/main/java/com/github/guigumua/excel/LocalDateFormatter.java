package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateFormatter extends Formatter<LocalDate> {
  private final DateTimeFormatter dateTimeFormatter;

  public LocalDateFormatter(String pattern) {
    super(pattern);
    this.dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
  }

  @Override
  public LocalDate convert(Cell value) {
    if(value.getType() == CellType.EMPTY) {
      return null;
    }
    return dateTimeFormatter.parse(value.asString(), LocalDate::from);
  }

  @Override
  public String convert(LocalDate value) {
    return dateTimeFormatter.format(value);
  }
}
