package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConstructor;
import com.github.guigumua.annotation.ExcelEntity;

@ExcelEntity
public record EntityTest(@ExcelColumn String a, int b) {

  @ExcelConstructor
  public EntityTest(@ExcelColumn("haha") String a, int b) {
    this.a = a;
    this.b = b;
  }

  @Override
  @ExcelColumn
  public String a() {
    return a;
  }
}
