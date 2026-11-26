package com.example.panel.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Coalesced current-state push, two readers, no unbounded per-event queue or JDBC during network send. */
@Service
public class NotificationPushService {
    private final ObjectProvider<NotificationSnapshotReader> readers;
    private final ObjectProvider<UiEventStreamService> streams;
    private final ConcurrentHashMap<UiEventRecipient,AtomicBoolean> pending=new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor executor=new ScheduledThreadPoolExecutor(2,r->{
        var thread=new Thread(r,"notification-push");thread.setDaemon(true);return thread;
    });
    private final String instance=UUID.randomUUID().toString();
    private final AtomicLong sequence=new AtomicLong();
    public NotificationPushService(ObjectProvider<NotificationSnapshotReader> readers,ObjectProvider<UiEventStreamService> streams) {
        this.readers=readers;this.streams=streams;executor.setRemoveOnCancelPolicy(true);
    }
    public void request(UiEventRecipient recipient) {
        if(executor.isShutdown())return;
        pending.compute(recipient,(key,dirty)->{
            if(dirty!=null){dirty.set(true);return dirty;}
            var created=new AtomicBoolean(true);
            executor.schedule(()->flush(key,created),100,TimeUnit.MILLISECONDS);
            return created;
        });
    }
    private void flush(UiEventRecipient recipient,AtomicBoolean dirty) {
        dirty.set(false);
        try {
            var stream=streams.getObject();
            if(!stream.hasRecipient(recipient))return;
            // Reader transaction finishes and returns the connection before deliverSnapshot sends bytes.
            var snapshot=readers.getObject().read(recipient);
            stream.deliverNotificationSnapshot(recipient,Map.of("instance",instance,"sequence",sequence.incrementAndGet(),
                "unread",snapshot.unread(),"page",snapshot.page()));
        } catch(org.springframework.security.access.AccessDeniedException revoked) {
            var stream=streams.getIfAvailable();
            if(stream!=null)stream.revokeNotificationRecipient(recipient);
        } catch(RuntimeException failure) {
            var stream=streams.getIfAvailable();
            if(stream!=null)stream.deliverNotificationRefreshRequired(recipient);
        } finally {
            pending.computeIfPresent(recipient,(key,current)->{
                if(current!=dirty)return current;
                if(dirty.get()&&!executor.isShutdown()){
                    executor.schedule(()->flush(key,dirty),100,TimeUnit.MILLISECONDS);return dirty;
                }
                return null;
            });
        }
    }
    @PreDestroy public void close(){executor.shutdownNow();pending.clear();}
}
