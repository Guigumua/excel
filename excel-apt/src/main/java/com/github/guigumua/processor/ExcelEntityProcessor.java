package com.github.guigumua.processor;

import com.github.guigumua.annotation.ExcelColumn;
import com.github.guigumua.annotation.ExcelConstructor;
import com.github.guigumua.annotation.ExcelConverter;
import com.github.guigumua.annotation.ExcelEntity;
import com.github.guigumua.metadata.ConverterMetadata;
import com.github.guigumua.metadata.Metadata;
import com.github.guigumua.metadata.Properties;
import com.github.guigumua.metadata.Property;
import com.github.guigumua.util.AnnotationUtil;
import com.google.auto.service.AutoService;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private TypeElement defaultReadConverterType;
  private TypeElement defaultWriteConverterType;

  private TypeElement readConverterInterfaceType;
  private TypeElement writeConverterInterfaceType;

  private ExecutableElement readConverterInterfaceMethod;
  private ExecutableElement writeConverterInterfaceMethod;
  private SequencedSet<String> inResolvedTypes = new LinkedHashSet<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    elementUtils = processingEnvironment.getElementUtils();
    messager = processingEnvironment.getMessager();
    filer = processingEnvironment.getFiler();
    typeUtils = processingEnvironment.getTypeUtils();
    defaultReadConverterType =
        elementUtils.getTypeElement("com.github.guigumua.excel.DefaultReadConverter");
    defaultWriteConverterType =
        elementUtils.getTypeElement("com.github.guigumua.excel.DefaultWriteConverter");
    readConverterInterfaceType =
        elementUtils.getTypeElement("com.github.guigumua.excel.ReadConverter");
    writeConverterInterfaceType =
        elementUtils.getTypeElement("com.github.guigumua.excel.WriteConverter");
    readConverterInterfaceMethod =
        readConverterInterfaceType.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getSimpleName().contentEquals("convert"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("can not find method convert"));
    writeConverterInterfaceMethod =
        writeConverterInterfaceType.getEnclosedElements().stream()
            .filter(e -> e.getKind() == ElementKind.METHOD)
            .map(ExecutableElement.class::cast)
            .filter(e -> e.getSimpleName().contentEquals("convert"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("can not find method convert"));
  }

  private final Map<String, Metadata> metadataCache = new HashMap<>();

  private void resolveMetadata(TypeElement type) {
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
    var converters = new ArrayList<ConverterMetadata>();
    var isRecordType = type.getKind() == ElementKind.RECORD;
    if (isRecordType) {
      resolveRecordGetters(type, members.get(ElementKind.METHOD), fields, getters, converters);
      resolveRecordSetters(type, fields, constructor, setters, converters);
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
                  resolveGetter(type, method, name, fields, propertyName, getters, converters);
                } else if (name.startsWith("set")) {
                  resolveSetter(type, method, name, fields, propertyName, setters, converters);
                }
              });
    }
    metadataCache.put(
        qualifiedName, new Metadata(type, constructor, getters, setters, isRecordType, converters));
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
      TypeElement type,
      Map<String, VariableElement> fields,
      ExecutableElement constructor,
      Properties setters,
      List<ConverterMetadata> converters) {
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
      var propertyBuilder = resolveSetters(fieldType, name, parameter, field, converters);
      var property =
          propertyBuilder
              .classType(type)
              .name(name)
              .method(null)
              .isGetter(false)
              .primitive(fieldType.getKind().isPrimitive())
              .build();
      setters.add(property);
    }
  }

  private void resolveSetter(
      TypeElement type,
      ExecutableElement method,
      String name,
      Map<String, VariableElement> fields,
      String propertyName,
      Properties setters,
      List<ConverterMetadata> converters) {
    if (method.getParameters().size() != 1) return;
    var parameterType = method.getParameters().getFirst().asType();
    if (method.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }

    var field = fields.get(propertyName);
    if (field != null && field.getAnnotation(ExcelColumn.Ignore.class) != null) {
      return;
    }
    var propertyBuilder = resolveSetters(parameterType, propertyName, method, field, converters);

    var property =
        propertyBuilder
            .classType(type)
            .name(name)
            .method(method)
            .directly(false)
            .primitive(parameterType.getKind().isPrimitive())
            .isGetter(false)
            .build();
    setters.add(property);
  }

  private Property.PropertyBuilder resolveSetters(
      TypeMirror type,
      String propertyName,
      Element primary,
      Element minor,
      List<ConverterMetadata> converters) {
    var excelColumn = mergeExcelColumn(primary, minor);
    var excelConverter = mergeExcelConverter(primary, minor);
    var isInternalType = isInternalType(type);
    var flatted = excelColumn.flat();
    if (isInternalType && flatted) {
      throw new IllegalStateException(
          "field %s is internal type, but flat is true, it is not supported"
              .formatted(propertyName));
    }
    var propertyBuilder = Property.builder();
    if (excelConverter.hasReader()) {
      var converterMetadataBuilder = ConverterMetadata.builder().reader(true);
      if (excelConverter.isDefaultReader()) {
        converterMetadataBuilder.isDefault(true).typeElement(defaultReadConverterType);
      } else {
        var typeElement = (TypeElement) typeUtils.asElement(excelConverter.readerClass);
        var method =
            typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(
                    e ->
                        elementUtils.overrides(
                            e, readConverterInterfaceMethod, readConverterInterfaceType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("can not find method convert"));
        converterMetadataBuilder.typeElement(typeElement).method(method);
      }
      var converterMetadata = converterMetadataBuilder.build();
      propertyBuilder.hasConverter(true).converterMetadata(converterMetadata);
      propertyBuilder.height(1).width(1).subProperties(new Properties());
      converters.add(converterMetadata);
    } else {
      if (!isInternalType) {
        var typeElement = (TypeElement) typeUtils.asElement(type);
        resolveMetadata(typeElement);
        var properties = metadataCache.get(typeElement.getQualifiedName().toString()).setters();
        propertyBuilder
            .height(flatted ? 0 : 1)
            .width(properties.getWidth())
            .subProperties(properties);
      } else {
        propertyBuilder.width(1).height(1).subProperties(new Properties());
      }
    }
    return propertyBuilder
        .type(type)
        .order(excelColumn.order())
        .isInternal(isInternalType)
        .columnName(excelColumn.value().isBlank() ? propertyName : excelColumn.value());
  }

  private void resolveGetter(
      TypeElement type,
      ExecutableElement method,
      String name,
      Map<String, VariableElement> fields,
      String propertyName,
      Properties getters,
      List<ConverterMetadata> converters) {
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
    var propertyBuilder = resolveGetters(propertyName, returnType, method, field, converters);
    var property =
        propertyBuilder
            .classType(type)
            .name(name)
            .method(method)
            .isGetter(true)
            .directly(writeDirectly(returnType))
            .primitive(returnType.getKind().isPrimitive())
            .build();
    getters.add(property);
  }

  private Property.PropertyBuilder resolveGetters(
      String propertyName,
      TypeMirror typeMirror,
      Element primary,
      Element minor,
      List<ConverterMetadata> converters) {
    var excelColumn = mergeExcelColumn(primary, minor);
    var excelConverter = mergeExcelConverter(primary, minor);
    var isInternalType = isInternalType(typeMirror);
    var propertyBuilder = Property.builder();
    var flatted = excelColumn.flat();
    if (isInternalType && flatted) {
      throw new IllegalStateException(
          "field %s is internal type, but flat is true, it is not supported"
              .formatted(propertyName));
    }
    if (excelConverter.hasWriter()) {
      var converterMetadataBuilder = ConverterMetadata.builder().reader(false);
      if (excelConverter.isDefaultWriter()) {
        converterMetadataBuilder.isDefault(true).typeElement(defaultWriteConverterType);
      } else {
        var typeElement = (TypeElement) typeUtils.asElement(excelConverter.writerClass);
        var method =
            typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(
                    e ->
                        elementUtils.overrides(
                            e, writeConverterInterfaceMethod, writeConverterInterfaceType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("can not find method convert"));
        converterMetadataBuilder.typeElement(typeElement).method(method);
      }
      var converterMetadata = converterMetadataBuilder.build();
      propertyBuilder.hasConverter(true).converterMetadata(converterMetadata);
      propertyBuilder.height(1).width(1).subProperties(new Properties());
      converters.add(converterMetadata);
    } else {
      if (!isInternalType) {
        var typeElement = (TypeElement) typeUtils.asElement(typeMirror);
        resolveMetadata(typeElement);
        var subMetadata = metadataCache.get(typeElement.getQualifiedName().toString());
        var properties = subMetadata.getters();
        propertyBuilder.width(properties.getWidth()).subProperties(properties);
        propertyBuilder.height(flatted ? 0 : 1);
      } else {
        propertyBuilder.height(1).width(1).subProperties(new Properties());
      }
    }
    propertyBuilder
        .type(typeMirror)
        .order(excelColumn.order())
        .columnName(excelColumn.value().isBlank() ? propertyName : excelColumn.value())
        .isInternal(isInternalType);
    return propertyBuilder;
  }

  private void resolveRecordGetters(
      TypeElement type,
      List<? extends Element> methods,
      Map<String, VariableElement> fields,
      Properties getters,
      List<ConverterMetadata> converters) {
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
              var fieldType = field.asType();
              var propertyBuilder = resolveGetters(name, fieldType, method, field, converters);
              var property =
                  propertyBuilder
                      .classType(type)
                      .name(name)
                      .method(method)
                      .directly(writeDirectly(fieldType))
                      .isGetter(true)
                      .primitive(fieldType.getKind().isPrimitive())
                      .build();
              getters.add(property);
            });
  }

  record ExcelColumnData(String value, int order, boolean flat) {
    @Builder
    ExcelColumnData {}
  }

  record ExcelConverterData(TypeMirror writerClass, TypeMirror readerClass) {
    @Builder
    ExcelConverterData {}

    boolean isDefaultWriter() {
      return "com.github.guigumua.excel.DefaultWriteConverter".equals(String.valueOf(writerClass));
    }

    boolean isDefaultReader() {
      return "com.github.guigumua.excel.DefaultReadConverter".equals(String.valueOf(readerClass));
    }

    boolean hasWriter() {
      return writerClass != null;
    }

    boolean hasReader() {
      return readerClass != null;
    }
  }

  private ExcelConverterData mergeExcelConverter(
      @NotNull Element element1, @Nullable Element element2) {
    var builder = ExcelConverterData.builder();
    var opt1 = Optional.ofNullable(element1.getAnnotation(ExcelConverter.class));
    var opt2 = Optional.ofNullable(element2).map(e -> e.getAnnotation(ExcelConverter.class));
    builder.writerClass(
        opt1.map(e -> AnnotationUtil.getTypeMirror(e::writer))
            .orElse(opt2.map(e -> AnnotationUtil.getTypeMirror(e::writer)).orElse(null)));
    builder.readerClass(
        opt1.map(e -> AnnotationUtil.getTypeMirror(e::reader))
            .orElse(opt2.map(e -> AnnotationUtil.getTypeMirror(e::reader)).orElse(null)));
    return builder.build();
  }

  @SuppressWarnings({"unchecked", "rawtypes", "RedundantSuppression", "SameParameterValue"})
  private ExcelColumnData mergeExcelColumn(@NotNull Element element1, @Nullable Element element2) {
    return mergeExcelColumn(
        element1.getAnnotation(ExcelColumn.class),
        Optional.ofNullable(element2).map(e -> e.getAnnotation(ExcelColumn.class)).orElse(null));
  }

  @SuppressWarnings({"unchecked", "rawtypes", "RedundantSuppression", "SameParameterValue"})
  private ExcelColumnData mergeExcelColumn(
      @Nullable ExcelColumn first, @Nullable ExcelColumn second) {
    var builder = ExcelColumnData.builder();
    var firstOpt = Optional.ofNullable(first);
    var secondOpt = Optional.ofNullable(second);
    builder
        .value(
            firstOpt
                .map(ExcelColumn::value)
                .filter(Predicate.not(String::isBlank))
                .orElse(secondOpt.map(ExcelColumn::value).orElse("")))
        .order(
            firstOpt
                .map(ExcelColumn::order)
                .filter(o -> o != Integer.MAX_VALUE)
                .orElse(secondOpt.map(ExcelColumn::order).orElse(Integer.MAX_VALUE)))
        .flat(
            firstOpt.map(ExcelColumn::flat).orElse(secondOpt.map(ExcelColumn::flat).orElse(false)));
    return builder.build();
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
    for (Element annotatedType : annotatedTypes) {
      resolveMetadata((TypeElement) annotatedType);
    }

    FileGenerator fileGenerator = new FileGenerator(metadataCache, elementUtils, typeUtils, filer);
    for (var entry : metadataCache.entrySet()) {
      fileGenerator.generateFile(entry.getValue());
    }
    return false;
  }
}
