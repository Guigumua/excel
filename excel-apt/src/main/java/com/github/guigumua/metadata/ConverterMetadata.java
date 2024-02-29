package com.github.guigumua.metadata;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import lombok.Builder;
import org.checkerframework.checker.nullness.Opt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public record ConverterMetadata(TypeElement typeElement, ExecutableElement method, boolean isDefault, boolean reader, String initArguments) {
  @Builder
  public ConverterMetadata {}
}
