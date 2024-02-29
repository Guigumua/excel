package com.github.guigumua;

import com.github.guigumua.entity.UserConverter;
import java.io.File;
import java.io.IOException;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.junit.jupiter.api.Test;

public class TestExcelReader {
  @Test
  public void userTest() throws IOException {
    UserConverter.streamOf(new ReadableWorkbook(new File("user.xlsx")))
        .forEach(System.out::println);
  }
}
