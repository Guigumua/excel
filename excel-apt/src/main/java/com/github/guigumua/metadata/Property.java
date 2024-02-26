package com.github.guigumua.metadata;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import lombok.Builder;
import org.jetbrains.annotations.Nullable;

public record Property(
    TypeElement classType,
    String name,
    @Nullable ExecutableElement method,
    TypeMirror type,
    int order,
    @Nullable String columnName,
    boolean isInternal,
    boolean isGetter,
    int width,
    int height,
    Properties subProperties,
    boolean directly,
    boolean primitive,
    ExecutableElement converter) {
  @Builder
  public Property {}
}
