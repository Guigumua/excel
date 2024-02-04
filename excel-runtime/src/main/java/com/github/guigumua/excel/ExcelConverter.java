package com.github.guigumua.excel;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class ExcelConverter {
  public static <T> Stream<T> readEntities(
      ReadableWorkbook workbook, Function<SequencedCollection<String>, Function<Row, T>> converterFactory
  ) {
    var sheets = workbook.getSheets();
    return sheets.map(sheet -> {
      try {
        return sheet.openStream();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).flatMap(rowStream -> StreamSupport.stream(new ConvertSpliterator<T>(rowStream, converterFactory), false));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static <T> void writeEntities(
      Stream<T> entities, Workbook workbook, Map<String, Function<T, Object>> converters
  ) throws IOException {
    var worksheet = workbook.newWorksheet("Sheet1");
    var entries = converters.entrySet();
    {
      var col = 0;
      for (var entry : entries) {
        worksheet.value(0, col++, entry.getKey());
      }
    }
    entities.reduce(1, (index, entity) -> {
      var col = 0;
      for (var entry : entries) {
        worksheet.value(index, col++, Objects.requireNonNullElse(entry.getValue().apply(entity), "").toString());
      }
      return index + 1;
    }, Integer::sum);
    workbook.close();
  }

}
