package com.alfian.deliveryordershipper.remote

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface IGoogleApi {
    @GET("maps/api/directions/json")
    fun getDirections(
        @Query("mode") mode:String?,
        @Query("transit_routing_preference") transit_routing:String?,
        @Query("origin") origin:String?,
        @Query("destination") destination:String?,
        @Query("key") key:String?,
        @Query("alternatives") alternatives: Boolean = true
    ): Observable<String?>?
}