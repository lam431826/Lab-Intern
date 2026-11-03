package vn.dxlab.recruitment.track.dto;

import vn.dxlab.recruitment.track.Track;

public record TrackDto(Long id, String code, String name, int quota, int slotMin, int slotMax,
                       boolean requiresPortfolio, Long leadUserId, String leadName, int sortOrder) {

    public static TrackDto from(Track t) {
        return new TrackDto(t.getId(), t.getCode(), t.getName(), t.getQuota(), t.getSlotMin(), t.getSlotMax(),
                t.isRequiresPortfolio(),
                t.getLead() == null ? null : t.getLead().getId(),
                t.getLead() == null ? null : t.getLead().getFullName(),
                t.getSortOrder());
    }
}
