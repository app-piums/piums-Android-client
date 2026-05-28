package com.piums.cliente.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class DeepLinkTarget {
    data class Artist(val id: String) : DeepLinkTarget()
    data class Booking(val id: String) : DeepLinkTarget()
    object Inbox         : DeepLinkTarget()
    object Coupons       : DeepLinkTarget()
    object Notifications : DeepLinkTarget()
}

@Singleton
class DeepLinkManager @Inject constructor() {
    private val _pending = MutableSharedFlow<DeepLinkTarget>(extraBufferCapacity = 1)
    val pending: SharedFlow<DeepLinkTarget> = _pending.asSharedFlow()

    fun dispatch(target: DeepLinkTarget) { _pending.tryEmit(target) }

    fun dispatchFromExtras(type: String?, entityId: String?) {
        if (type == null) return
        val target: DeepLinkTarget = when (type) {
            "NEW_MESSAGE", "CHAT"              -> DeepLinkTarget.Inbox
            "BOOKING_CONFIRMED",
            "BOOKING_CANCELLED",
            "BOOKING_UPDATED",
            "BOOKING_COMPLETED",
            "RESCHEDULE_REQUEST",
            "RESCHEDULE_REQUESTED",
            "RESCHEDULE_APPROVED",
            "RESCHEDULE_REJECTED"              -> if (entityId != null) DeepLinkTarget.Booking(entityId)
                                                  else DeepLinkTarget.Notifications
            "NEW_REVIEW", "ARTIST_PROFILE"     -> if (entityId != null) DeepLinkTarget.Artist(entityId)
                                                  else DeepLinkTarget.Notifications
            "DISPUTE_OPENED",
            "DISPUTE_RESOLVED",
            "DISPUTE_MESSAGE"                  -> DeepLinkTarget.Inbox
            "COUPON_SENT",
            "COUPON_EXPIRING",
            "DISCOUNT"                         -> DeepLinkTarget.Coupons
            else                               -> DeepLinkTarget.Notifications
        }
        dispatch(target)
    }
}
