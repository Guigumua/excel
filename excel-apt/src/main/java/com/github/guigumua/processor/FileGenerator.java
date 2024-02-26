package com.github.guigumua.processor;

import com.github.guigumua.metadata.Metadata;
import com.github.guigumua.metadata.Properties;
import com.github.guigumua.metadata.Property;

import javax.annotation.processing.Filer;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FileGenerator {
  private final Map<String, Metadata> metadataCache;
  private final Elements elementUtils;
  private final Types typeUtils;
  private final Filer filer;

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
      var importHeadersStatement = generateHeaderCreateSourceCode(setters);
      var exportHeadersStatement = generateHeaderCreateSourceCode(getters);
      writer.write(
          """
          package %s;

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

          public class %s {
            public static final List<Header> IMPORT_HEADERS = %s;
            public static final List<Header> EXPORT_HEADERS = %s;

          %s

          %s

          %s
          }
          """
              .formatted(
                  packageName,
                  converterClassName,
                  importHeadersStatement,
                  exportHeadersStatement,
                  generateConverterMethods(entityClassName),
                  generateExporter(entityClassName, metadata),
                  generateImporter(entityClassName, metadata)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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

  private String generateEntityValueGetterConverter(
      String entityName, Property property) {
    if (property.directly()) {
      return """
                currentSheet.value(y, x++, %s.%s());
        """
          .formatted(entityName, property.name());
    }
    if (property.primitive()) {
      return """
                currentSheet.value(y, x++, String.valueOf(%s.%s()));
        """
          .formatted(entityName, property.name());
    }
    if (property.isInternal()) {
      return """
                if(%s.%s() != null) currentSheet.value(y, x, String.valueOf(%s.%s()));
                x++;
        """
          .formatted(entityName, property.name(), entityName, property.name());
    }
    return """
                if(%s.%s() != null) {
                  var %s = %s.%s();
        %s
                } else {
                  x += %d;
                }
        """
        .formatted(
            entityName,
            property.name(),
            property.name(),
            entityName,
            property.name(),
            generateWriteEntityMethodBody(property.name(), property.subProperties()).indent(2),
            property.subProperties().getWidth());
  }

  private void mapMulti(
      Property property, Consumer<Property> consumer) {
    consumer.accept(property);
    var metadata = metadataCache.get(property.name());
    if (metadata != null) {
      metadata.getters().stream().mapMulti(this::mapMulti).forEach(consumer);
    }
  }

  private String generateWriteEntityMethodBody(
      String entityName, Properties properties) {
    return properties.stream()
        .mapMulti(this::mapMulti)
        .map(
            property ->
                generateEntityValueGetterConverter(entityName, property))
        .collect(Collectors.joining());
  }

  private String generateExporter(
      String entityClassName, Metadata metadata) {
    return """
           public static class Exporter extends AbstractExporter<%s> {

             protected Exporter(Workbook workbook, List<Header> headers) {
               super(workbook, headers);
             }

             @Override
             protected void writeEntity(%s entity) {
         %s
             }
           }
         """
        .formatted(
            entityClassName,
            entityClassName,
            generateWriteEntityMethodBody("entity", metadata.getters()));
  }

  private String generateConverterMethods(String entityClassName) {
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
         """
        .formatted(entityClassName, entityClassName);
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
