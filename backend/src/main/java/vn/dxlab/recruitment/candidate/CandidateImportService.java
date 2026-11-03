package vn.dxlab.recruitment.candidate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.dto.CandidateRequest;
import vn.dxlab.recruitment.candidate.dto.ImportReport;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.text.VietnameseText;
import vn.dxlab.recruitment.interview.InterviewSlotRepository;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.track.TrackResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nhập hồ sơ từ Excel (Bước 1 "Đổ dữ liệu" của quy trình sàng lọc).
 *
 * <ul>
 *   <li>Tự tìm dòng tiêu đề và nhận cột theo nhiều cách đặt tên ("Mã SV", "MSSV", "Họ và tên", "Tên ứng viên"...).</li>
 *   <li>Trùng Mã SV với hồ sơ đã có thì cập nhật thông tin, không đụng tới kết quả sàng lọc.</li>
 *   <li>Kiểm tra toàn bộ file trước; có lỗi ở dòng nào thì không lưu dòng nào.</li>
 * </ul>
 */
@Service
public class CandidateImportService {

    static final int MAX_ROWS = 1000;
    private static final int HEADER_SEARCH_ROWS = 15;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    enum Column {
        STUDENT_CODE("Mã SV", "ma sv", "mssv", "ma sinh vien", "ma so sinh vien"),
        FULL_NAME("Họ và tên", "ho va ten", "ho ten", "ten ung vien", "ten", "ho va ten ung vien"),
        EMAIL("Email", "email", "e mail", "gmail"),
        PHONE("SĐT", "sdt", "so dien thoai", "dien thoai", "phone"),
        MAJOR("Chuyên ngành", "chuyen nganh", "nganh hoc"),
        TRACK("Ngạch", "ngach", "vi tri", "vi tri ung tuyen", "khoi", "ngach ung tuyen"),
        CV_LINK("Link CV", "link cv", "cv", "link cv portfolio"),
        PORTFOLIO("Link Portfolio", "link portfolio", "portfolio");

        final String header;
        final Set<String> aliases;

        Column(String header, String... aliases) {
            this.header = header;
            this.aliases = Set.of(aliases);
        }

        static Column match(String normalizedHeader) {
            for (Column c : values()) {
                if (c.aliases.contains(normalizedHeader)) {
                    return c;
                }
            }
            return null;
        }
    }

    record ParsedRow(int excelRow, Map<Column, String> values) {
        String get(Column c) {
            String v = values.get(c);
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    private final CandidateService candidateService;
    private final CandidateRepository candidates;
    private final InterviewSlotRepository slots;
    private final TrackRepository tracks;
    private final TrackResolver trackResolver;

    public CandidateImportService(CandidateService candidateService, CandidateRepository candidates,
                                  InterviewSlotRepository slots, TrackRepository tracks, TrackResolver trackResolver) {
        this.candidateService = candidateService;
        this.candidates = candidates;
        this.slots = slots;
        this.tracks = tracks;
        this.trackResolver = trackResolver;
    }

    @Transactional
    public ImportReport importFile(InputStream file, boolean dryRun) {
        List<ParsedRow> rows = parse(file);
        TrackResolver.Lookup trackLookup = trackResolver.lookup();
        Set<Long> scheduled = slots.findCandidateIdsWithActiveSlot();

        List<ImportReport.RowError> errors = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();

        for (ParsedRow row : rows) {
            String code = row.get(Column.STUDENT_CODE) == null ? null : row.get(Column.STUDENT_CODE).toUpperCase(Locale.ROOT);
            List<String> problems = new ArrayList<>();
            if (code == null) {
                problems.add("Thiếu Mã SV");
            } else if (code.length() > 20) {
                problems.add("Mã SV dài quá 20 ký tự");
            } else if (!seenCodes.add(code)) {
                problems.add("Mã SV bị lặp trong file");
            }
            if (row.get(Column.FULL_NAME) == null) {
                problems.add("Thiếu họ tên");
            } else if (row.get(Column.FULL_NAME).length() > 100) {
                problems.add("Họ tên dài quá 100 ký tự");
            }
            Track track = null;
            if (row.get(Column.TRACK) == null) {
                problems.add("Thiếu ngạch");
            } else {
                track = trackLookup.find(row.get(Column.TRACK)).orElse(null);
                if (track == null) {
                    problems.add("Không nhận ra ngạch \"" + row.get(Column.TRACK) + "\"");
                }
            }
            String email = row.get(Column.EMAIL);
            if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 254)) {
                problems.add("Email không đúng định dạng");
            }
            if (row.get(Column.PHONE) != null && row.get(Column.PHONE).length() > 20) {
                problems.add("SĐT dài quá 20 ký tự");
            }
            if (row.get(Column.MAJOR) != null && row.get(Column.MAJOR).length() > 100) {
                problems.add("Chuyên ngành dài quá 100 ký tự");
            }
            if (lengthOver(row.get(Column.CV_LINK), 500) || lengthOver(row.get(Column.PORTFOLIO), 500)) {
                problems.add("Link dài quá 500 ký tự");
            }

            Candidate existing = code == null ? null : candidates.findByStudentCode(code).orElse(null);
            if (existing != null && track != null && !existing.getTrack().getId().equals(track.getId())
                    && scheduled.contains(existing.getId())) {
                problems.add("Hồ sơ đã được xếp lịch phỏng vấn ở ngạch " + existing.getTrack().getName() + ", không đổi ngạch được");
            }

            if (!problems.isEmpty()) {
                errors.add(new ImportReport.RowError(row.excelRow(), code, String.join("; ", problems)));
                continue;
            }
            pending.add(new Pending(existing, track, new CandidateRequest(code, row.get(Column.FULL_NAME), email,
                    row.get(Column.PHONE), row.get(Column.MAJOR), track.getId(),
                    row.get(Column.CV_LINK), row.get(Column.PORTFOLIO))));
        }

