package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelEntity;

@ExcelEntity
public class Address {
  private String name;
  private String locate;
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLocate() {
    return locate;
  }

  public void setLocate(String locate) {
    this.locate = locate;
  }
}
