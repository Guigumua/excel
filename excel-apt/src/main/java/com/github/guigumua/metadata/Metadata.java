package com.github.guigumua.metadata;


import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public record Metadata(
    TypeElement entityType,
    ExecutableElement constructor,
    Properties getters,
    Properties setters, boolean isRecord) {
}
