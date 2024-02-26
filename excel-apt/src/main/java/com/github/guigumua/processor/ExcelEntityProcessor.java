package com.github.guigumua.processor;

import com.github.guigumua.annotation.Converter;
import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConstructor;
import com.github.guigumua.annotation.ExcelEntity;
import com.github.guigumua.metadata.ConverterMetadata;
import com.github.guigumua.metadata.Metadata;
import com.github.guigumua.metadata.Properties;
import com.github.guigumua.metadata.Property;
import com.google.auto.service.AutoService;
import org.dhatim.fastexcel.reader.Cell;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
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

  private final Map<String, Metadata> metadataCache = new HashMap<>();
  private final Map<String, ConverterMetadata> converterMetadataCache = new HashMap<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    elementUtils = processingEnvironment.getElementUtils();
    messager = processingEnvironment.getMessager();
    filer = processingEnvironment.getFiler();
    typeUtils = processingEnvironment.getTypeUtils();
  }

  private void resolveMetadata(SequencedSet<String> inResolvedTypes, TypeElement type) {
    var qualifiedName = type.getQualifiedName().toString();
    if (metadataCache.containsKey(qualifiedName)) return;
    var excelEntity = type.getAnnotation(ExcelEntity.class);
    if (excelEntity == null) {
      throw new IllegalStateException("type %s is not annotated with @ExcelEntity".formatted(type));
    }
    if (inResolvedTypes.contains(qualifiedName)) {
      throw new IllegalStateException(
          "Cyclic dependency detected %s"
              .formatted(
                  inResolvedTypes.stream()
                      .map(Object::toString)
                      .collect(Collectors.joining(" --> "))));
    }
    inResolvedTypes.add(qualifiedName);
    var members =
        elementUtils.getAllMembers(type).stream()
            .filter(ExcelEntityProcessor::isFieldOrMethodOrConstructor)
            .collect(Collectors.groupingBy(Element::getKind));
    var constructor = resolveConstructor(type, members);

    var fields =
        members.get(ElementKind.FIELD).stream()
            .map(VariableElement.class::cast)
            .collect(Collectors.toMap(e -> e.getSimpleName().toString(), Function.identity()));
    var getters = new Properties();
    var setters = new Properties();
    var isRecordType = type.getKind() == ElementKind.RECORD;
    if (isRecordType) {
      resolveRecordGetters(inResolvedTypes, type, members.get(ElementKind.METHOD), fields, getters);
      resolveRecordSetters(inResolvedTypes, type, fields, constructor, setters);
    } else {
      members.get(ElementKind.METHOD).stream()
          .map(ExecutableElement.class::cast)
          .forEach(
              method -> {
                var name = method.getSimpleName().toString();
                if (name.length() <= 4) return;
                if (!method.getModifiers().contains(Modifier.PUBLIC)) return;
                var propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                if (name.startsWith("get")) {
                  resolveGetter(inResolvedTypes, type, method, name, fields, propertyName, getters);
                } else if (name.startsWith("set")) {
                  resolveSetter(inResolvedTypes, type, method, name, fields, propertyName, setters);
                }
              });
    }
    metadataCache.put(
        qualifiedName, new Metadata(type, constructor, getters, setters, isRecordType));
    inResolvedTypes.removeLast();
  }

  @NotNull
  private ExecutableElement resolveConstructor(
      TypeElement type, Map<ElementKind, ? extends List<? extends Element>> members) {
    ExecutableElement constructor = null;
    for (var element : members.get(ElementKind.CONSTRUCTOR)) {
      var e = (ExecutableElement) element;
      if (!e.getModifiers().contains(Modifier.PUBLIC)) {
        continue;
      }
      if (type.getKind() == ElementKind.RECORD) {
        if (e.getAnnotation(ExcelConstructor.class) != null) {
          return e;
        }
        if (type.getRecordComponents().size() == e.getParameters().size()) {
          constructor = e;
        }
      } else if (e.getParameters().isEmpty()) {
        constructor = e;
      }
    }
    if (constructor == null) {
      throw new IllegalStateException("no suitable constructor found in type %s.".formatted(type));
    }
    return constructor;
  }

  private void resolveRecordSetters(
      SequencedSet<String> inResolvedTypes,
      TypeElement type,
      Map<String, VariableElement> fields,
      ExecutableElement constructor,
      Properties setters) {
    var parameters = constructor.getParameters();
    for (var parameter : parameters) {
      if (parameter.getAnnotation(ExcelColumn.Ignore.class) != null) {
        continue;
      }
      var name = parameter.getSimpleName().toString();
      var field = fields.get(name);
      if (field == null) {
        throw new IllegalStateException(
            "resolve constructor parameter %s, but there is no field named %s in type %s"
                .formatted(name, name, type.getQualifiedName()));
      }
      var fieldType = field.asType();
      if (fieldType.getAnnotation(ExcelColumn.Ignore.class) != null) {
        continue;
      }
      var excelColumn =
          mergerAnnotation(
              ExcelColumn.class,
              parameter.getAnnotation(ExcelColumn.class),
              field.getAnnotation(ExcelColumn.class));
      var useConverter = false;
      if (excelColumn.converter() != Object.class) {
        useConverter = true;
        resolveConverterMetadata(excelColumn);
      }
      var propertyBuilder = Property.builder();
      var isInternalType = isInternalType(fieldType);
      if (useConverter) {
        var converterMetadata = converterMetadataCache.get(excelColumn.converter().getTypeName());
        if (converterMetadata == null) throw new IllegalStateException("converter not found");
        var converter =
            converterMetadata.methods().get("(" + Cell.class.getTypeName() + ")->" + fieldType);
        if (converter == null) {
          throw new IllegalStateException(
              "converter method not found for %s->%s in class %s"
                  .formatted(
                      excelColumn.converter().getTypeName(), Cell.class.getTypeName(), fieldType));
        }
        propertyBuilder.converter(converter);
      } else if (isInternalType && excelColumn.flat()) {
        throw new IllegalStateException(
            "field %s is internal type, but flat is true, it is not supported".formatted(name));
      }
      var width = 1;
      var height = 1;
      var subProperties = new Properties();
      if (!isInternalType) {
        var fieldTypeElement = (TypeElement) typeUtils.asElement(fieldType);
        resolveMetadata(inResolvedTypes, fieldTypeElement);
        var properties =
            metadataCache.get(fieldTypeElement.getQualifiedName().toString()).setters();
        if (excelColumn.flat()) {
          height = 0;
        }
        width = properties.getWidth();
        subProperties = properties;
      }
      var property =
          propertyBuilder
              .classType(type)
              .name(name)
              .method(null)
              .type(fieldType)
              .order(excelColumn.order())
              .columnName(excelColumn.value().isBlank() ? name : excelColumn.value())
              .isInternal(isInternalType)
              .isGetter(false)
              .primitive(fieldType.getKind().isPrimitive())
              .width(width)
              .height(height)
              .subProperties(subProperties)
              .build();
      setters.add(property);
    }
  }

  private void resolveConverterMetadata(@NotNull ExcelColumn excelColumn) {
    var converterTypeName = excelColumn.converter().getTypeName();
    var converterClassType = elementUtils.getTypeElement(converterTypeName);
    if (converterClassType == null) {
      throw new IllegalStateException("converter class %s not found".formatted(converterTypeName));
    }
    if (converterMetadataCache.containsKey(converterTypeName)) {
      return;
    }
    var methods =
        elementUtils.getAllMembers(converterClassType).stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .filter(e -> e.getAnnotation(Converter.class) != null)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getParameters().size() == 1)
            .filter(e -> e.getReturnType().getKind() != TypeKind.VOID)
            .collect(Collectors.toMap(ExcelEntityProcessor::signature, Function.identity()));
    var converterMetadata = new ConverterMetadata(converterClassType, methods);
    converterMetadataCache.put(converterTypeName, converterMetadata);
  }

  @NotNull
  private static String signature(ExecutableElement method) {
    return method.getParameters().stream()
            .map(p -> p.asType().toString())
            .collect(Collectors.joining(",", "(", ")"))
        + "->"
        + method.getReturnType().toString();
  }

  private void resolveSetter(
      SequencedSet<String> inResolvedTypes,
      TypeElement type,
      ExecutableElement method,
      String name,
      Map<String, VariableElement> fields,
      String propertyName,
      Properties setters) {
    if (method.getParameters().size() != 1) return;
    var parameterType = method.getParameters().getFirst().asType();
    if (method.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }

    var field = fields.get(propertyName);
    if (field != null && field.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }
    var excelColumn =
        mergerAnnotation(
            ExcelColumn.class,
            method.getAnnotation(ExcelColumn.class),
            Optional.ofNullable(field)
                .map(e -> e.getAnnotation(ExcelColumn.class))
                .orElse(ExcelColumn.DEFAULT));
    var isInternalType = isInternalType(parameterType);
    if (isInternalType && excelColumn.flat()) {
      throw new IllegalStateException(
          "field %s is internal type, but flat is true, it is not supported"
              .formatted(propertyName));
    }
    var width = 1;
    var height = 1;
    var subProperties = new Properties();
    if (!isInternalType) {
      var typeElement = (TypeElement) typeUtils.asElement(parameterType);
      resolveMetadata(inResolvedTypes, typeElement);
      var properties = metadataCache.get(typeElement.getQualifiedName().toString()).setters();
      if (excelColumn.flat()) {
        height = 0;
      }
      width = properties.getWidth();
      subProperties = properties;
    }

    var property =
        Property.builder()
            .classType(type)
            .name(name)
            .method(method)
            .type(parameterType)
            .order(excelColumn.order())
            .columnName(excelColumn.value().isBlank() ? propertyName : excelColumn.value())
            .isInternal(isInternalType)
            .directly(false)
            .primitive(parameterType.getKind().isPrimitive())
            .isGetter(false)
            .width(width)
            .height(height)
            .subProperties(subProperties)
            .build();
    setters.add(property);
  }

  private void resolveGetter(
      SequencedSet<String> inResolvedTypes,
      TypeElement type,
      ExecutableElement method,
      String name,
      Map<String, VariableElement> fields,
      String propertyName,
      Properties getters) {
    if (name.equals("getClass")) return;
    if (!method.getParameters().isEmpty()) return;
    if (method.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }
    var returnType = method.getReturnType();
    var field = fields.get(propertyName);
    if (field != null && field.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }
    var excelColumn =
        mergerAnnotation(
            ExcelColumn.class,
            method.getAnnotation(ExcelColumn.class),
            Optional.ofNullable(field)
                .map(e -> e.getAnnotation(ExcelColumn.class))
                .orElse(ExcelColumn.DEFAULT));
    var isInternalType = isInternalType(returnType);
    if (isInternalType && excelColumn.flat()) {
      throw new IllegalStateException(
          "field %s is internal type, but flat is true, it is not supported"
              .formatted(propertyName));
    }
    var width = 1;
    var height = 1;
    var subProperties = new Properties();
    if (!isInternalType) {
      var typeElement = (TypeElement) typeUtils.asElement(returnType);
      resolveMetadata(inResolvedTypes, typeElement);
      var properties = metadataCache.get(typeElement.getQualifiedName().toString()).getters();
      if (excelColumn.flat()) {
        height = 0;
      }
      width = properties.getWidth();
      subProperties = properties;
    }
    var property =
        Property.builder()
            .classType(type)
            .name(name)
            .method(method)
            .type(returnType)
            .order(excelColumn.order())
            .columnName(excelColumn.value().isBlank() ? propertyName : excelColumn.value())
            .isInternal(isInternalType)
            .isGetter(true)
            .directly(writeDirectly(returnType))
            .primitive(returnType.getKind().isPrimitive())
            .width(width)
            .height(height)
            .subProperties(subProperties)
            .build();
    getters.add(property);
  }

  private void resolveRecordGetters(
      SequencedSet<String> inResolvedTypes,
      TypeElement type,
      List<? extends Element> methods,
      Map<String, VariableElement> fields,
      Properties getters) {
    methods.stream()
        .map(ExecutableElement.class::cast)
        .filter(method -> method.getAnnotation(ExcelColumn.Ignore.class) == null)
        .filter(method -> method.getModifiers().contains(Modifier.PUBLIC))
        .filter(method -> method.getParameters().isEmpty())
        .filter(method -> method.getReturnType().getKind() != TypeKind.VOID)
        .filter(method -> fields.containsKey(method.getSimpleName().toString()))
        .forEach(
            method -> {
              var name = method.getSimpleName().toString();
              var field = fields.get(name);
              if (field == null) {
                throw new IllegalStateException(
                    "resolve method %s, but there is no field named %s in type %s"
                        .formatted(name, name, type.getQualifiedName()));
              }
              if (field.getAnnotation(ExcelColumn.Ignore.class) != null) {
                return;
              }
              var excelColumn =
                  mergerAnnotation(
                      ExcelColumn.class,
                      method.getAnnotation(ExcelColumn.class),
                      field.getAnnotation(ExcelColumn.class));
              var fieldType = field.asType();
              var isInternalType = isInternalType(fieldType);
              if (isInternalType && excelColumn.flat()) {
                throw new IllegalStateException(
                    "field %s is internal type, but flat is true, it is not supported"
                        .formatted(name));
              }
              var width = 1;
              var height = 1;
              var subProperties = new Properties();
              if (!isInternalType) {
                var typeElement = (TypeElement) typeUtils.asElement(fieldType);
                resolveMetadata(inResolvedTypes, typeElement);
                var properties =
                    metadataCache.get(typeElement.getQualifiedName().toString()).getters();
                if (excelColumn.flat()) {
                  height = 0;
                }
                width = properties.getWidth();
                subProperties = properties;
              }
              var property =
                  Property.builder()
                      .classType(type)
                      .name(name)
                      .method(method)
                      .type(fieldType)
                      .order(excelColumn.order())
                      .columnName(excelColumn.value().isBlank() ? name : excelColumn.value())
                      .isInternal(isInternalType)
                      .directly(writeDirectly(fieldType))
                      .isGetter(true)
                      .primitive(fieldType.getKind().isPrimitive())
                      .width(width)
                      .height(height)
                      .subProperties(subProperties)
                      .build();
              getters.add(property);
            });
  }

  @SuppressWarnings({"unchecked", "rawtypes", "RedundantSuppression", "SameParameterValue"})
  private static <T extends Annotation> T mergerAnnotation(Class<T> clazz, T first, T second) {
    return (T)
        Proxy.newProxyInstance(
            clazz.getClassLoader(),
            new Class[] {clazz},
            (proxy, method, args) -> {
              if (method.getName().equals("annotationType")) {
                return clazz;
              }
              var firstValue = first == null ? method.getDefaultValue() : method.invoke(first);
              var secondValue = second == null ? method.getDefaultValue() : method.invoke(second);
              var defaultValue = method.getDefaultValue();
              if (!Objects.equals(firstValue, defaultValue)) {
                return firstValue;
              }
              if (!Objects.equals(secondValue, defaultValue)) {
                return secondValue;
              }
              return defaultValue;
            });
  }

  private static boolean isFieldOrMethodOrConstructor(Element e) {
    var elementKind = e.getKind();
    return elementKind == ElementKind.FIELD
        || elementKind == ElementKind.METHOD
        || elementKind == ElementKind.CONSTRUCTOR;
  }

  private boolean writeDirectly(TypeMirror typeMirror) {
    return switch (typeMirror.getKind()) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> true;
      case DECLARED -> {
        var qualifiedName = ((TypeElement) typeUtils.asElement(typeMirror)).getQualifiedName();
        yield switch (qualifiedName.toString()) {
          case "java.lang.String",
                  "java.lang.Boolean",
                  "java.lang.Byte",
                  "java.lang.Short",
                  "java.lang.Integer",
                  "java.lang.Long",
                  "java.lang.Float",
                  "java.lang.Double",
                  "java.math.BigInteger",
                  "java.math.BigDecimal",
                  "java.util.Date",
                  "java.time.LocalDate",
                  "java.time.LocalDateTime",
                  "java.time.ZonedDateTime" ->
              true;
          default -> false;
        };
      }
      case CHAR -> false;
      default -> throw new IllegalStateException("");
    };
  }

  private boolean isInternalType(TypeMirror typeMirror) {
    if (typeMirror.getKind().isPrimitive()) {
      return true;
    }
    if (typeMirror.getKind() != TypeKind.DECLARED) {
      throw new IllegalStateException("unknown type %s".formatted(typeMirror));
    }
    var type = (TypeElement) typeUtils.asElement(typeMirror);
    if (type == null) {
      throw new IllegalStateException("unknown type %s".formatted(typeMirror));
    }
    var qualifiedName = type.getQualifiedName();
    if (qualifiedName == null) {
      return true;
    }

    switch (qualifiedName.toString()) {
      case "java.lang.String",
          "java.lang.Byte",
          "java.lang.Short",
          "java.lang.Integer",
          "java.lang.Long",
          "java.math.BigInteger",
          "java.math.BigDecimal",
          "java.lang.Boolean",
          "java.util.Date",
          "java.lang.Character",
          "java.time.LocalDate",
          "java.time.LocalDateTime",
          "java.time.ZonedDateTime" -> {
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
      return false;
    }

    var annotatedTypes = roundEnv.getElementsAnnotatedWith(ExcelEntity.class);
    var inResolvedTypes = new LinkedHashSet<String>();
    for (Element annotatedType : annotatedTypes) {
      resolveMetadata(inResolvedTypes, (TypeElement) annotatedType);
    }

    FileGenerator fileGenerator = new FileGenerator(metadataCache, elementUtils, typeUtils, filer);
    for (var entry : metadataCache.entrySet()) {
      fileGenerator.generateFile(entry.getValue());
    }
    return false;
  }
}
