package com.github.guigumua.excel;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ExcelConverter {
  public static <T> Stream<T> readEntities(
      ReadableWorkbook workbook,
      Function<SequencedCollection<String>, Function<Row, T>> converterFactory) {
    var sheets = workbook.getSheets();
    return sheets
        .map(
            sheet -> {
              try {
                return sheet.openStream();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .flatMap(
            rowStream ->
                StreamSupport.stream(
                    new ConvertSpliterator<T>(rowStream, converterFactory), false));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static <T> void writeEntities(
      Stream<T> entities, Workbook workbook, Map<String, Function<T, Object>> converters)
      throws IOException {
    var worksheet = workbook.newWorksheet("Sheet1");
    var entries = converters.entrySet();
    {
      var col = 0;
      for (var entry : entries) {
        worksheet.value(0, col++, entry.getKey());
      }
    }
    entities.reduce(
        1,
        (index, entity) -> {
          var col = 0;
          for (var entry : entries) {
            var obj = Objects.requireNonNullElse(entry.getValue().apply(entity), "");
            switch (obj) {
              case String s -> worksheet.value(index, col++, s);
              case Number n -> worksheet.value(index, col++, n);
              case Boolean b -> worksheet.value(index, col++, b);
              case Date date -> worksheet.value(index, col++, date);
              case LocalDateTime dateTime -> worksheet.value(index, col++, dateTime);
              case ZonedDateTime zonedDateTime -> worksheet.value(index, col++, zonedDateTime);
              case LocalDate localDate -> worksheet.value(index, col++, localDate);
              default -> worksheet.value(index, col++, obj.toString());
            }
          }
          return index + 1;
        },
        Integer::sum);
    workbook.close();
  }
}
