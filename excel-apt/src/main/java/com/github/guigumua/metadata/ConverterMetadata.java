package com.github.guigumua.metadata;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.Builder;

import java.util.Map;

public record ConverterMetadata(TypeElement typeElement, Map<String, ExecutableElement> methods) {
  @Builder
  public ConverterMetadata {}
}
