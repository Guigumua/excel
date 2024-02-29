package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConstructor;
import com.github.guigumua.annotation.ExcelEntity;

import java.time.LocalDate;

@ExcelEntity
public record User(
    @ExcelColumn(value = "username") String name,
    City city,
    LocalDate birthday) {
  public User {
    if (name == null) {
      throw new IllegalArgumentException("name is null");
    }
  }

  @ExcelConstructor
  public User(String name, City city) {
    this(name, city, LocalDate.now());
  }
}
