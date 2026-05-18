package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicFormSubmissionFlowServiceTest {

    @Test
    void createSessionReturnsCachedIdempotentSessionWithoutPersisting() {
        PublicFormChannelService publicFormChannelService = mock(PublicFormChannelService.class);
        PublicFormAntiAbuseService publicFormAntiAbuseService = mock(PublicFormAntiAbuseService.class);
        PublicFormSubmissionPolicyService publicFormSubmissionPolicyService = mock(PublicFormSubmissionPolicyService.class);
        PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService = mock(PublicFormSubmissionPersistenceService.class);

        PublicFormSubmissionFlowService service = new PublicFormSubmissionFlowService(
                publicFormChannelService,
                publicFormAntiAbuseService,
                publicFormSubmissionPolicyService,
                publicFormSubmissionPersistenceService
        );

        Channel channel = new Channel();
        channel.setId(14L);
        channel.setPublicId("web-main");
        channel.setActive(true);

        PublicFormConfig config = new PublicFormConfig(
                14L,
                "web-main",
                "Support Web",
                1,
                true,
                false,
                200,
                "Instruction",
                null,
                List.of(new PublicFormQuestion("message", "Сообщение", "textarea", 1, Map.of()))
        );
        PublicFormSubmission submission = new PublicFormSubmission(
                "Нужна помощь",
                "Анна",
                "+79991234567",
                "anna",
                null,
                Map.of("message", "Нужна помощь"),
                "req-1"
        );
        PublicFormSubmissionPolicyService.PreparedSubmission preparedSubmission =
                new PublicFormSubmissionPolicyService.PreparedSubmission(
                        submission,
                        submission.answers(),
                        "Нужна помощь",
                        "Анна"
                );
        PublicFormAntiAbuseService.SubmissionFingerprint fingerprint =
                new PublicFormAntiAbuseService.SubmissionFingerprint("req-1", "hash-1");
        PublicFormSessionDto cached = new PublicFormSessionDto(
                "token-1",
                "web-1",
                14L,
                "web-main",
                "Анна",
                "+79991234567",
                "anna",
                OffsetDateTime.now()
        );

        when(publicFormChannelService.resolveChannel("web-main")).thenReturn(Optional.of(channel));
        when(publicFormChannelService.loadConfigRaw("web-main")).thenReturn(Optional.of(config));
        when(publicFormSubmissionPolicyService.prepareSubmission(config, submission)).thenReturn(preparedSubmission);
        when(publicFormAntiAbuseService.prepareSubmissionFingerprint(submission, submission.answers())).thenReturn(fingerprint);
        when(publicFormAntiAbuseService.findIdempotentSession(channel, "ip+fp", fingerprint)).thenReturn(Optional.of(cached));

        PublicFormSessionDto result = service.createSession("web-main", submission, "ip+fp");

        assertThat(result).isSameAs(cached);
        verify(publicFormSubmissionPersistenceService, never()).persistSubmission(channel, preparedSubmission, "ip+fp");
        verify(publicFormAntiAbuseService, never()).cacheIdempotentSession(channel, "ip+fp", fingerprint, cached);
    }

    @Test
    void createSessionPersistsAndCachesWhenNoIdempotentHitExists() {
        PublicFormChannelService publicFormChannelService = mock(PublicFormChannelService.class);
        PublicFormAntiAbuseService publicFormAntiAbuseService = mock(PublicFormAntiAbuseService.class);
        PublicFormSubmissionPolicyService publicFormSubmissionPolicyService = mock(PublicFormSubmissionPolicyService.class);
        PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService = mock(PublicFormSubmissionPersistenceService.class);

        PublicFormSubmissionFlowService service = new PublicFormSubmissionFlowService(
                publicFormChannelService,
                publicFormAntiAbuseService,
                publicFormSubmissionPolicyService,
                publicFormSubmissionPersistenceService
        );

        Channel channel = new Channel();
        channel.setId(21L);
        channel.setPublicId("web-save");
        channel.setActive(true);

        PublicFormConfig config = new PublicFormConfig(
                21L,
                "web-save",
                "Support Web",
                1,
                true,
                false,
                200,
                "Instruction",
                null,
                List.of()
        );
        PublicFormSubmission submission = new PublicFormSubmission(
                "Нужна помощь",
                "Анна",
                "+79991234567",
                "anna",
                null,
                Map.of("business", "БлинБери"),
                "req-2"
        );
        PublicFormSubmissionPolicyService.PreparedSubmission preparedSubmission =
                new PublicFormSubmissionPolicyService.PreparedSubmission(
                        submission,
                        submission.answers(),
                        "Ответы формы",
                        "Анна"
                );
        PublicFormAntiAbuseService.SubmissionFingerprint fingerprint =
                new PublicFormAntiAbuseService.SubmissionFingerprint("req-2", "hash-2");
        PublicFormSessionDto created = new PublicFormSessionDto(
                "token-2",
                "web-2",
                21L,
                "web-save",
                "Анна",
                "+79991234567",
                "anna",
                OffsetDateTime.now()
        );

        when(publicFormChannelService.resolveChannel("web-save")).thenReturn(Optional.of(channel));
        when(publicFormChannelService.loadConfigRaw("web-save")).thenReturn(Optional.of(config));
        when(publicFormSubmissionPolicyService.prepareSubmission(config, submission)).thenReturn(preparedSubmission);
        when(publicFormAntiAbuseService.prepareSubmissionFingerprint(submission, submission.answers())).thenReturn(fingerprint);
        when(publicFormAntiAbuseService.findIdempotentSession(channel, "ip+fp-2", fingerprint)).thenReturn(Optional.empty());
        doNothing().when(publicFormAntiAbuseService).enforceRateLimit(channel, "ip+fp-2");
        when(publicFormSubmissionPersistenceService.persistSubmission(channel, preparedSubmission, "ip+fp-2")).thenReturn(created);

        PublicFormSessionDto result = service.createSession("web-save", submission, "ip+fp-2");

        assertThat(result).isSameAs(created);
        verify(publicFormAntiAbuseService).enforceRateLimit(channel, "ip+fp-2");
        verify(publicFormSubmissionPersistenceService).persistSubmission(channel, preparedSubmission, "ip+fp-2");
        verify(publicFormAntiAbuseService).cacheIdempotentSession(channel, "ip+fp-2", fingerprint, created);
    }

    @Test
    void createSessionRejectsDisabledChannelBeforeSubmissionPreparation() {
        PublicFormChannelService publicFormChannelService = mock(PublicFormChannelService.class);
        PublicFormAntiAbuseService publicFormAntiAbuseService = mock(PublicFormAntiAbuseService.class);
        PublicFormSubmissionPolicyService publicFormSubmissionPolicyService = mock(PublicFormSubmissionPolicyService.class);
        PublicFormSubmissionPersistenceService publicFormSubmissionPersistenceService = mock(PublicFormSubmissionPersistenceService.class);

        PublicFormSubmissionFlowService service = new PublicFormSubmissionFlowService(
                publicFormChannelService,
                publicFormAntiAbuseService,
                publicFormSubmissionPolicyService,
                publicFormSubmissionPersistenceService
        );

        Channel channel = new Channel();
        channel.setPublicId("web-disabled");
        channel.setActive(false);

        PublicFormSubmission submission = new PublicFormSubmission(
                "Сообщение",
                "Анна",
                null,
                null,
                null,
                Map.of(),
                "req-3"
        );

        when(publicFormChannelService.resolveChannel("web-disabled")).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> service.createSession("web-disabled", submission, "ip+fp-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Форма канала временно отключена");

        verify(publicFormChannelService, never()).loadConfigRaw("web-disabled");
        verifyNoInteractions(publicFormSubmissionPolicyService, publicFormSubmissionPersistenceService, publicFormAntiAbuseService);
    }
}
