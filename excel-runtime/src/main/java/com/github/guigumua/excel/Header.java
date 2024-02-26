package com.github.guigumua.excel;


import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;

public final class Header {
  private final String name;
  private final int width;
  private final int height;
  private final int subHeadersHeight;
  private final SequencedCollection<Header> subHeaders;

  public String name() {
    return name;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int subHeadersHeight() {
    return subHeadersHeight;
  }

  public SequencedCollection<Header> subHeaders() {
    return subHeaders;
  }

  public Header(String name, int width, int height, SequencedCollection<Header> subHeaders) {
    Objects.requireNonNull(name);
    if (!subHeaders.isEmpty()) {
      width = subHeaders.stream().mapToInt(Header::width).sum();
      this.subHeadersHeight = subHeaders.stream().mapToInt(Header::height).max().orElse(0);
    } else {
      this.subHeadersHeight = 0;
    }
    if (width < 0) {
      throw new IllegalArgumentException("Width must be greater than 0");
    }
    if (height < 0) {
      throw new IllegalArgumentException("Height must be greater than 0");
    }
    this.name = name;
    this.width = width;
    this.height = height;
    this.subHeaders = subHeaders;
  }

  public Header(String name) {
    this(name, 1, 1, List.of());
  }

  public Header(String name, int width, int height) {
    this(name, width, height, List.of());
  }

  public Header(String name, List<Header> subHeaders) {
    this(name, 1, 1, subHeaders);
  }

  public Header(String name, int height, List<Header> subHeaders) {
    this(name, 1, height, subHeaders);
  }
}
