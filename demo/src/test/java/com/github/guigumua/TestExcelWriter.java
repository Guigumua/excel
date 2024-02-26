package com.github.guigumua;

import com.github.guigumua.entity.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class TestExcelWriter {
  @Test
  public void test() throws IOException {}

  @Test
  public void testCityConverter() throws IOException {}

  @Test
  public void testUserConverter() throws IOException {
    var generator = RandomGenerator.getDefault();
    try (var workbook =
        Stream.generate(
                () -> {
                  var city = new City();
                  var address = new Address();
                  city.setAddress(address);
                  address.setLocate("locate" + generator.nextInt(100));
                  address.setName("name" + generator.nextInt(100));
                  city.setCh(((char) generator.nextInt(65, 120)));
                  city.setName("name" + generator.nextInt(100));
                  city.setIntNum(generator.nextInt(100));
                  city.setLongNum(generator.nextLong());
                  return new User(
                      "name" + generator.nextInt(100),
                      city,
                      LocalDate.now().minusDays(generator.nextInt(10000)));
                })
            .limit(100)
            .collect(UserConverter.collectorOf(new FileOutputStream("user.xlsx")))) {}
  }
}
