package vn.dxlab.recruitment.settings;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsDto get() {
        return settings.get();
    }

    /** Giảng viên được sửa trọng số và điểm sàn (Kế hoạch phần mềm, mục III). */
    @PutMapping
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LECTURER')")
    public SettingsDto update(@Valid @RequestBody SettingsDto req) {
        return settings.update(req);
    }
}