        int created = (int) pending.stream().filter(p -> p.existing() == null).count();
        if (!errors.isEmpty() || dryRun) {
            return new ImportReport(false, dryRun, rows.size(), created, pending.size() - created, 0, errors);
        }

        int updated = 0;
        int unchanged = 0;
        for (Pending p : pending) {
            if (p.existing() == null) {
                candidateService.create(p.request());
            } else if (candidateService.applyInfo(p.existing(), p.request(), p.track(), scheduled.contains(p.existing().getId()))) {
                candidates.save(p.existing());
                updated++;
            } else {
                unchanged++;
            }
        }
        return new ImportReport(true, false, rows.size(), created, updated, unchanged, List.of());
    }

    private record Pending(Candidate existing, Track track, CandidateRequest request) {
    }

    List<ParsedRow> parse(InputStream file) {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            int headerRow = -1;
            Map<Column, Integer> columns = new EnumMap<>(Column.class);
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<Column, Integer> found = new EnumMap<>(Column.class);
                for (Cell cell : row) {
                    Column col = Column.match(VietnameseText.normalize(formatter.formatCellValue(cell, evaluator)));
                    if (col != null) {
                        found.putIfAbsent(col, cell.getColumnIndex());
                    }
                }
                if (found.containsKey(Column.STUDENT_CODE) && found.containsKey(Column.FULL_NAME) && found.containsKey(Column.TRACK)) {
                    headerRow = r;
                    columns = found;
                    break;
                }
            }
            if (headerRow < 0) {
                throw new BusinessException("IMPORT_HEADER_NOT_FOUND",
                        "Không tìm thấy dòng tiêu đề. File cần có ít nhất các cột: Mã SV, Họ và tên, Ngạch.");
            }

