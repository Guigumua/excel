package com.github.guigumua.excel;

import java.util.function.Function;

@FunctionalInterface
public interface WriteConverter<S, R> extends Function<S, R> {
  R convert(S value);

  @Override
  default R apply(S s) {
    return convert(s);
  }
}
