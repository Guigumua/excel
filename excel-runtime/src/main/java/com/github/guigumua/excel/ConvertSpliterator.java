package com.github.guigumua.excel;

import org.dhatim.fastexcel.reader.Row;

import java.util.ArrayList;
import java.util.SequencedCollection;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ConvertSpliterator<T> implements Spliterator<T> {
  private final Spliterator<Row> rows;
  private Function<Row, T> converter;

  public ConvertSpliterator(
      Stream<Row> rowStream, Function<SequencedCollection<String>, Function<Row, T>> converterFactory
  ) {
    this.rows = rowStream.spliterator();
    this.rows.tryAdvance(row -> {
      var physicalCellCount = row.getPhysicalCellCount();
      var headers = new ArrayList<String>(physicalCellCount);
      for (int i = 0; i < physicalCellCount; i++) {
        headers.add(row.getCell(i).getText());
      }
      this.converter = converterFactory.apply(headers);
    });
  }

  private ConvertSpliterator(
      Spliterator<Row> rows, Function<Row, T> converter
  ) {
    this.rows = rows;
    this.converter = converter;
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
    return rows.tryAdvance(row -> {
      var obj = converter.apply(row);
      action.accept(obj);
    });
  }

  @Override
  public Spliterator<T> trySplit() {
    var rowSpliterator = this.rows.trySplit();
    if (rowSpliterator == null) {
      return null;
    }
    return new ConvertSpliterator<>(rowSpliterator, converter);
  }

  @Override
  public long estimateSize() {
    return rows.estimateSize();
  }

  @Override
  public int characteristics() {
    return rows.characteristics();
  }
}
