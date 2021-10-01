package com.alfian.deliveryordershipper.model

class ShippingOrderModel {
    var key:String?=null
    var shipperPhone:String?=null
    var shipperName:String?=null
    var currentLat = 0.0
    var currentLng = 0.0
    var orderModel:OrderModel?=null
    var isStartTrip=false
    var estimateTime:String="UNKNOWN"
    var restaurantKey:String?=null //same as server app
}