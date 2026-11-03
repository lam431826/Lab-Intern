package vn.dxlab.recruitment.result.dto;

import java.util.List;

/** Tổng quan theo ngạch (theo quyền xem) và việc cần làm của người đang đăng nhập. */
public record DashboardDto(List<TrackStats> tracks, MyTasks myTasks) {

    public record TrackStats(Long trackId, String trackCode, String trackName, int quota,
                             long candidates, long screeningPending, long screeningOverdue, long screeningPassed,
                             long scheduled, long attended, long absent, long scoresComplete,
                             long decided, long approved, long seatsTaken, long offersAccepted) {
    }

    /**
     * @param sheetsToScore ứng viên đã có mặt trong ca mình ngồi mà mình chưa nộp phiếu
     * @param screeningToDo hồ sơ chờ duyệt CV ở ngạch mình làm Lead (Người tổ chức: mọi ngạch)
     * @param toApprove     kết luận chờ Giảng viên phê duyệt (chỉ với Giảng viên)
     */
    public record MyTasks(long sheetsToScore, long screeningToDo, long toApprove) {
    }
}
