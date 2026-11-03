package vn.dxlab.recruitment.track;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.dxlab.recruitment.track.dto.CriterionDto;
import vn.dxlab.recruitment.track.dto.CriterionRequest;
import vn.dxlab.recruitment.track.dto.TrackDto;
import vn.dxlab.recruitment.track.dto.UpdateTrackRequest;

import java.util.List;

/** Ngạch và tiêu chí: mọi vai trò đều đọc được (giám khảo cần xem thang chấm). */
@RestController
public class TrackController {

    private final TrackService tracks;
    private final CriterionService criteria;

    public TrackController(TrackService tracks, CriterionService criteria) {
        this.tracks = tracks;
        this.criteria = criteria;
    }

    @GetMapping("/api/tracks")
    public List<TrackDto> tracks() {
        return tracks.list();
    }

    @PutMapping("/api/tracks/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public TrackDto updateTrack(@PathVariable Long id, @Valid @RequestBody UpdateTrackRequest req) {
        return tracks.update(id, req);
    }

    @GetMapping("/api/criteria")
    public List<CriterionDto> criteria(@RequestParam(required = false) Long trackId) {
        return criteria.list(trackId);
    }

    @PostMapping("/api/criteria")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public CriterionDto createCriterion(@Valid @RequestBody CriterionRequest req) {
        return criteria.create(req);
    }

    @PutMapping("/api/criteria/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public CriterionDto updateCriterion(@PathVariable Long id, @Valid @RequestBody CriterionRequest req) {
        return criteria.update(id, req);
    }

    @DeleteMapping("/api/criteria/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZER', 'LEAD')")
    public void deleteCriterion(@PathVariable Long id) {
        criteria.delete(id);
    }
}
