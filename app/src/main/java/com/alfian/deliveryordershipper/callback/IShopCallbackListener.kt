package com.alfian.deliveryordershipper.callback

import com.alfian.deliveryordershipper.model.ShopModel

interface IShopCallbackListener {
    fun onShopLoadSuccess(shopList: List<ShopModel>)
    fun onShopLoadFailed(message:String)
}