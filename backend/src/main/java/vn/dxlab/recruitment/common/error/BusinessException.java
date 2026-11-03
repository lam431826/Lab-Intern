package vn.dxlab.recruitment.common.error;

import org.springframework.http.HttpStatus;

/**
 * Vi phạm quy tắc nghiệp vụ, ví dụ chấm điểm khi ứng viên vắng mặt hay sửa kết luận đã phê duyệt.
 * Mặc định trả 422; dùng 409 cho xung đột trạng thái.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
