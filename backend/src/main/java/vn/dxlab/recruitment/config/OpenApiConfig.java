package vn.dxlab.recruitment.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI chỉ để frontend sinh kiểu TypeScript ({@code npm run gen:api}); chỉ bật ở profile local.
 *
 * <p>Jackson luôn ghi đủ mọi trường của record DTO (giá trị có thể là null), nên mọi trường được đánh dấu
 * bắt buộc. Một trường được coi là không null khi: là kiểu nguyên thủy; có {@code @NotNull/@NotBlank/@NotEmpty};
 * hoặc record gắn JSpecify {@code @NullMarked} và trường không có {@code @Nullable}. Còn lại là nullable.
 * Gắn {@code @NullMarked} dần cho DTO khi làm tới màn hình dùng nó.
 * Mặc định springdoc coi mọi trường là tùy chọn, khiến kiểu TypeScript toàn {@code field?}.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    ModelConverter recordNullabilityConverter() {
        return new RecordNullabilityConverter();
    }

    static class RecordNullabilityConverter implements ModelConverter {

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
            Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
            Class<?> raw = rawClass(type);
            if (resolved == null || raw == null || !raw.isRecord()) {
                return resolved;
            }
            Schema target = resolved.get$ref() != null ? context.getDefinedModels().get(simpleRef(resolved.get$ref())) : resolved;
            if (target == null || target.getProperties() == null) {
                return resolved;
            }
            Map<String, Schema> props = new LinkedHashMap<>(target.getProperties());
            List<String> required = new ArrayList<>();
            boolean nullMarked = raw.isAnnotationPresent(NullMarked.class)
                    || (raw.getEnclosingClass() != null && raw.getEnclosingClass().isAnnotationPresent(NullMarked.class))
                    || raw.getPackage().isAnnotationPresent(NullMarked.class);
            for (RecordComponent rc : raw.getRecordComponents()) {
                Schema prop = props.get(rc.getName());
                if (prop == null) {
                    continue;
                }
                required.add(rc.getName());
                boolean nonNull = rc.getType().isPrimitive()
                        || (nullMarked && !rc.isAnnotationPresent(Nullable.class)
                                && !rc.getAnnotatedType().isAnnotationPresent(Nullable.class))
                        || rc.isAnnotationPresent(NotNull.class) || rc.isAnnotationPresent(NotBlank.class)
                        || rc.isAnnotationPresent(NotEmpty.class)
                        || (rc.getAccessor() != null && rc.getAccessor().isAnnotationPresent(NotNull.class));
                if (!nonNull) {
                    props.put(rc.getName(), nullable(prop));
                }
            }
            target.setProperties(props);
            target.setRequired(required);
            return resolved;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Schema nullable(Schema prop) {
            if (prop.get$ref() != null) {
                // $ref không gắn thêm type được: dùng anyOf [ref, null]
                Schema wrapper = new Schema();
                wrapper.setAnyOf(List.of(new Schema().$ref(prop.get$ref()), new Schema().types(Set.of("null"))));
                return wrapper;
            }
            Set<String> types = prop.getTypes();
            if (types != null && !types.isEmpty()) {
                Set<String> withNull = new java.util.LinkedHashSet<>(types);
                withNull.add("null");
                prop.setTypes(withNull);
            } else if (prop.getType() != null) {
                prop.setTypes(new java.util.LinkedHashSet<>(List.of(prop.getType(), "null")));
            }
            return prop;
        }

        private static Class<?> rawClass(AnnotatedType type) {
            if (type.getType() instanceof Class<?> c) {
                return c;
            }
            if (type.getType() instanceof JavaType jt) {
                return jt.getRawClass();
            }
            try {
                return Json31.mapper().constructType(type.getType()).getRawClass();
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String simpleRef(String ref) {
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
    }
}
