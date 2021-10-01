package com.alfian.deliveryordershipper.callback

import com.alfian.deliveryordershipper.model.ShippingOrderModel

interface IShippingOrderCallbackListener {
    fun onShippingOrderLoadSuccess(shippingOrders:List<ShippingOrderModel>)
    fun onShippingOrderLoadFailed(message:String)
}