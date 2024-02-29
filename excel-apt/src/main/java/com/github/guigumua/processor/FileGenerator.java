package com.github.guigumua.processor;

import com.github.guigumua.excel.DefaultReadConverter;
import com.github.guigumua.excel.DefaultWriteConverter;
import com.github.guigumua.metadata.ConverterMetadata;
import com.github.guigumua.metadata.Metadata;
import com.github.guigumua.metadata.Properties;
import com.github.guigumua.metadata.Property;

import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FileGenerator {
  private final Map<String, Metadata> metadataCache;
  private final Elements elementUtils;
  private final Types typeUtils;
  private final Filer filer;
  private final Map<String, String> generatedFiles = new HashMap<>();

  public FileGenerator(
      Map<String, Metadata> metadataCache, Elements elementUtils, Types typeUtils, Filer filer) {
    this.metadataCache = metadataCache;
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
    this.filer = filer;
  }

  void generateFile(Metadata metadata) {
    var getters = metadata.getters();
    var setters = metadata.setters();
    var type = metadata.entityType();
    var packageName = elementUtils.getPackageOf(type).getQualifiedName().toString();
    var entityClassName = type.getSimpleName().toString();
    var converterClassName = entityClassName + "Converter";
    try (var writer = filer.createSourceFile(packageName + "." + converterClassName).openWriter()) {
      writer.write(
          """
          package %s;
          %s
          public class %s {
            public static final List<Header> IMPORT_HEADERS = %s;
            public static final List<Header> EXPORT_HEADERS = %s;
          %s

          %s

          %s

          %s
          }
          """
              .formatted(
                  packageName,
                  generateImports(metadata),
                  converterClassName,
                  generateHeaderCreateSourceCode(setters),
                  generateHeaderCreateSourceCode(getters),
                  generateConverterCreation(metadata),
                  generateMethods(entityClassName, metadata),
                  generateExporter(entityClassName, metadata),
                  generateImporter(entityClassName, metadata)));
      generatedFiles.put(entityClassName, packageName + "." + converterClassName);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String generateImports(Metadata metadata) {
    return """
                import com.github.guigumua.excel.Header;
                import com.github.guigumua.excel.RowsConverterSpliterator;
                import com.github.guigumua.excel.AbstractExporter;
                import org.dhatim.fastexcel.Workbook;
                import org.dhatim.fastexcel.Worksheet;
                import org.dhatim.fastexcel.reader.ReadableWorkbook;
                import org.dhatim.fastexcel.reader.Row;
                import org.dhatim.fastexcel.reader.Sheet;
                import java.io.IOException;
                import java.io.OutputStream;
                import java.util.List;
                import java.util.function.Consumer;
                import java.util.function.Function;
                import java.util.stream.Collector;
                import java.util.stream.Stream;
                import java.util.stream.StreamSupport;
                import java.util.Objects;
                """
        + metadata.converters().stream()
            .map(ConverterMetadata::typeElement)
            .map(TypeElement::getQualifiedName)
            .map(Object::toString)
            .distinct()
            .map(s -> "import " + s + ";")
            .collect(Collectors.joining("\n"));
  }

  private String generateHeaderCreateSourceCode(Properties properties) {
    var maxHeight = properties.getMaxHeight();
    return properties.stream()
        .map(
            property -> {
              if (property.subProperties().isEmpty()) {
                return "new Header(\"%s\", %d, %d)"
                    .formatted(
                        Objects.requireNonNullElse(property.columnName(), property.name()),
                        property.width(),
                        maxHeight);
              }
              return "new Header(\"%s\", %d, %d, %s)"
                  .formatted(
                      Objects.requireNonNullElse(property.columnName(), property.name()),
                      property.width(),
                      maxHeight - property.subProperties().getMaxHeight(),
                      generateHeaderCreateSourceCode(property.subProperties()));
            })
        .collect(Collectors.joining(",", "List.of(", ")"));
  }

  private String generateConverterCreation(Metadata metadata) {
    return metadata.converters().stream()
        .map(ConverterMetadata::typeElement)
        .map(TypeElement::getSimpleName)
        .map(Object::toString)
        .distinct()
        .filter(
            s ->
                !s.equals(DefaultReadConverter.class.getSimpleName())
                    && !s.equals(DefaultWriteConverter.class.getSimpleName()))
        .map(
            s ->
                "  public static final %s %s_CONVERTER = new %s();"
                    .formatted(s, camelCaseToUpperCase(s), s))
        .collect(Collectors.joining(";\n"));
  }

  private String camelCaseToUpperCase(String s) {
    return s.chars()
        .collect(
            StringBuilder::new,
            (sb, codepoint) -> {
              if (sb.isEmpty()) {
                sb.append((char) codepoint);
                return;
              }
              if (Character.isUpperCase(codepoint)) {
                sb.append('_').append((char) Character.toUpperCase(codepoint));
              } else {
                sb.append((char) Character.toUpperCase(codepoint));
              }
            },
            StringBuilder::append)
        .toString();
  }

  private String generateDefaultConverterWrite(
      ConverterMetadata converterMetadata, Property property) {
    switch (property.type().toString()) {
      case "java.lang.String",
          "java.lang.Boolean",
          "java.time.LocalDate",
          "java.time.LocalDateTime",
          "java.time.ZonedDateTime",
          "java.util.Date" -> {
        return """
            sheet.value(y, x++, entity.%s());
        """
            .formatted(property.name());
      }
      default -> {
        if (typeUtils.isAssignable(
            property.type(), elementUtils.getTypeElement("java.lang.Number").asType())) {
          return """
              sheet.value(y, x++, entity.%s());
          """
              .formatted(property.name());
        } else {
          return """
              if(entity.%s() != null) {
                sheet.value(y, x, Objects.toString(entity.%s()));
              }
              x++;
          """
              .formatted(property.name(), property.name());
        }
      }
    }
  }

  private String generateEntityValueGetterConverter(Property property) {
    if (property.hasConverter()) {
      var converterMetadata = property.converterMetadata();
      if (converterMetadata.isDefault()) {
        return generateDefaultConverterWrite(converterMetadata, property);
      } else {
        return """
            sheet.value(y, x++, %s_CONVERTER.convert(entity.%s()));
        """
            .formatted(
                camelCaseToUpperCase(converterMetadata.typeElement().getSimpleName().toString()),
                property.name());
      }
    }
    if (property.directly()) {
      return """
            sheet.value(y, x++, entity.%s());
        """
          .formatted(property.name());
    }
    if (property.primitive()) {
      return """
            sheet.value(y, x++, String.valueOf(entity.%s()));
        """
          .formatted(property.name());
    }
    if (property.isInternal()) {
      return """
            if(entity.%s() != null) sheet.value(y, x, String.valueOf(entity.%s()));
            x++;
        """
          .formatted(property.name(), property.name());
    }
    return """
            if(entity.%s() != null) {
              %s.writeRow(sheet, x, y, entity.%s());
            }
            x += %d;
        """
        .formatted(
            property.name(),
            property.type().toString() + "Converter",
            property.name(),
            property.subProperties().getWidth());
  }

  private void mapMulti(Property property, Consumer<Property> consumer) {
    consumer.accept(property);
    var metadata = metadataCache.get(property.name());
    if (metadata != null) {
      metadata.getters().stream().mapMulti(this::mapMulti).forEach(consumer);
    }
  }

  private String generateWriteRowBody(Properties properties) {
    return properties.stream()
        .mapMulti(this::mapMulti)
        .map(this::generateEntityValueGetterConverter)
        .collect(Collectors.joining());
  }

  private String generateExporter(String entityClassName, Metadata metadata) {
    return """
           public static class Exporter extends AbstractExporter<%s> {

             public Exporter(Workbook workbook, List<Header> headers) {
               super(workbook, headers);
             }
             @Override
             protected void writeEntity(%s entity) {
               writeRow(currentSheet, x, y, entity);
             }
           }
         """
        .formatted(entityClassName, entityClassName);
  }

  private String generateMethods(String entityClassName, Metadata metadata) {
    return """
           public static Collector<%s, Workbook, Workbook> collectorOf(OutputStream outputStream) {
             var workbook = new Workbook(outputStream, "excel", "1.0");
             var exporter = new Exporter(workbook, EXPORT_HEADERS);
             return Collector.of(
                 () -> workbook, (ignore, entity) -> exporter.accept(entity), (pre, next) -> workbook);
           }

           public static Stream<%s> streamOf(ReadableWorkbook workbook) {
             return workbook.getSheets().flatMap(new Importer(IMPORT_HEADERS));
           }

           static void writeRow(Worksheet sheet, int x, int y, %s entity) {
         %s
           }
         """
        .formatted(
            entityClassName,
            entityClassName,
            entityClassName,
            generateWriteRowBody(metadata.getters()));
  }

  private String generateImporter(String entityClassName, Metadata metadata) {
    return """
            public static class Importer implements Function<Sheet, Stream<%s>> {
              private final List<Header> headers;

              public Importer(List<Header> headers) {
                this.headers = headers;
              }

              protected %s readEntity(Row row) {
                return null;
              }

              @Override
              public Stream<%s> apply(Sheet sheet) {
                try {
                  return StreamSupport.stream(
                      new RowsConverterSpliterator<>(sheet.openStream(), this::readEntity, headers), false);
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            }
          """
        .formatted(entityClassName, entityClassName, entityClassName);
  }
}
