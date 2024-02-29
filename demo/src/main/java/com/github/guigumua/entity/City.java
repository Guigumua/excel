package com.github.guigumua.entity;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConverter;
import com.github.guigumua.annotation.ExcelEntity;
import com.github.guigumua.entity.converter.AddPrefixConverter;
import com.github.guigumua.excel.ToStringConverter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Objects;

@ExcelEntity
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class City {
  @ExcelColumn("名称")
  private String name;

  @ExcelColumn("人口")
  private Long population;
}
