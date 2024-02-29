package com.github.guigumua.excel;


@FunctionalInterface
public interface WriteConverter<S, R> {
  R convert(S value);
}
