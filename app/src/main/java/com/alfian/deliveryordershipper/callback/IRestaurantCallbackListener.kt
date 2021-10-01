package com.alfian.deliveryordershipper.callback

import com.alfian.deliveryordershipper.model.RestaurantModel

interface IRestaurantCallbackListener {
    fun onRestaurantLoadSuccess(restaurantList: List<RestaurantModel>)
    fun onRestaurantLoadFailed(message:String)
}