            List<ParsedRow> rows = new ArrayList<>();
            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<Column, String> values = new EnumMap<>(Column.class);
                for (Map.Entry<Column, Integer> col : columns.entrySet()) {
                    Cell cell = row.getCell(col.getValue());
                    if (cell == null) {
                        continue;
                    }
                    boolean isLink = col.getKey() == Column.CV_LINK || col.getKey() == Column.PORTFOLIO;
                    String value = isLink && cell.getHyperlink() != null
                            ? cell.getHyperlink().getAddress()
                            : formatter.formatCellValue(cell, evaluator);
                    values.put(col.getKey(), value);
                }
                if (values.values().stream().allMatch(v -> v == null || v.isBlank())) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new BusinessException("IMPORT_TOO_LARGE", "File có quá " + MAX_ROWS + " dòng dữ liệu");
                }
                rows.add(new ParsedRow(r + 1, values));
            }
            if (rows.isEmpty()) {
                throw new BusinessException("IMPORT_EMPTY", "File không có dòng dữ liệu nào dưới dòng tiêu đề");
            }
            return rows;
        } catch (IOException | org.apache.poi.EmptyFileException | org.apache.poi.UnsupportedFileFormatException e) {
            throw new BusinessException("IMPORT_UNREADABLE", "Không đọc được file. Hãy dùng file Excel .xlsx hoặc .xls.");
        }
    }

    /** File mẫu: sheet nhập liệu có dropdown chọn ngạch, sheet danh sách ngạch, sheet hướng dẫn. */
    @Transactional(readOnly = true)
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("Ứng viên");
            Sheet trackSheet = wb.createSheet("Ngạch");
            Sheet guide = wb.createSheet("Hướng dẫn");

            CellStyle headerStyle = wb.createCellStyle();
            var bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            CellStyle textStyle = wb.createCellStyle();
            textStyle.setDataFormat(wb.createDataFormat().getFormat("@"));

            Row header = data.createRow(0);
            Column[] cols = Column.values();
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i].header);
                cell.setCellStyle(headerStyle);
                data.setColumnWidth(i, (cols[i] == Column.CV_LINK || cols[i] == Column.PORTFOLIO ? 40 : 22) * 256);
            }
            // Mã SV và SĐT để dạng chữ, tránh Excel nuốt số 0 ở đầu
            data.setDefaultColumnStyle(Column.STUDENT_CODE.ordinal(), textStyle);
            data.setDefaultColumnStyle(Column.PHONE.ordinal(), textStyle);
            data.createFreezePane(0, 1);

            List<Track> all = tracks.findAllByOrderBySortOrderAsc();
            Row th = trackSheet.createRow(0);
            th.createCell(0).setCellValue("Tên ngạch");
            th.createCell(1).setCellValue("Mã ngạch");
            th.getCell(0).setCellStyle(headerStyle);
            th.getCell(1).setCellStyle(headerStyle);
            for (int i = 0; i < all.size(); i++) {
                Row r = trackSheet.createRow(i + 1);
                r.createCell(0).setCellValue(all.get(i).getName());
                r.createCell(1).setCellValue(all.get(i).getCode());
            }
            trackSheet.setColumnWidth(0, 32 * 256);
            trackSheet.setColumnWidth(1, 16 * 256);

            DataValidationHelper dv = data.getDataValidationHelper();
            var constraint = dv.createFormulaListConstraint("'Ngạch'!$A$2:$A$" + (all.size() + 1));
            var validation = dv.createValidation(constraint,
                    new CellRangeAddressList(1, MAX_ROWS, Column.TRACK.ordinal(), Column.TRACK.ordinal()));
            validation.setShowErrorBox(true);
            data.addValidationData(validation);

            String[] lines = {
                    "Hướng dẫn nhập hồ sơ ứng viên",
                    "1. Mỗi dòng ở sheet \"Ứng viên\" là một hồ sơ. Bắt buộc: Mã SV, Họ và tên, Ngạch.",
                    "2. Chọn ngạch từ danh sách thả xuống (lấy từ sheet \"Ngạch\").",
                    "3. Link CV và Link Portfolio phải bắt đầu bằng http:// hoặc https://. Graphic Design và Game Design bắt buộc có Portfolio.",
                    "4. Nhập lại file có Mã SV đã tồn tại thì hệ thống cập nhật thông tin, giữ nguyên kết quả sàng lọc.",
                    "5. Có lỗi ở bất kỳ dòng nào thì không dòng nào được lưu; sửa theo báo lỗi rồi nhập lại."
            };
            for (int i = 0; i < lines.length; i++) {
                guide.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            guide.getRow(0).getCell(0).setCellStyle(headerStyle);
            guide.setColumnWidth(0, 120 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được file mẫu", e);
        }
    }

    private static boolean lengthOver(String s, int max) {
        return s != null && s.length() > max;
    }
}
