package vn.dxlab.recruitment.audit;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.security.AppUserPrincipal;

import java.util.List;
import java.util.Objects;

/** Ghi nhật ký thay đổi. Chạy trong transaction của thao tác gốc: thao tác rollback thì nhật ký cũng rollback. */
@Service
public class StatusHistoryService {

    private final StatusHistoryRepository history;

    public StatusHistoryService(StatusHistoryRepository history) {
        this.history = history;
    }

    /** Bỏ qua nếu giá trị không đổi. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, Long entityId, String field, Object oldValue, Object newValue, String note) {
        String oldText = oldValue == null ? null : oldValue.toString();
        String newText = newValue == null ? null : newValue.toString();
        if (Objects.equals(oldText, newText)) {
            return;
        }
        StatusHistory h = new StatusHistory();
        h.setEntityType(entityType);
        h.setEntityId(entityId);
        h.setField(field);
        h.setOldValue(oldText);
        h.setNewValue(newText);
        h.setNote(note);
        h.setChangedBy(currentUserId());
        history.save(h);
    }

    @Transactional(readOnly = true)
    public List<StatusHistory> of(String entityType, Long entityId) {
        return history.findByEntityTypeAndEntityIdOrderByChangedAtAsc(entityType, entityId);
    }

    private static Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserPrincipal p ? p.id() : null;
    }
}
