package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.dto.response.VenueResponse;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.VenueMapper;
import com.pulsepass.pass.repository.VenueRepository;
import com.pulsepass.pass.service.VenueService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final VenueMapper mapper;

    public VenueServiceImpl(VenueRepository venueRepository, VenueMapper mapper){
        this.venueRepository = venueRepository;
        this.mapper = mapper;
    }

    @Override
    public VenueResponse findByCode(String code){
        return venueRepository.findByCode(code)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found: " + code));
    }

    @Override
    public List<VenueResponse> findActiveVenues(){
        return venueRepository.findActiveVenues()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }


}
