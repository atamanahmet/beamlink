package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.PeerListVersion;
import com.atamanahmet.beamlink.nexus.repository.PeerListVersionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PeerListService {

    private final PeerListVersionRepository peerListVersionRepository;

    private PeerListVersion version;


    @PostConstruct
    @Transactional
    public void init() {
        // Fetch the existing row if it exists
        version = peerListVersionRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    // If table is empty, create the initial version
                    PeerListVersion initial = new PeerListVersion();
                    initial.setVersion(1L);
                    return peerListVersionRepository.save(initial);
                });
    }

    @Transactional
    public long incrementVersion() {
        version.setVersion(version.getVersion() + 1);
        version = peerListVersionRepository.save(version);
        return version.getVersion();
    }

    public long getCurrentVersion() {
        return version.getVersion();
    }


    public boolean isPeerListOutdated(long clientVersion) {
        return clientVersion < getCurrentVersion();
    }
}

