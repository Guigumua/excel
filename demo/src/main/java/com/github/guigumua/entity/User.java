package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConverter;
import com.github.guigumua.annotation.ExcelEntity;
import com.github.guigumua.annotation.LocalDateFormat;
import com.github.guigumua.entity.converter.SexConverter;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@ExcelEntity
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class User {
  @ExcelColumn("ID")
  private Long id;

  @ExcelColumn("姓名")
  private String name;

  @ExcelColumn("年龄")
  private Integer age;

  @ExcelColumn("生日")
  @LocalDateFormat("yyyy-MM-dd")
  private LocalDate birthday;

  @ExcelColumn("性别")
  @ExcelConverter(writer = SexConverter.class, reader = SexConverter.class)
  private boolean sex;

  @ExcelColumn("城市")
  private City city;
}
