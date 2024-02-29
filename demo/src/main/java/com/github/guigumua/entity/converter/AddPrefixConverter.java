package com.github.guigumua.entity.converter;

import com.github.guigumua.excel.WriteConverter;

public class AddPrefixConverter implements WriteConverter<Object, String> {
  @Override
  public String convert(Object value) {
    return "prefix-" + value;
  }
}
