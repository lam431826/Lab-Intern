package vn.dxlab.recruitment.candidate.dto;

import java.util.List;

/**
 * Kết quả nhập file. Có lỗi ở bất kỳ dòng nào thì không lưu dòng nào ({@code applied = false}),
 * để Người tổ chức sửa file rồi nhập lại một lần cho trọn.
 *
 * @param applied   đã lưu vào database
 * @param dryRun    chỉ kiểm tra, không lưu
 * @param created   số hồ sơ mới
 * @param updated   số hồ sơ đã có (trùng Mã SV) được cập nhật thông tin
 * @param unchanged số hồ sơ đã có, không thay đổi gì
 */
public record ImportReport(boolean applied, boolean dryRun, int totalRows, int created, int updated, int unchanged,
                           List<RowError> errors) {

    /** @param row số dòng như Excel hiển thị (bắt đầu từ 1) */
    public record RowError(int row, String studentCode, String message) {
    }
}
