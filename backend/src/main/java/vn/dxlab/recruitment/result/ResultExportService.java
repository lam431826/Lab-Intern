package vn.dxlab.recruitment.result;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.result.dto.ResultRow;
import vn.dxlab.recruitment.result.dto.TrackResultDto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

/** Xuất bảng kết quả: một sheet tổng hợp mọi ngạch được xem, điểm làm tròn 2 chữ số như trên màn hình. */
@Service
@Transactional(readOnly = true)
public class ResultExportService {

    private static final String[] HEADER = {"Ngạch", "Hạng", "Mã SV", "Họ và tên", "Phỏng vấn", "Phiếu đã nộp",
            "Điểm chung", "Điểm chuyên môn", "Tổng", "Đạt sàn", "Đề xuất Đạt/Không", "Gợi ý", "Kết luận",
            "Chuyển sang", "Phê duyệt", "Email", "Offer", "Ghi chú"};

    private final ResultService results;

    public ResultExportService(ResultService results) {
        this.results = results;
    }

    public byte[] export(Long trackId) {
        List<TrackResultDto> tables = trackId == null ? results.list() : List.of(results.track(trackId));
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Kết quả");
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle score = wb.createCellStyle();
            score.setDataFormat(wb.createDataFormat().getFormat("0.00"));

            Row h = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                h.createCell(i).setCellValue(HEADER[i]);
                h.getCell(i).setCellStyle(headerStyle);
            }
            int r = 1;
            for (TrackResultDto t : tables) {
                for (ResultRow row : t.rows()) {
                    Row x = sheet.createRow(r++);
                    x.createCell(0).setCellValue(t.trackName());
                    if (row.rank() != null) {
                        x.createCell(1).setCellValue(row.rank());
                    }
                    x.createCell(2).setCellValue(row.studentCode());
                    x.createCell(3).setCellValue(row.fullName());
                    x.createCell(4).setCellValue(interview(row.interview()));
                    x.createCell(5).setCellValue(row.submittedSheets() + "/" + row.panelSize());
                    number(x, 6, row.generalAverage(), score);
                    number(x, 7, row.professionalAverage(), score);
                    number(x, 8, row.total(), score);
                    x.createCell(9).setCellValue(row.total() == null ? "" : row.meetsThreshold() ? "Đạt" : "Không đạt");
                    x.createCell(10).setCellValue(row.passVotes() + "/" + row.failVotes());
                    x.createCell(11).setCellValue(label(row.suggestion()));
                    var d = row.decision();
                    if (d != null) {
                        x.createCell(12).setCellValue(label(d.finalStatus()));
                        x.createCell(13).setCellValue(d.transferTrackCode() == null ? "" : d.transferTrackCode());
                        x.createCell(14).setCellValue(d.approved() ? "Đã duyệt" : "Chưa duyệt");
                        x.createCell(15).setCellValue(d.emailStatus() == EmailStatus.SENT ? "Đã gửi" : "Chưa gửi");
                        x.createCell(16).setCellValue(switch (d.offerStatus()) {
                            case NONE -> "";
                            case ACCEPTED -> "Nhận";
                            case DECLINED -> "Từ chối";
                        });
                        x.createCell(17).setCellValue(d.note() == null ? "" : d.note());
                    }
                }
            }
            int[] widths = {20, 6, 12, 26, 14, 10, 10, 14, 8, 10, 12, 12, 14, 12, 11, 9, 9, 40};
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

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        if (value != null) {
            row.createCell(col).setCellValue(value.doubleValue());
            row.getCell(col).setCellStyle(style);
        }
    }

    private static String interview(String code) {
        return switch (code) {
            case "ATTENDED" -> "Có mặt";
            case "ABSENT" -> "Vắng mặt";
            case "SCHEDULED" -> "Đã xếp lịch";
            default -> "Chưa xếp lịch";
        };
    }

    static String label(FinalStatus s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case PENDING -> "Chưa kết luận";
            case HIRED -> "Trúng tuyển";
            case WAITLIST -> "Dự bị";
            case TRANSFER -> "Chuyển ngạch";
            case REJECTED -> "Loại";
        };
    }
}
