package vn.dxlab.recruitment.interview;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Quy tắc thuần về giờ phỏng vấn, không phụ thuộc DB. */
public final class InterviewRules {

    /** Khung giờ phỏng vấn theo Kế hoạch: sáng 10h–12h30, chiều 16h–18h30. */
    public static final List<TimeRange> WINDOWS = List.of(
            new TimeRange(LocalTime.of(10, 0), LocalTime.of(12, 30)),
            new TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 30)));

    /** Hội đồng chuẩn: Giảng viên, Người tổ chức, Lead ngạch và 3 thành viên luân chuyển. */
    public static final int PANEL_SIZE = 6;

    private InterviewRules() {
    }

    public record TimeRange(LocalTime start, LocalTime end) {

        public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }

        public boolean contains(LocalTime otherStart, LocalTime otherEnd) {
            return !otherStart.isBefore(start) && !otherEnd.isAfter(end);
        }
    }

    /** Khoảng giờ nằm trọn trong một khung giờ phỏng vấn. */
    public static boolean withinWindows(LocalTime start, LocalTime end) {
        return WINDOWS.stream().anyMatch(w -> w.contains(start, end));
    }

    /**
     * Tìm {@code count} chỗ trống liên tiếp dài {@code minutes} phút trong ca, bỏ qua các slot đã có.
     * Trả về ít hơn {@code count} chỗ nếu ca không đủ thời gian.
     */
    public static List<TimeRange> freeSlots(TimeRange session, List<TimeRange> occupied, int minutes, int count) {
        List<TimeRange> taken = new ArrayList<>(occupied);
        taken.sort(Comparator.comparing(TimeRange::start));
        List<TimeRange> result = new ArrayList<>();
        LocalTime cursor = session.start();
        while (result.size() < count) {
            LocalTime end = cursor.plusMinutes(minutes);
            // plusMinutes quay vòng qua nửa đêm: end < cursor nghĩa là đã vượt 24h
            if (end.isBefore(cursor) || end.isAfter(session.end())) {
                break;
            }
            LocalTime from = cursor;
            var clash = taken.stream().filter(t -> t.overlaps(from, end)).findFirst();
            if (clash.isPresent()) {
                cursor = clash.get().end();
                continue;
            }
            result.add(new TimeRange(cursor, end));
            cursor = end;
        }
        return result;
    }

    /** Nhãn giờ kiểu file Excel cũ: "10h - 10h15". */
    public static String label(LocalTime start, LocalTime end) {
        return hour(start) + " - " + hour(end);
    }

    private static String hour(LocalTime t) {
        return t.getMinute() == 0 ? t.getHour() + "h" : t.getHour() + "h" + String.format("%02d", t.getMinute());
    }
}
