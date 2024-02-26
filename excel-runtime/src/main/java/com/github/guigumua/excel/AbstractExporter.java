package com.github.guigumua.excel;

import org.dhatim.fastexcel.*;

import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractExporter<T> implements Consumer<T> {
  public static final int MAX_ROWS = 1_048_576;
  private final Workbook workbook;
  protected final List<Header> headers;
  protected Worksheet currentSheet;
  protected int x = 0;
  protected int y = 0;
  private int sheetIndex = 1;

  protected AbstractExporter(Workbook workbook, List<Header> headers) {
    this.workbook = workbook;
    this.headers = headers;
    newSheet();
  }

  private void newSheet() {
    this.currentSheet = workbook.newWorksheet("Sheet" + sheetIndex);
    sheetIndex++;
    x = 0;
    y = 0;
    writeHeader(headers);
    x = 0;
    y = headers.getFirst().height() + headers.getFirst().subHeadersHeight();
  }

  private void writeHeader(Header header) {
    currentSheet.value(y, x, header.name());
    var height = header.height();
    var width = header.width();
    var styleSetter = currentSheet.style(y, x);
    if (height > 1 && width > 0 || height > 0 && header.width() > 1) {
      var range = currentSheet.range(y, x, y + height - 1, x + header.width() - 1);
      range.merge();
      styleSetter = range.style();
    }
    setHeaderStyle(styleSetter);

    if (!header.subHeaders().isEmpty()) {
      var startY = y;
      y = startY + height;
      for (var subHeader : header.subHeaders()) {
        writeHeader(subHeader);
      }
      y = startY;
    } else {
      x += header.width();
    }
  }

  protected void setHeaderStyle(StyleSetter styleSetter) {
    styleSetter
        .bold()
        .horizontalAlignment("center")
        .verticalAlignment("bottom")
        .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
        .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
        .borderStyle(BorderSide.TOP, BorderStyle.THIN)
        .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
        .set();
  }

  protected void writeHeader(List<Header> headers) {
    for (var header : headers) {
      writeHeader(header);
    }
  }

  private void nextRow() {
    x = 0;
    y++;
  }

  @Override
  public void accept(T entity) {
    if (y >= MAX_ROWS) {
      newSheet();
    }
    writeEntity(entity);
    nextRow();
  }

  protected abstract void writeEntity(T entity);
}
