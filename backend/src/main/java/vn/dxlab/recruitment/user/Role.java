package vn.dxlab.recruitment.user;

/** Vai trò theo Kế hoạch phần mềm, mục III. */
public enum Role {
    /** Giảng viên phụ trách: xem toàn bộ, chấm điểm, phê duyệt danh sách cuối. */
    LECTURER,
    /** Người tổ chức: quản trị và điều phối toàn bộ quy trình. */
    ORGANIZER,
    /** Lead chuyên môn: duyệt CV, sửa tiêu chí, xem kết quả ngạch mình phụ trách. */
    LEAD,
    /** Thành viên luân chuyển: chỉ chấm điểm trong ca được phân công. */
    MEMBER
}
