package com.github.guigumua;

import com.github.guigumua.entity.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.dhatim.fastexcel.Workbook;
import org.junit.jupiter.api.Test;

public class TestExcelWriter {
  @Test
  public void testUserConverter() throws IOException {
    List<User> users =
        List.of(
            new User()
                .setId(1L)
                .setName("张三")
                .setAge(18)
                .setBirthday(LocalDate.now())
                .setSex(true)
                .setCity(new City().setName("上海").setPopulation(20000000L)),
            new User()
                .setId(2L)
                .setName("李四")
                .setAge(20)
                .setBirthday(LocalDate.now())
                .setSex(false)
                .setCity(new City().setName("北京").setPopulation(20000001L)));
    var workbook =
        users.stream().collect(UserConverter.collectorOf(new FileOutputStream("user.xlsx")));
    workbook.finish();
  }
}
