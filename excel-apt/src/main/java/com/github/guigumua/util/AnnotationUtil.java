package com.github.guigumua.util;

import javax.lang.model.element.Element;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.function.Supplier;

public class AnnotationUtil {

  public static Object getAnnotationValue(
      Element element, Class<? extends Annotation> annotation, String key) {
    return element.getAnnotationMirrors().stream()
        .filter(
            annotationMirror ->
                annotationMirror.getAnnotationType().toString().equals(annotation.getName()))
        .flatMap(annotationMirror -> annotationMirror.getElementValues().entrySet().stream())
        .filter(entry -> entry.getKey().getSimpleName().toString().equals(key))
        .map(entry -> entry.getValue().getValue())
        .findFirst()
        .orElseThrow();
  }

  public static TypeMirror getAnnotationValueAsType(
      Element element, Class<? extends Annotation> annotation, String key) {
    return (TypeMirror) getAnnotationValue(element, annotation, key);
  }

  public static <T> TypeMirror getTypeMirror(Supplier<Class<?>> function) {
    try{
      function.get();
    } catch (MirroredTypeException e) {
      return e.getTypeMirror();
    }
    throw new IllegalStateException("This should never happen");
  }
}
