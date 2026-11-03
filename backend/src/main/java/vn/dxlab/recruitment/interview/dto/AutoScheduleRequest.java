package vn.dxlab.recruitment.interview.dto;

import java.util.List;

/**
 * {@code candidateIds} rỗng: xếp mọi ứng viên Đạt của ngạch chưa có lịch, theo thứ tự duyệt CV.
 * Có giá trị: chỉ xếp các ứng viên này, theo đúng thứ tự gửi lên.
 */
public record AutoScheduleRequest(List<Long> candidateIds) {

    public AutoScheduleRequest {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }
}
