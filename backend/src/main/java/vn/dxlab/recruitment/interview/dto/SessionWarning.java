package vn.dxlab.recruitment.interview.dto;
import org.jspecify.annotations.NullMarked;

/** Cảnh báo không chặn thao tác, hiển thị cho Người tổ chức tự quyết. */
@NullMarked
public record SessionWarning(String code, String message) {
}
