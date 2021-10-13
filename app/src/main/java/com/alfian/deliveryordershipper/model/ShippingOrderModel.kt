package com.alfian.deliveryordershipper.model

class ShippingOrderModel {
    var key:String?=null
    var shipperPhone:String?=null
    var shipperName:String?=null
    var currentLat: Double? = 0.0
    var currentLng: Double? = 0.0
    var orderModel:OrderModel?=null
    var startTrip: Boolean?=false
    var estimateTime:String?="UNKNOWN"
    var shopKey:String?=null //same as server app
}