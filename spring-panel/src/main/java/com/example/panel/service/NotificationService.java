package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.model.notification.NotificationSummary;
import com.example.panel.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> findForUser(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        return notificationRepository.findByUserIdentityOrderByCreatedAtDesc(identity).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationSummary summary(String userIdentity) {
        String identity = normalizeIdentity(userIdentity);
        long unread = notificationRepository.countByUserIdentityAndIsReadFalse(identity);
        return new NotificationSummary(unread);
    }

    public void markAsRead(String userIdentity, Long id) {
        String identity = normalizeIdentity(userIdentity);
        notificationRepository.findByIdAndUserIdentity(id, identity).ifPresent(notification -> {
            notification.setIsRead(Boolean.TRUE);
            notificationRepository.save(notification);
        });
    }

    private NotificationDto toDto(Notification entity) {
        return new NotificationDto(
                entity.getId(),
                entity.getText(),
                entity.getUrl(),
                Boolean.TRUE.equals(entity.getIsRead()),
                entity.getCreatedAt()
        );
    }

    private String normalizeIdentity(String userIdentity) {
        return StringUtils.hasText(userIdentity) ? userIdentity : "all";
    }
}
