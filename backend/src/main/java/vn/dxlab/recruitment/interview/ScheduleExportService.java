package vn.dxlab.recruitment.interview;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Xuất lịch phỏng vấn ra Excel theo cột của file "Đánh giá ứng viên" cũ (Thời gian PV, Ngày PV, PV2...). */
@Service
@Transactional(readOnly = true)
public class ScheduleExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] HEADER = {"STT", "Ngày PV", "Thời gian PV", "Ngạch", "Phòng", "Mã SV", "Họ và tên",
            "Email", "SĐT", "Lịch", "Lịch cũ (đã dời)", "Điểm danh", "Hội đồng"};

    private final InterviewSessionRepository sessions;
    private final InterviewSlotRepository slots;

    public ScheduleExportService(InterviewSessionRepository sessions, InterviewSlotRepository slots) {
        this.sessions = sessions;
        this.slots = slots;
    }

    public byte[] export(Long trackId, LocalDate from, LocalDate to) {
        Map<Long, InterviewSession> chosen = sessions.findAllByOrderBySessionDateAscStartTimeAsc().stream()
                .filter(s -> trackId == null || s.getTrack().getId().equals(trackId))
                .filter(s -> from == null || !s.getSessionDate().isBefore(from))
                .filter(s -> to == null || !s.getSessionDate().isAfter(to))
                .collect(Collectors.toMap(InterviewSession::getId, Function.identity()));
        List<InterviewSlot> active = chosen.isEmpty() ? List.of() : slots.findActiveInSessions(Set.copyOf(chosen.keySet()));

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Lịch phỏng vấn");
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);

            Row h = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                h.createCell(i).setCellValue(HEADER[i]);
                h.getCell(i).setCellStyle(headerStyle);
            }
            int r = 1;
            for (InterviewSlot slot : active.stream()
                    .sorted(Comparator.comparing((InterviewSlot s) -> s.getSession().getSessionDate())
                            .thenComparing(InterviewSlot::getStartTime))
                    .toList()) {
                InterviewSession s = slot.getSession();
                Candidate c = slot.getCandidate();
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(r);
                row.createCell(1).setCellValue(s.getSessionDate().format(DATE));
                row.createCell(2).setCellValue(InterviewRules.label(slot.getStartTime(), slot.getEndTime()));
                row.createCell(3).setCellValue(s.getTrack().getName());
                row.createCell(4).setCellValue(nullToEmpty(s.getRoom()));
                row.createCell(5).setCellValue(c.getStudentCode());
                row.createCell(6).setCellValue(c.getFullName());
                row.createCell(7).setCellValue(nullToEmpty(c.getEmail()));
                row.createCell(8).setCellValue(nullToEmpty(c.getPhone()));
                row.createCell(9).setCellValue(slot.getSlotType().name());
                row.createCell(10).setCellValue(slots.findByReplacedById(slot.getId())
                        .map(old -> old.getSession().getSessionDate().format(DATE) + " "
                                + InterviewRules.label(old.getStartTime(), old.getEndTime()))
                        .orElse(""));
                row.createCell(11).setCellValue(switch (slot.getAttendance()) {
                    case PENDING -> "";
                    case ATTENDED -> "Có mặt";
                    case ABSENT -> "Vắng mặt";
                });
                row.createCell(12).setCellValue(s.getPanel().stream()
                        .sorted(Comparator.comparing((PanelMember p) -> p.getPanelRole().ordinal()))
                        .map(p -> p.getUser().getFullName())
                        .collect(Collectors.joining(", ")));
                row.getCell(12).setCellStyle(wrap);
                r++;
            }
            int[] widths = {6, 12, 16, 22, 10, 12, 26, 28, 14, 7, 24, 12, 50};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
