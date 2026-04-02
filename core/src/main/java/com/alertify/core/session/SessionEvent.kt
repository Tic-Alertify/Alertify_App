package com.alertify.core.session

sealed class SessionEvent {
    data object SessionExpired : SessionEvent()
    data object LogoutSuccess : SessionEvent()
}
