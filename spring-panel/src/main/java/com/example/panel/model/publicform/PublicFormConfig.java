package com.example.panel.model.publicform;

import java.util.List;

public record PublicFormConfig(Long channelId,
                               String channelPublicId,
                               String channelName,
                               List<PublicFormQuestion> questions) {
}
