package vn.dxlab.recruitment.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.audit.StatusHistoryService;
import vn.dxlab.recruitment.common.error.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Transactional
public class SettingsService {

    private final ScoringSettingsRepository repository;
    private final StatusHistoryService history;

    public SettingsService(ScoringSettingsRepository repository, StatusHistoryService history) {
        this.repository = repository;
        this.history = history;
    }

    /** Dùng trong service khác (tính điểm, SLA, xếp lịch). */
    @Transactional(readOnly = true)
    public ScoringSettings current() {
        return repository.findById(ScoringSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Thiếu dòng scoring_settings (migration V8)"));
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        return SettingsDto.from(current());
    }

    public SettingsDto update(SettingsDto req) {
        BigDecimal general = req.weightGeneral().setScale(2, RoundingMode.HALF_UP);
        BigDecimal professional = req.weightProfessional().setScale(2, RoundingMode.HALF_UP);
        if (general.add(professional).compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessException("INVALID_WEIGHTS", "Trọng số điểm chung và điểm chuyên môn phải cộng lại bằng 1");
        }
        ScoringSettings s = current();
        history.record("SETTINGS", 1L, "weights", s.getWeightGeneral() + "/" + s.getWeightProfessional(),
                general + "/" + professional, null);
        history.record("SETTINGS", 1L, "passThreshold", s.getPassThreshold(),
                req.passThreshold().setScale(2, RoundingMode.HALF_UP), null);
        s.setWeightGeneral(general);
        s.setWeightProfessional(professional);
        s.setPassThreshold(req.passThreshold().setScale(2, RoundingMode.HALF_UP));
        s.setSlaHours(req.slaHours());
        s.setSlotMinutes(req.slotMinutes());
        return SettingsDto.from(s);
    }
}
