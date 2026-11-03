package vn.dxlab.recruitment.candidate;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.support.ApiIntegrationTest;
import vn.dxlab.recruitment.user.AppUser;
import vn.dxlab.recruitment.user.Role;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CandidateImportIT extends ApiIntegrationTest {

    @Autowired CandidateRepository candidates;

    AppUser organizer;

    @BeforeEach
    void setUp() {
        organizer = createLinkedUser("user_imp", "imp@dxlab.vn", Role.ORGANIZER);
    }

    /** Tiêu đề giống tab "Sàng lọc CV" cũ, có dòng tiêu đề phụ phía trên và tên ngạch kiểu cũ. */
    @Test
    void importsLegacyStyleSheetWithHyperlinks() throws IOException {
        byte[] file = workbook(new String[]{"DANH SÁCH ỨNG VIÊN ĐỢT 08/2026"},
                new String[]{"STT", "Mã sv", "Tên ứng viên", "Chuyên ngành", "Vị trí", "Link CV", "Email"},
                new Object[]{1, "he180001", "Nguyễn Văn An", "Kỹ thuật phần mềm", "Software Engineer", link("https://drive.google.com/an"), "An@Gmail.com"},
                new Object[]{2, "HE180002", "Đặng Thị Bình", "Thiết kế đồ họa", "Graphic\nDesigners", "https://drive.google.com/binh", null},
                new Object[]{null, null, null, null, null, null, null},
                new Object[]{3, "HE180003", "Lý Minh", "An toàn thông tin", "IA Intern", null, null});

        assertThat(upload(file, false)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.applied").isEqualTo(true);
                    json.assertThat().extractingPath("$.totalRows").isEqualTo(3);
                    json.assertThat().extractingPath("$.created").isEqualTo(3);
                });

        Candidate an = candidates.findByStudentCode("HE180001").orElseThrow();
        assertThat(an.getTrack().getCode()).isEqualTo("SE");
        assertThat(an.getCvLink()).isEqualTo("https://drive.google.com/an");
        assertThat(an.getScreeningStatus()).isEqualTo(ScreeningStatus.PENDING);
        assertThat(candidates.findByStudentCode("HE180002").orElseThrow().getTrack().getCode()).isEqualTo("GRAPHIC");
        assertThat(candidates.findByStudentCode("HE180003").orElseThrow().getTrack().getCode()).isEqualTo("IA");
    }

    @Test
    void reimportUpdatesInfoButKeepsScreeningResult() throws IOException {
        String[] header = {"Mã SV", "Họ và tên", "Ngạch", "Email"};
        upload(workbook(null, header, new Object[]{"HE190001", "Trần An", "AI", null}), false);
        Candidate c = candidates.findByStudentCode("HE190001").orElseThrow();
        c.setScreeningStatus(ScreeningStatus.PASSED);
        candidates.saveAndFlush(c);

        assertThat(upload(workbook(null, header,
                new Object[]{"HE190001", "Trần Văn An", "AI", "an@dxlab.vn"},
                new Object[]{"HE190002", "Mới", "AI", null}), false))
                .hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.created").isEqualTo(1);
                    json.assertThat().extractingPath("$.updated").isEqualTo(1);
                });

        Candidate after = candidates.findByStudentCode("HE190001").orElseThrow();
        assertThat(after.getFullName()).isEqualTo("Trần Văn An");
        assertThat(after.getEmail()).isEqualTo("an@dxlab.vn");
        assertThat(after.getScreeningStatus()).isEqualTo(ScreeningStatus.PASSED);
    }

    @Test
    void reportsEveryBadRowAndSavesNothing() throws IOException {
        byte[] file = workbook(null, new String[]{"Mã SV", "Họ và tên", "Ngạch", "Email"},
                new Object[]{"HE200001", "Đúng", "SE", "ok@dxlab.vn"},
                new Object[]{"HE200002", null, "SE", null},
                new Object[]{"HE200003", "Sai ngạch", "Marketing", null},
                new Object[]{"HE200001", "Lặp mã", "SE", null},
                new Object[]{"HE200004", "Sai email", "AI", "khong-phai-email"});

        assertThat(upload(file, false)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.applied").isEqualTo(false);
                    json.assertThat().extractingPath("$.errors[*].row").asArray().containsExactly(3, 4, 5, 6);
                    json.assertThat().extractingPath("$.errors[1].message").asString().contains("Marketing");
                });
        assertThat(candidates.findByStudentCode("HE200001")).isEmpty();
    }

    @Test
    void dryRunDoesNotSave() throws IOException {
        byte[] file = workbook(null, new String[]{"MSSV", "Họ tên", "Khối"}, new Object[]{"HE210001", "Thử", "Game Design"});

        assertThat(upload(file, true)).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    json.assertThat().extractingPath("$.dryRun").isEqualTo(true);
                    json.assertThat().extractingPath("$.created").isEqualTo(1);
                });
        assertThat(candidates.findByStudentCode("HE210001")).isEmpty();
    }

    @Test
    void rejectsFileWithoutRequiredHeaders() throws IOException {
        assertThat(upload(workbook(null, new String[]{"Tên", "Email"}, new Object[]{"A", "a@b.vn"}), false))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_HEADER_NOT_FOUND");
        assertThat(upload("không phải excel".getBytes(), false))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("IMPORT_UNREADABLE");
    }

    @Test
    void templateCanBeImportedBack() throws IOException {
        MvcTestResult result = mvc.get().uri("/api/candidates/import-template")
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer)).exchange();
        assertThat(result).hasStatusOk();
        byte[] template = result.getResponse().getContentAsByteArray();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row r = wb.getSheet("Ứng viên").createRow(1);
            r.createCell(0).setCellValue("HE220001");
            r.createCell(1).setCellValue("Từ file mẫu");
            r.createCell(5).setCellValue("Game Developer");
            wb.write(out);
            assertThat(upload(out.toByteArray(), false)).hasStatusOk()
                    .bodyJson().extractingPath("$.created").isEqualTo(1);
        }
        assertThat(candidates.findByStudentCode("HE220001").orElseThrow().getTrack().getCode()).isEqualTo("GAME_DEV");
    }

    @Test
    void onlyOrganizerCanImport() throws IOException {
        AppUser lead = createLinkedUser("user_imp_lead", "imp.lead@dxlab.vn", Role.LEAD);
        byte[] file = workbook(null, new String[]{"Mã SV", "Họ và tên", "Ngạch"}, new Object[]{"HE230001", "X", "AI"});

        assertThat(mvc.post().uri("/api/candidates/import").multipart()
                .file(new MockMultipartFile("file", "ds.xlsx", null, file))
                .header(HttpHeaders.AUTHORIZATION, bearer(lead)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult upload(byte[] content, boolean dryRun) {
        return mvc.post().uri("/api/candidates/import?dryRun=" + dryRun).multipart()
                .file(new MockMultipartFile("file", "ung-vien.xlsx", null, content))
                .header(HttpHeaders.AUTHORIZATION, bearer(organizer))
                .exchange();
    }

    private record Link(String url) {
    }

    private static Link link(String url) {
        return new Link(url);
    }

    /** {@code title} != null thì thêm một dòng tiêu đề phụ phía trên dòng tiêu đề cột. */
    private static byte[] workbook(String[] title, String[] header, Object[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sàng lọc CV");
            int r = 0;
            if (title != null) {
                sheet.createRow(r++).createCell(0).setCellValue(title[0]);
            }
            Row h = sheet.createRow(r++);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            for (Object[] values : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.length; i++) {
                    Object v = values[i];
                    if (v == null) {
                        continue;
                    }
                    Cell cell = row.createCell(i);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v instanceof Link l) {
                        cell.setCellValue("Xem CV");
                        var hyperlink = wb.getCreationHelper().createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(l.url());
                        cell.setHyperlink(hyperlink);
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
