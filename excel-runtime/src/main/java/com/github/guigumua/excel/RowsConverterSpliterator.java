package com.github.guigumua.excel;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.dhatim.fastexcel.reader.Row;

public final class RowsConverterSpliterator<T> implements Spliterator<T> {
  private final Spliterator<Row> rows;
  private final Function<Row, T> converter;
  private final SequencedCollection<Header> headers;

  public RowsConverterSpliterator(
      Stream<Row> rowStream, Function<Row, T> converter, SequencedCollection<Header> headers) {
    this.rows = rowStream.spliterator();
    this.headers = headers;
    this.converter = converter;
    resolveHeader();
  }

  private void resolveHeader() {
    var maxHeight = headers.stream().mapToInt(Header::height).max().orElse(0);
    var headerRows = new ArrayList<Row>(maxHeight);
    for (int i = 0; i < maxHeight; i++) {
      if (!rows.tryAdvance(headerRows::add)) {
        throw new IllegalArgumentException("Header rows not enough.");
      }
    }
    int offset = 0;
    for (Header header : headers) {
      validateHeader(header, headerRows, offset, 0);
      offset += header.width();
    }
  }

  private void validateHeader(Header header, List<Row> headerRows, int offset, int deep) {
    if (deep == headerRows.size()) {
      throw new IllegalArgumentException("Header rows not enough.");
    }
    var row = headerRows.get(deep);
    var cell = row.getCell(offset);
    if (cell == null) {
      throw new IllegalArgumentException("Header cell not found.");
    }
    if (!cell.asString().equals(header.name())) {
      throw new IllegalArgumentException("Header cell not match.");
    }
    if (!header.subHeaders().isEmpty()) {
      var subHeaders = header.subHeaders();
      var subOffset = offset;
      for (Header subHeader : subHeaders) {
        validateHeader(subHeader, headerRows, subOffset, deep + 1);
        subOffset += subHeader.width();
      }
    }
  }



  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
    return rows.tryAdvance(
        row -> {
          var obj = converter.apply(row);
          action.accept(obj);
        });
  }

  @Override
  public Spliterator<T> trySplit() {
    return null;
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
