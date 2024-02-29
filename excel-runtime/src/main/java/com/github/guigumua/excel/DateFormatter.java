package com.github.guigumua.excel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;

public class DateFormatter extends Formatter<Date> {
  private final SimpleDateFormat simpleDateFormat;

  public DateFormatter(String pattern) {
    super(pattern);
    this.simpleDateFormat = new SimpleDateFormat(pattern);
  }

  @Override
  public Date convert(Cell value) {
    try {
      if (value.getType() == CellType.EMPTY) {
        return null;
      }
      return simpleDateFormat.parse(value.asString());
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String convert(Date value) {
    return simpleDateFormat.format(value);
  }
}
