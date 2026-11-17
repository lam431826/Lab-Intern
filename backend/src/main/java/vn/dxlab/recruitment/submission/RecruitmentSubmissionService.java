package vn.dxlab.recruitment.submission;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.common.error.BusinessException;
import vn.dxlab.recruitment.common.error.NotFoundException;
import vn.dxlab.recruitment.security.AuthorizationService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.submission.dto.CreateSubmissionRequest;
import vn.dxlab.recruitment.submission.dto.MarkSentRequest;
import vn.dxlab.recruitment.submission.dto.SubmissionDto;
import vn.dxlab.recruitment.submission.dto.SubmissionItemDto;
import vn.dxlab.recruitment.submission.dto.SubmissionItemRequest;
import vn.dxlab.recruitment.submission.dto.UpdateSubmissionRequest;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.AppUserRepository;
import vn.dxlab.recruitment.user.Role;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Transactional
public class RecruitmentSubmissionService {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 2000;
    private static final String HISTORY_TYPE = "RECRUITMENT_SUBMISSION";

    private final RecruitmentSubmissionRepository submissions;
    private final RecruitmentSubmissionItemRepository items;
    private final RecruitmentSubmissionFileRepository files;
    private final TrackRepository tracks;
    private final AppUserRepository users;
    private final CurrentUser currentUser;
    private final AuthorizationService authz;
    private final StatusHistoryService history;

