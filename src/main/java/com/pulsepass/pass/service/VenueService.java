package com.pulsepass.pass.service;

import com.pulsepass.pass.dto.response.VenueResponse;

import java.util.List;

public interface VenueService {

    VenueResponse findByCode(String code);

    List<VenueResponse> findActiveVenues();
}
