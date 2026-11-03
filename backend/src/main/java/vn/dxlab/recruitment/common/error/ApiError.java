package vn.dxlab.recruitment.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Định dạng lỗi thống nhất trả về cho frontend.
 * {@code code} ổn định để frontend rẽ nhánh; {@code message} là câu tiếng Việt hiển thị được ngay.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldViolation> fieldErrors) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
