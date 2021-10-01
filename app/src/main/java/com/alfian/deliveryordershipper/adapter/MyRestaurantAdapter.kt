package com.alfian.deliveryordershipper.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alfian.deliveryordershipper.R
import com.alfian.deliveryordershipper.callback.IRecyclerItemClickListener
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.RestaurantModel
import com.alfian.deliveryordershipper.model.eventbus.RestaurantSelectEvent
import com.bumptech.glide.Glide
import org.greenrobot.eventbus.EventBus

class MyRestaurantAdapter (internal var context: Context,
                           internal var restaurantList: List<RestaurantModel>) :
    RecyclerView.Adapter<MyRestaurantAdapter.MyViewHolder>()  {

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        override fun onClick(view: View?) {
            listener!!.onItemClick(view!!,adapterPosition)
        }

        var txtRestaurantName: TextView?=null
        var txtRestaurantAddress: TextView?=null
        var imgRestaurant: ImageView?=null

        private var listener: IRecyclerItemClickListener?=null

        fun setListener(listener: IRecyclerItemClickListener)
        {
            this.listener = listener
        }

        init{
            txtRestaurantName = itemView.findViewById(R.id.txt_restaurant_name) as TextView
            txtRestaurantAddress = itemView.findViewById(R.id.txt_restaurant_address) as TextView
            imgRestaurant = itemView.findViewById(R.id.img_restaurant) as ImageView
            itemView.setOnClickListener(this)
        }

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MyRestaurantAdapter.MyViewHolder {
        return MyViewHolder(LayoutInflater.from(context).inflate(R.layout.layout_restaurant,parent,false))
    }

    override fun onBindViewHolder(holder: MyRestaurantAdapter.MyViewHolder, position: Int) {
        Glide.with(context).load(restaurantList[position].imageUrl)
            .into(holder.imgRestaurant!!)
        holder.txtRestaurantName!!.text = restaurantList[position].name
        holder.txtRestaurantAddress!!.text = restaurantList[position].address

        holder.setListener(object : IRecyclerItemClickListener{
            override fun onItemClick(view: View, pos: Int) {
                //code late
                Common.currentRestaurant = restaurantList[pos]
                EventBus.getDefault().postSticky(RestaurantSelectEvent(restaurantList[pos]))
            }

        })
    }

    override fun getItemCount(): Int {
        return restaurantList.size
    }

}