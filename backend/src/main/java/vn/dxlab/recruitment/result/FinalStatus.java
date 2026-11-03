package vn.dxlab.recruitment.result;

/** Kết luận cuối cùng của hội đồng cho một ứng viên. */
public enum FinalStatus {
    PENDING,
    /** Trúng tuyển. */
    HIRED,
    /** Dự bị: gọi bổ sung theo thứ hạng khi có người từ chối offer. */
    WAITLIST,
    /** Điều chuyển sang ngạch còn thiếu chỉ tiêu. */
    TRANSFER,
    /** Từ chối. */
    REJECTED
}
