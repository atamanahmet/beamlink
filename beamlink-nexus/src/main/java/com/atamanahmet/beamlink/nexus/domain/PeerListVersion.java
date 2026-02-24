package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.repository.PeerListVersionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "peer_list_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PeerListVersion {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private Long version =1L;




}