    public RecruitmentSubmissionService(RecruitmentSubmissionRepository submissions,
                                        RecruitmentSubmissionItemRepository items,
                                        RecruitmentSubmissionFileRepository files,
                                        TrackRepository tracks,
                                        AppUserRepository users,
                                        CurrentUser currentUser,
                                        AuthorizationService authz,
                                        StatusHistoryService history) {
        this.submissions = submissions;
        this.items = items;
        this.files = files;
        this.tracks = tracks;
        this.users = users;
        this.currentUser = currentUser;
        this.authz = authz;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public List<SubmissionDto> list(String termCode, Long trackId, SubmissionStatus status,
                                    Instant from, Instant to, String q) {
        String normalizedTerm = normalizeTerm(termCode);
        String needle = blankToNull(q) == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return submissions.findAllByOrderByUpdatedAtDesc().stream()
                .filter(s -> normalizedTerm == null || s.getTermCode().equalsIgnoreCase(normalizedTerm))
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> from == null || (s.getSentAt() != null && !s.getSentAt().isBefore(from)))
                .filter(s -> to == null || (s.getSentAt() != null && !s.getSentAt().isAfter(to)))
                .filter(s -> trackId == null || s.getItems().stream().anyMatch(i -> i.getTrack().getId().equals(trackId)))
                .filter(s -> needle == null || matches(s, needle))
                .map(SubmissionDto::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDto get(Long id) {
        return SubmissionDto.detail(detailed(id));
    }

    public SubmissionDto create(CreateSubmissionRequest req) {
        RecruitmentSubmission s = new RecruitmentSubmission();
        s.setTermCode(requiredTerm(req.termCode()));
        s.setTitle(req.title().trim());
        s.setRecipient(blankToNull(req.recipient()));
        s.setExternalReference(blankToNull(req.externalReference()));
        s.setNote(blankToNull(req.note()));
        if (req.replacesSubmissionId() != null) {
            RecruitmentSubmission replaced = find(req.replacesSubmissionId());
            if (replaced.getStatus() != SubmissionStatus.SENT) {
                throw new BusinessException("INVALID_REPLACEMENT", "Chỉ bản đã gửi mới được tạo bản bổ sung hoặc thay thế");
            }
            s.setReplaces(replaced);
        }
        submissions.saveAndFlush(s);
        history.record(HISTORY_TYPE, s.getId(), "status", null, SubmissionStatus.DRAFT, "Tạo bản nháp");
        return SubmissionDto.detail(s);
    }

    public SubmissionDto update(Long id, UpdateSubmissionRequest req) {
        RecruitmentSubmission s = lock(id);
        requireDraft(s);
        checkVersion(s.getVersion(), req.version());
        s.setTermCode(requiredTerm(req.termCode()));
        s.setTitle(req.title().trim());
        s.setRecipient(blankToNull(req.recipient()));
        s.setExternalReference(blankToNull(req.externalReference()));
        s.setNote(blankToNull(req.note()));
        submissions.saveAndFlush(s);
        return SubmissionDto.detail(detailed(id));
    }

    public void delete(Long id) {
        RecruitmentSubmission s = lock(id);
        requireDraft(s);
        submissions.delete(s);
        submissions.flush();
    }

    public SubmissionItemDto addItem(Long submissionId, SubmissionItemRequest req) {
        RecruitmentSubmission s = lock(submissionId);
        requireDraft(s);
        Track track = track(req.trackId());
        requireCanEditTrack(track.getId());
        RecruitmentSubmissionItem item = new RecruitmentSubmissionItem();
        item.setSubmission(s);
        applyItem(item, track, req);
        // Đồng bộ cả hai chiều quan hệ: markSent đọc s.getItems() trong cùng persistence context
        s.getItems().add(item);
        items.saveAndFlush(item);
        return SubmissionItemDto.from(item);
    }

    public SubmissionItemDto updateItem(Long itemId, SubmissionItemRequest req) {
        RecruitmentSubmissionItem item = lockItem(itemId);
        requireDraft(item.getSubmission());
        if (req.version() == null || req.version() != item.getVersion()) {
            throw stale();
        }
        requireCanEditTrack(item.getTrack().getId());
        Track track = track(req.trackId());
        requireCanEditTrack(track.getId());
        applyItem(item, track, req);
        items.saveAndFlush(item);
        return SubmissionItemDto.from(item);
    }

    public void deleteItem(Long itemId) {
        RecruitmentSubmissionItem item = lockItem(itemId);
        requireDraft(item.getSubmission());
        requireCanEditTrack(item.getTrack().getId());
        // Gỡ khỏi danh sách của bản gửi (orphanRemoval xóa dòng); nếu chỉ delete item thì
        // cascade ALL từ danh sách còn giữ item có thể khiến Hibernate bỏ qua lệnh xóa
        item.getSubmission().getItems().remove(item);
        items.delete(item);
        items.flush();
    }

    public SubmissionItemDto uploadFile(Long itemId, MultipartFile upload, Integer version) {
        RecruitmentSubmissionItem item = lockItem(itemId);
        requireDraft(item.getSubmission());
        requireCanEditTrack(item.getTrack().getId());
        if (version == null || version != item.getVersion()) {
            throw stale();
        }
        ValidFile valid = validateFile(upload);
        files.findByItemId(itemId).ifPresent(files::delete);
        files.flush();

        RecruitmentSubmissionFile stored = new RecruitmentSubmissionFile();
        stored.setItem(item);
        stored.setData(valid.data());
        files.save(stored);
        item.setFileName(valid.fileName());
        item.setFileMimeType(valid.mimeType());
        item.setFileSize((long) valid.data().length);
        item.setFileSha256(sha256(valid.data()));
        items.saveAndFlush(item);
        return SubmissionItemDto.from(item);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadFile(Long itemId) {
        RecruitmentSubmissionItem item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("vị trí tuyển dụng", itemId));
        if (!item.hasFile()) {
            throw new NotFoundException("file JD của vị trí", itemId);
        }
        RecruitmentSubmissionFile file = files.findByItemId(itemId)
                .orElseThrow(() -> new NotFoundException("file JD của vị trí", itemId));
        return new FileDownload(item.getFileName(), item.getFileMimeType(), item.getFileSize(),
                item.getFileSha256(), file.getData());
    }

    public SubmissionDto markSent(Long id, MarkSentRequest req) {
        RecruitmentSubmission s = lock(id);
        requireDraft(s);
        checkVersion(s.getVersion(), req.version());
        if (req.sentAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new BusinessException("INVALID_SENT_AT", "Thời điểm gửi không được nằm trong tương lai");
        }
        if (s.getItems().isEmpty()) {
            throw new BusinessException("SUBMISSION_EMPTY", "Phải có ít nhất một vị trí tuyển dụng trước khi đánh dấu đã gửi");
        }
        for (RecruitmentSubmissionItem item : s.getItems()) {
            if (blankToNull(item.getJdContent()) == null && !item.hasFile()) {
                throw new BusinessException("JD_REQUIRED", "Vị trí " + item.getPositionTitle() + " chưa có nội dung hoặc file JD");
            }
            // Chốt snapshot đúng tại thời điểm gửi, không phụ thuộc thay đổi tên ngạch về sau.
            item.setTrackCodeSnapshot(item.getTrack().getCode());
            item.setTrackNameSnapshot(item.getTrack().getName());
        }
        s.setRecipient(req.recipient().trim());
        s.setExternalReference(blankToNull(req.externalReference()));
        s.setSentAt(req.sentAt());
        s.setSubmittedBy(users.getReferenceById(currentUser.id()));
        s.setStatus(SubmissionStatus.SENT);
        submissions.saveAndFlush(s);
        history.record(HISTORY_TYPE, id, "status", SubmissionStatus.DRAFT, SubmissionStatus.SENT,
                "Đã gửi cho " + s.getRecipient());
        return SubmissionDto.detail(detailed(id));
    }

    private static boolean matches(RecruitmentSubmission s, String needle) {
        return contains(s.getTitle(), needle) || contains(s.getExternalReference(), needle)
                || contains(s.getRecipient(), needle)
                || s.getItems().stream().anyMatch(i -> contains(i.getPositionTitle(), needle)
                || contains(i.getTrackCodeSnapshot(), needle) || contains(i.getTrackNameSnapshot(), needle));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void applyItem(RecruitmentSubmissionItem item, Track track, SubmissionItemRequest req) {
        item.setTrack(track);
        item.setTrackCodeSnapshot(track.getCode());
        item.setTrackNameSnapshot(track.getName());
        item.setPositionTitle(req.positionTitle().trim());
        item.setQuota(req.quota());
        item.setJdContent(blankToNull(req.jdContent()));
        item.setNote(blankToNull(req.note()));
        item.setSortOrder(req.sortOrder());
    }

    private void requireCanEditTrack(Long trackId) {
        if (currentUser.hasRole(Role.ORGANIZER)) {
            return;
        }
        if (currentUser.hasRole(Role.LEAD) && authz.isLeadOf(trackId)) {
            return;
        }
        throw new AccessDeniedException("Chỉ Người tổ chức hoặc Lead của đúng ngạch được sửa JD này");
    }

    private static ValidFile validateFile(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessException("INVALID_JD_FILE", "File JD đang trống");
        }
        if (upload.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("JD_FILE_TOO_LARGE", "File JD tối đa 5 MB");
        }
        String name = safeFileName(upload.getOriginalFilename());
        try {
            byte[] data = upload.getBytes();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".pdf") && isPdf(data)) {
                return new ValidFile(name, "application/pdf", data);
            }
            if (lower.endsWith(".docx") && isDocx(data)) {
                return new ValidFile(name, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", data);
            }
            throw new BusinessException("INVALID_JD_FILE", "Chỉ nhận file PDF hoặc DOCX hợp lệ");
        } catch (IOException e) {
            throw new BusinessException("INVALID_JD_FILE", "Không đọc được file JD");
        }
    }

    private static boolean isPdf(byte[] data) {
        return data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D'
                && data[3] == 'F' && data[4] == '-';
    }

    private static boolean isDocx(byte[] data) {
        if (data.length < 4 || data[0] != 'P' || data[1] != 'K') {
            return false;
        }
        boolean contentTypes = false;
        boolean document = false;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_DOCX_ENTRIES) {
                    return false;
                }
                String name = entry.getName();
                contentTypes |= "[Content_Types].xml".equals(name);
                document |= "word/document.xml".equals(name);
                if (contentTypes && document) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String safeFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")) {
            throw new BusinessException("INVALID_JD_FILE", "Tên file JD không hợp lệ");
        }
        return name;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", e);
        }
    }

    private RecruitmentSubmission detailed(Long id) {
        return submissions.findDetailedById(id).orElseThrow(() -> new NotFoundException("lần gửi tuyển dụng", id));
    }

    private RecruitmentSubmission find(Long id) {
        return submissions.findById(id).orElseThrow(() -> new NotFoundException("lần gửi tuyển dụng", id));
    }

    private RecruitmentSubmission lock(Long id) {
        return submissions.lockById(id).orElseThrow(() -> new NotFoundException("lần gửi tuyển dụng", id));
    }

    private RecruitmentSubmissionItem lockItem(Long id) {
        return items.lockById(id).orElseThrow(() -> new NotFoundException("vị trí tuyển dụng", id));
    }

    private Track track(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("ngạch", id));
    }

    private static void requireDraft(RecruitmentSubmission s) {
        if (s.getStatus() != SubmissionStatus.DRAFT) {
            throw BusinessException.conflict("SUBMISSION_LOCKED", "Bản đã gửi bị khóa. Hãy tạo bản bổ sung hoặc thay thế mới.");
        }
    }

    private static void checkVersion(int actual, Integer supplied) {
        if (supplied == null || supplied != actual) {
            throw stale();
        }
    }

    private static BusinessException stale() {
        return BusinessException.conflict("STALE_DATA", "Dữ liệu vừa được người khác cập nhật. Tải lại trang rồi thử lại.");
    }

    private static String requiredTerm(String value) {
        String term = normalizeTerm(value);
        if (term == null) {
            throw new BusinessException("VALIDATION_FAILED", "Mã kỳ không được để trống");
        }
        return term;
    }

    private static String normalizeTerm(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidFile(String fileName, String mimeType, byte[] data) {
    }

    public record FileDownload(String fileName, String mimeType, long size, String sha256, byte[] data) {
    }
}
