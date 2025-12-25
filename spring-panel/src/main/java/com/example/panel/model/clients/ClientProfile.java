package com.example.panel.model.clients;

import java.util.List;

public record ClientProfile(
        ClientProfileHeader header,
        ClientProfileStats stats,
        List<ClientProfileTicket> tickets,
        ClientBlacklistInfo blacklist,
        Double averageRating
) {
}
