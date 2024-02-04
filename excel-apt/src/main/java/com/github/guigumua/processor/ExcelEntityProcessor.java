package com.github.guigumua.processor;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelEntity;
import com.google.auto.service.AutoService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
  "com.github.guigumua.annotation.ExcelEntity",
  "com.github.guigumua.annotation.ExcelColumn"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ExcelEntityProcessor extends AbstractProcessor {
  private Elements elementUtils;

  private Types typeUtils;

  private Messager messager;

  private Filer filer;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    elementUtils = processingEnvironment.getElementUtils();
    messager = processingEnvironment.getMessager();
    filer = processingEnvironment.getFiler();
    typeUtils = processingEnvironment.getTypeUtils();
  }

  private boolean isGetterOrSetter(Element element) {
    if (element.getKind() != ElementKind.METHOD) {
      return false;
    }
    var executableElement = (ExecutableElement) element;
    var s = element.getSimpleName().toString();
    return !s.equals("getClass")
        && s.length() > 4
        && ((s.startsWith("get") && executableElement.getParameters().isEmpty())
            || (s.startsWith("set") && executableElement.getParameters().size() == 1));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
      return false;
    }
    class Property {
      VariableElement field;
      String name;
      TypeMirror type;
      List<? extends AnnotationMirror> annotations;
      ExecutableElement getter;
      ExecutableElement setter;
    }
    var annotatedTypes = roundEnv.getElementsAnnotatedWith(ExcelEntity.class);
    for (Element annotatedType : annotatedTypes) {
      var packageName = elementUtils.getPackageOf(annotatedType).getQualifiedName().toString();
      var entityClassName = annotatedType.getSimpleName().toString();
      var factoryClassName = entityClassName + "ConverterFactory";
      var imported =
          """
          import java.util.SequencedCollection;
          import java.util.function.Function;
          import org.dhatim.fastexcel.reader.Row;

          """;
      var fieldAndMethods =
          elementUtils.getAllMembers((TypeElement) annotatedType).stream()
              .filter(e -> e.getKind() == ElementKind.FIELD || isGetterOrSetter(e))
              .collect(
                  Collectors.groupingBy(
                      (e) -> {
                        var name = e.getSimpleName().toString();
                        if (e.getKind() == ElementKind.FIELD) {
                          return name;
                        }
                        if (name.startsWith("get") || name.startsWith("set")) {
                          return Character.toLowerCase(name.charAt(3)) + name.substring(4);
                        }
                        throw new UnsupportedOperationException();
                      },
                      Collector.of(
                          Property::new,
                          (p, e) -> {
                            if (e.getKind() == ElementKind.FIELD) {
                              p.field = (VariableElement) e;
                              p.name = e.getSimpleName().toString();
                              p.type = e.asType();
                              p.annotations = e.getAnnotationMirrors();
                            } else {
                              var s = e.getSimpleName().toString();
                              if (s.startsWith("get")) {
                                p.getter = (ExecutableElement) e;
                              } else {
                                p.setter = (ExecutableElement) e;
                              }
                            }
                          },
                          (p1, p2) -> {
                            throw new UnsupportedOperationException();
                          },
                          Collector.Characteristics.IDENTITY_FINISH)));
      var readConverterCases = new StringBuilder();
      var writeConverterCases = new StringBuilder();
      for (var entry : fieldAndMethods.entrySet()) {
        var property = entry.getValue();
        if (property.setter != null
            && typeUtils.isSameType(
                property.setter.getParameters().getFirst().asType(), property.type)) {
          var name =
              Optional.of(property.field)
                  .map(f -> f.getAnnotation(ExcelColumn.class))
                  .map(ExcelColumn::name)
                  .or(
                      () ->
                          Optional.of(property.setter)
                              .map(e -> e.getAnnotation(ExcelColumn.class))
                              .map(ExcelColumn::name))
                  .filter(s -> !s.isBlank())
                  .orElse(property.name);
          readConverterCases
              .append("case \"")
              .append(name)
              .append("\" -> entity.")
              .append(property.setter.getSimpleName().toString())
              .append("(row.getCell(colIndex).getText());\n            ");
        }
        if (property.getter != null
          && typeUtils.isSameType(property.getter.getReturnType(), property.type)
        ) {
          var name =
              Optional.of(property.field)
                  .map(f -> f.getAnnotation(ExcelColumn.class))
                  .map(ExcelColumn::name)
                  .or(
                      () ->
                          Optional.of(property.getter)
                              .map(e -> e.getAnnotation(ExcelColumn.class))
                              .map(ExcelColumn::name))
                  .filter(s -> !s.isBlank())
                  .orElse(property.name);
          writeConverterCases
              .append("\"")
              .append(name)
              .append("\", entity -> entity.")
              .append(property.getter.getSimpleName().toString())
              .append("()")
              .append(',')
              .append('\n')
              .append("    ")
          ;
        }
      }
      writeConverterCases.deleteCharAt(writeConverterCases.length() - 6);

      try {
        var sourceFile = filer.createSourceFile(packageName + "." + factoryClassName);
        var writer = sourceFile.openWriter();
        writer.write(
            """
                          package %s;
                          %s

                          import %s.%s;

                          public final class %s {
                            private %s() {}
                            public static final java.util.Map<String, Function<%s, Object>> CONVERTERS = java.util.Map.of(
                              %s);
                            public static Function<Row, %s> factory(SequencedCollection<String> headers) {
                              return row -> {
                                var entity = new %s();
                                var colIndex = 0;
                                for (var s : headers) {
                                  switch (s) {
                                      %s
                                      default -> {}
                                  }
                                  colIndex++;
                                }
                                return entity;
                              };
                            }
                          }
                          """
                .formatted(
                    packageName,
                    imported,
                    packageName,
                    entityClassName,
                    factoryClassName,
                    factoryClassName,
                    entityClassName,
                    writeConverterCases,
                    entityClassName,
                    entityClassName,
                    readConverterCases));
        writer.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    }
    return false;
  }
}
