package com.github.guigumua.metadata;

import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;

public class Properties extends HashMap<String, Property> {
  @Getter
  private int width = 0;
  @Getter
  private int maxHeight = 0;

  private final PriorityQueue<Property> properties = new PriorityQueue<>(Comparator.comparing(Property::order));

  public Properties() {
    super();
  }

  @Override
  public Property put(String key, Property value) {
    maxHeight = Math.max(maxHeight, value.height() + value.subProperties().getMaxHeight());
    width = width + value.width();
    properties.add(value);
    return super.put(key, value);
  }

  @Override
  public void putAll(Map<? extends String, ? extends Property> m) {
    m.forEach(this::put);
  }

  public void add(Property property) {
    put(property.name(), property);
  }

  public Stream<Property> stream() {
    return properties.stream();
  }

  public void addAll(Properties properties) {
    this.putAll(properties);
  }
}
