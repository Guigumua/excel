package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelEntity;

@ExcelEntity
public class City {
  private String name;
  private Address address;
  private int intNum;
  private long longNum;
  private char ch;
  @ExcelColumn.Ignore
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

  public Address getAddress() {
    return address;
  }

  public void setAddress(Address address) {
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
