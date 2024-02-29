package com.github.guigumua.entity.converter;

import com.github.guigumua.excel.ReadConverter;
import com.github.guigumua.excel.WriteConverter;
import org.dhatim.fastexcel.reader.Cell;

public class SexConverter implements WriteConverter<Boolean,String>, ReadConverter<Boolean> {
  @Override
  public Boolean convert(Cell value) {
    return value.asString().equals("男");
  }

  @Override
  public String convert(Boolean value) {
    return value ? "男" : "女";
  }
}
