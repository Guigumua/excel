package com.github.guigumua;

import com.github.guigumua.entity.AddressConverter;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class TestExcelReader {
  @Test
  public void addressTest() throws IOException {
    AddressConverter.streamOf(new ReadableWorkbook(new File("address.xlsx"))).forEach(System.out::println);
  }
}

