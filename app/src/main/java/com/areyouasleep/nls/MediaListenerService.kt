package com.areyouasleep.nls

import android.service.notification.NotificationListenerService
import com.areyouasleep.core.GuardState

/**
 * 这个服务本身不读通知内容，只是「通知使用权」是普通应用调用
 * MediaSessionManager.getActiveSessions() 的唯一合法门路。
 * 用户在系统设置里手动授权后，onListenerConnected 才会被调用。
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        GuardState.nlsEnabled.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        GuardState.nlsEnabled.value = false
    }
}
