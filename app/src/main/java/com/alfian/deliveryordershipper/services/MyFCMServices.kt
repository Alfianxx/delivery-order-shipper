package com.alfian.deliveryordershipper.services

import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.eventbus.UpdateShippingOrderEvent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import java.util.*

class MyFCMServices : FirebaseMessagingService() {
    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        Common.updateToken(this, p0, false, true) //because we are in shipper app so shipper = true
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val dataRecv = remoteMessage.data
        Common.showNotification(
            this, Random().nextInt(),
            dataRecv[Common.NOTIF_TITLE],
            dataRecv[Common.NOTIF_CONTENT],
            null
        )

        EventBus.getDefault()
            .postSticky(UpdateShippingOrderEvent()) //refresh order list after receive order need ship
    }
}