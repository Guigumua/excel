package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelEntity;

@ExcelEntity
public class City {
  private String name;
  private String address;
  private int intNum;
  private long longNum;
  private char ch;

  public char getCh() {
    return ch;
  }

  public void setCh(char ch) {
    this.ch = ch;
  }

  public int getIntNum() {
    return intNum;
  }

  public void setIntNum(int intNum) {
    this.intNum = intNum;
  }

  public long getLongNum() {
    return longNum;
  }

  public void setLongNum(long longNum) {
    this.longNum = longNum;
  }

  @ExcelColumn(name = "haha")
  public String getAddress() {
    return address;
  }
  // @ExcelColumn(name = "haha")
  public void setAddress(String address) {
    this.address = address;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return "City{" + "name='" + name + '\'' + ", address='" + address + '\'' + '}';
  }
}
