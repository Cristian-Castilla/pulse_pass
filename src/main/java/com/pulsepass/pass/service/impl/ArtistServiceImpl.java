package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.dto.response.ArtistResponse;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.ArtistMapper;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.service.ArtistService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final ArtistMapper mapper;

    public ArtistServiceImpl (ArtistRepository artistRepository, ArtistMapper mapper){
        this.artistRepository = artistRepository;
        this.mapper = mapper;
    }

    @Override
    public ArtistResponse findById(Long id){
        return artistRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Artists doesnt exist: " + id));
    }

    @Override
    public ArtistResponse findByStageName(String stageName){
        return artistRepository.findByStageName(stageName)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Artist with stage name " + stageName + " doesn't exist."));
    }

    @Override
    public List<ArtistResponse> findActiveArtists(){
        return artistRepository.findByActiveTrueOrderByStageNameAsc()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
