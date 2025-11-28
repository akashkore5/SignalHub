package com.khetisetu.event.notifications.provider;

import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;

public interface NotificationProvider {
    String getType();
    void send(NotificationRequestEvent event, Notification notification) throws Exception;
    boolean isEnabled();
}