package com.example.panel.model.publicform;

import java.util.List;

public record PublicFormConfig(Long channelId,
                               String channelPublicId,
                               String channelName,
                               Integer schemaVersion,
                               boolean enabled,
                               boolean captchaEnabled,
                               int disabledStatus,
                               String successInstruction,
                               Integer responseEtaMinutes,
                               List<PublicFormQuestion> questions) {
}
