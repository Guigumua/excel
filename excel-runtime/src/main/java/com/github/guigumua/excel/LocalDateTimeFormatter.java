package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeFormatter extends Formatter<LocalDateTime> {
  private final DateTimeFormatter dateTimeFormatter;

  public LocalDateTimeFormatter(String pattern) {
    super(pattern);
    this.dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
  }

  @Override
  public LocalDateTime convert(Cell value) {
    if(value.getType() == CellType.EMPTY) {
      return null;
    }
    return dateTimeFormatter.parse(value.asString(), LocalDateTime::from);
  }

  @Override
  public String convert(LocalDateTime value) {
    return dateTimeFormatter.format(value);
  }
}
