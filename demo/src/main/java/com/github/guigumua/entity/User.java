package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelEntity;

import java.time.LocalDate;

@ExcelEntity
public record User(
    @ExcelColumn(value = "username") String name,
    @ExcelColumn(flat = true)
    City city,
    @ExcelColumn.Ignore LocalDate birthday) {
  public User {
    if (name == null) {
      throw new IllegalArgumentException("name is null");
    }
  }
}