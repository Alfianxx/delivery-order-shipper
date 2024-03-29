package com.alfian.deliveryordershipper

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.alfian.deliveryordershipper.common.Common
import com.alfian.deliveryordershipper.model.FCMSendData
import com.alfian.deliveryordershipper.model.ShippingOrderModel
import com.alfian.deliveryordershipper.model.TokenModel
import com.alfian.deliveryordershipper.model.eventbus.UpdateShippingOrderEvent
import com.alfian.deliveryordershipper.remote.IFCMService
import com.alfian.deliveryordershipper.remote.IGoogleApi
import com.alfian.deliveryordershipper.remote.RetrofitClient
import com.alfian.deliveryordershipper.remote.RetrofitFCMClient
import com.bumptech.glide.Glide
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.paperdb.Paper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_shipping.*
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class ShippingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    
    private var shipperMarker: Marker?=null
    private var shippingOrderModel: ShippingOrderModel?=null
    
    var isInit = false
    var previousLocation: Location?=null

    private var handler: Handler?=null
    private var index:Int = -1
    private var next:Int=0
    private var startPosition:LatLng?= LatLng(0.0,0.0)
    private var endPosition:LatLng?=LatLng(0.0,0.0)
    private var v:Float = 0f
    private var lat:Double=-1.0
    private var lng:Double=-1.0

    private var blackPolyline:Polyline?=null
    private var greyPolyline:Polyline?=null
    private var polylineOptions:PolylineOptions?=null
    private var blackPolylineOptions:PolylineOptions?=null
    private var redPolyline:Polyline?=null
    private var yellowPolyline:Polyline?=null

    private var polylineList:MutableList<LatLng> = ArrayList()
    private var iGoogleApi: IGoogleApi?=null
    private var ifcmService: IFCMService?=null
    private var compositeDisposable = CompositeDisposable()

    private lateinit var placesFragment:AutocompleteSupportFragment
    private lateinit var placesClient:PlacesClient
    private val placeFields = mutableListOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG)

    val TAG = "abcd"
    private val mode = "walking"    // driving || walking || bicycling || transit
    private val transitRouting = "fewer_transfers"  // less_walking || fewer_transfers  ->   // hanya untuk mode transit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shipping)

        iGoogleApi = RetrofitClient.instance!!.create(IGoogleApi::class.java)
        ifcmService = RetrofitFCMClient.getInstance().create(IFCMService::class.java)

        initPlaces()
        setupPlaceAutocomplete()

        buildLocationRequest()
        buildLocationCallback()

        Log.d(TAG, "onCreate: run...")

        Dexter.withContext(this)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object:PermissionListener{
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                    val mapFragment = supportFragmentManager
                        .findFragmentById(R.id.map) as SupportMapFragment
                    mapFragment.getMapAsync(this@ShippingActivity)

                    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this@ShippingActivity)
                    if (ActivityCompat.checkSelfPermission(
                            this@ShippingActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            this@ShippingActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback,
                        Looper.myLooper())
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) {

                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    Toast.makeText(this@ShippingActivity,"You must enable this permission",Toast.LENGTH_SHORT).show()
                }

            }).check()

        initViews()


    }

    private fun setupPlaceAutocomplete() {
        placesFragment = supportFragmentManager
            .findFragmentById(R.id.places_autocomplete_fragment) as AutocompleteSupportFragment
        placesFragment.setPlaceFields(placeFields)
        Log.d(TAG, "setupPlaceAutocomplete: run....")
        placesFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {

                Log.d(TAG, "onPlaceSelected run.......")
                drawRoutes(place)
            }

            override fun onError(p0: Status) {

                Log.d(TAG, "onError: error p0 = ${p0.statusMessage}")

                Toast.makeText(this@ShippingActivity, "" + p0.statusMessage, Toast.LENGTH_SHORT)
                    .show()
            }

        })
    }

    private fun initPlaces() {
        Places.initialize(this,getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
    }

    private fun initViews() {
        btn_start_trip.setOnClickListener {
            val data = Paper.book().read<String>(Common.SHIPPING_DATA)
            Paper.book().write(Common.TRIP_START,data)
//            btn_start_trip.isEnabled = false //Deactive after click   //TODO : Aktifkan nanti

            shippingOrderModel = Gson().fromJson(data,object:TypeToken<ShippingOrderModel?>(){}.type)


            Log.d("aan", "initViews: mydata = $data")

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return@setOnClickListener //adding feature ------------------------------------------------------------------------
            }
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->

                compositeDisposable.add(iGoogleApi!!.getDirections(mode,
                    transitRouting,
                    Common.buildLocationString(location),
                    StringBuilder().append(shippingOrderModel!!.orderModel!!.lat)
                        .append(",")
                        .append(shippingOrderModel!!.orderModel!!.lng).toString(),
                    getString(R.string.google_maps_key)
                )!!
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ s ->

                        Log.d(TAG, "initViews: nilai s = $s")
                        Log.d("aan", "nilai data = $data")

                        //Get estimate time from API
                        val estimateTime: String
                        val jsonObject = JSONObject(s)
                        val routes = jsonObject.getJSONArray("routes")
                        val `object` = routes.getJSONObject(0)
                        val legs = `object`.getJSONArray("legs")
                        val legsObject = legs.getJSONObject(0)
                        //time
                        val time = legsObject.getJSONObject("duration")
                        estimateTime = time.getString("text")

                        val updateData = HashMap<String, Any>()
                        updateData["currentLat"] = location.latitude
                        updateData["currentLng"] = location.longitude
                        updateData["estimateTime"] = estimateTime

                        FirebaseDatabase.getInstance()
                            .getReference(Common.SHOP_REF)
                            .child(Common.currentShop!!.uid)
                            .child(Common.SHIPPING_ORDER_REF)
                            .child(shippingOrderModel!!.key!!)
                            .updateChildren(updateData)
                            .addOnFailureListener { e ->
                                Log.d("abcd", "initViews: ${e.message}")
                                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                            }
                            .addOnSuccessListener {
                                //Show directions from shipper to order's location after start trip
                                drawRoutes(data)
                                Log.d("aan", "initViews: data = $data") // tidak tampil
                            }

                    }, { t: Throwable? ->
                        Log.d("abcd", "throwable : ${t?.message}")
                        Toast.makeText(this@ShippingActivity, t!!.message, Toast.LENGTH_SHORT)
                            .show()
                    })
                )
            }
        }

        btn_show.setOnClickListener {
            if (expandable_layout.isExpanded)
                btn_show.text = "SHOW"
            else
                btn_show.text = "HIDE"
            expandable_layout.toggle()
        }

        btn_call.setOnClickListener {
            if (shippingOrderModel != null)
            {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) != PackageManager.PERMISSION_GRANTED
                ){
                    //Request Permission
                    Dexter.withContext(this)
                        .withPermission(Manifest.permission.CALL_PHONE)
                        .withListener(object:PermissionListener{
                            override fun onPermissionGranted(response: PermissionGrantedResponse?) {

                            }

                            override fun onPermissionRationaleShouldBeShown(
                                permission: PermissionRequest?,
                                token: PermissionToken?
                            ) {

                            }

                            override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                                Toast.makeText(this@ShippingActivity,"You must enable this permission to CALL",Toast.LENGTH_SHORT).show()
                            }

                        }).check()
                    return@setOnClickListener
                }
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = (Uri.parse(StringBuilder("tel:").append(shippingOrderModel!!.orderModel!!.userPhone!!).toString()))
                startActivity(intent)
            }
        }
        
        btn_done.setOnClickListener { 
            if (shippingOrderModel != null)
            {
                val builder = AlertDialog.Builder(this)
                    .setTitle("Done Order")
                    .setMessage("Confirm you already shipped this order")
                    .setNegativeButton("NO"){ dialogInterface, _ -> dialogInterface.dismiss() }
                    .setPositiveButton("YES"){ _, _ ->

                        //Create waiting dialog
                        val dialog = AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setMessage("waiting...")
                            .create()

                        dialog.show()

                        //Update order
                        val updateData = HashMap<String,Any>()
                        updateData["orderStatus"] = 2
                        updateData["shipperUid"] = Common.currentShipperUser!!.uid!!

                        FirebaseDatabase.getInstance()
                            .getReference(Common.SHOP_REF)
                            .child(shippingOrderModel!!.shopKey!!)
                            .child(Common.ORDER_REF)
                            .child(shippingOrderModel!!.orderModel!!.key!!)
                            .updateChildren(updateData)
                            .addOnFailureListener { e-> Toast.makeText(this@ShippingActivity,e.message,Toast.LENGTH_LONG).show() }
                            .addOnSuccessListener {

                                //Delete shipping order information
                                FirebaseDatabase.getInstance()
                                    .getReference(Common.SHOP_REF)
                                    .child(shippingOrderModel!!.shopKey!!)
                                    .child(Common.SHIPPING_ORDER_REF)
                                    .child(shippingOrderModel!!.orderModel!!.key!!)
                                    .removeValue()
                                    .addOnFailureListener { e-> Toast.makeText(this@ShippingActivity,e.message,Toast.LENGTH_LONG).show() }
                                    .addOnSuccessListener {
                                        //send notification
                                        //copy from server app
                                        //Load token
                                        FirebaseDatabase.getInstance()
                                            .getReference(Common.TOKEN_REF)
                                            .child(shippingOrderModel!!.orderModel!!.userId!!)
                                            .addListenerForSingleValueEvent(object:
                                                ValueEventListener {
                                                override fun onCancelled(p0: DatabaseError) {
                                                    dialog.dismiss()
                                                    Toast.makeText(this@ShippingActivity,""+p0.message,Toast.LENGTH_SHORT).show()
                                                }

                                                override fun onDataChange(p0: DataSnapshot) {
                                                    if (p0.exists())
                                                    {
                                                        val tokenModel = p0.getValue(TokenModel::class.java)
                                                        val notiData = HashMap<String,String>()
                                                        notiData[Common.NOTIF_TITLE] = "Your order has been shipped"
                                                        notiData[Common.NOTIF_CONTENT] =
                                                            StringBuilder("Your order has been shipped by shipper")
                                                                .append(Common.currentShipperUser!!.phone!!).toString()

                                                        val sendData = FCMSendData(tokenModel!!.token!!,notiData)

                                                        compositeDisposable.add(
                                                            ifcmService!!.sendNotification(sendData)
                                                                .subscribeOn(Schedulers.io())
                                                                .observeOn(AndroidSchedulers.mainThread())
                                                                .subscribe({ fcmResponse ->
                                                                    dialog.dismiss()
                                                                    if (fcmResponse.success == 1)
                                                                    {
                                                                        Toast.makeText(this@ShippingActivity,"Finish!",Toast.LENGTH_SHORT).show()

                                                                    }
                                                                    else
                                                                    {
                                                                        Toast.makeText(this@ShippingActivity,"Order has been update but failed to send notification",Toast.LENGTH_SHORT).show()

                                                                    }

                                                                    if (!TextUtils.isEmpty(Paper.book().read(Common.TRIP_START)))
                                                                        Paper.book().delete(Common.TRIP_START)
                                                                    EventBus.getDefault().postSticky(
                                                                        UpdateShippingOrderEvent()
                                                                    )
                                                                    finish()

                                                                },
                                                                    {t ->
                                                                        dialog.dismiss()
                                                                        Toast.makeText(this@ShippingActivity,""+t.message,Toast.LENGTH_SHORT).show()

                                                                    })
                                                        )
                                                    }
                                                    else
                                                    {
                                                        dialog.dismiss()
                                                        Toast.makeText(this@ShippingActivity,"Token not found",Toast.LENGTH_SHORT).show()
                                                    }
                                                }

                                            })

                                    }

                            }
                    }
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    private fun buildLocationCallback() {
        locationCallback = object:LocationCallback(){
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                val locationShipper = LatLng(p0!!.lastLocation.latitude,
                    p0.lastLocation.longitude)

                updateLocation(p0.lastLocation)

                if (shipperMarker == null)
                {
                    val height = 80
                    val width = 80
                    val bitmapDrawable = ContextCompat.getDrawable(this@ShippingActivity,
                        R.drawable.shipper)
                    val b = bitmapDrawable!!.toBitmap()
                    val smallMarker = Bitmap.createScaledBitmap(b,width,height,false)
                    shipperMarker = mMap.addMarker(MarkerOptions()
                        .icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
                        .position(locationShipper)
                        .title("You"))

                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locationShipper,18f))

                    Log.d(TAG, "onLocationResult: run...")
                }


                if (isInit && previousLocation != null)
                {
                    val from = StringBuilder()
                        .append(previousLocation!!.latitude)
                        .append(",")
                        .append(previousLocation!!.longitude)
                    val to = StringBuilder()
                        .append(locationShipper.latitude)
                        .append(",")
                        .append(locationShipper.longitude)

                    moveMarkerAnimation(shipperMarker,from,to)

                    previousLocation = p0.lastLocation
                }
                if (!isInit)
                {
                    isInit = true
                    previousLocation = p0.lastLocation
                }
            }
        }
    }

    private fun updateLocation(lastLocation: Location?) {


        val data = Paper.book().read<String>(Common.TRIP_START)
        if (!TextUtils.isEmpty(data))
        {
            val shippingOrder = Gson().fromJson<ShippingOrderModel>(data, object :TypeToken<ShippingOrderModel>(){}.type)
            if (shippingOrder != null)
            {
                compositeDisposable.add(
                    iGoogleApi!!.getDirections(
                        mode,
                        transitRouting,
                        Common.buildLocationString(lastLocation),
                        StringBuilder().append(shippingOrderModel!!.orderModel!!.lat)
                            .append(",")
                            .append(shippingOrderModel!!.orderModel!!.lng).toString(),
                        getString(R.string.google_maps_key)
                    )!!
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ s ->

                            Log.d(TAG, "updateLocation: run 1....")
                            //Get estimate time from API
                            val estimateTime: String
                            val jsonObject = JSONObject(s)
                            val routes = jsonObject.getJSONArray("routes")
                            Log.d(TAG, "updateLocation: s = $s")
                            val `object` = routes.getJSONObject(0)
                            val legs = `object`.getJSONArray("legs")
                            val legsObject = legs.getJSONObject(0)
                            //time
                            val time = legsObject.getJSONObject("duration")
                            estimateTime = time.getString("text")

                            val updateData = HashMap<String, Any>()
                            updateData["currentLat"] = lastLocation!!.latitude
                            updateData["currentLng"] = lastLocation.longitude
                            updateData["estimateTime"] = estimateTime

                            Log.d(TAG, "updateLocation: run 2...")

                            FirebaseDatabase.getInstance()
                                .getReference(Common.SHOP_REF)
                                .child(Common.currentShop!!.uid)
                                .child(Common.SHIPPING_ORDER_REF)
                                .child(shippingOrderModel!!.key!!)
                                .updateChildren(updateData)
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                                } //in updateLocation, we just remove drawPath


                        }, { t: Throwable? ->
                            Log.d(
                                TAG,
                                "updateLocation: ${t?.message}"
                            )     // Error --------------------------------------------------------------------
                            Toast.makeText(this@ShippingActivity, t!!.message, Toast.LENGTH_SHORT)
                                .show()
                        })
                )
            }
        }
        else
        {
            Toast.makeText(this,"Please press START_TRIP",Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveMarkerAnimation(
        marker: Marker?,
        from: java.lang.StringBuilder,
        to: java.lang.StringBuilder
    ) {
        compositeDisposable.add(iGoogleApi!!.getDirections(mode,
        transitRouting,
        from.toString(),
        to.toString(),
        getString(R.string.google_maps_key))!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ s->
                Log.d("DEBUG",s!!)
                try {
                    val jsonObject = JSONObject(s)
                    val jsonArray = jsonObject.getJSONArray("routes")
                    for (i in 0 until jsonArray.length())
                    {
                        val route = jsonArray.getJSONObject(i)
                        val poly = route.getJSONObject("overview_polyline")
                        val polyline = poly.getString("points")
                        polylineList = Common.decodePoly(polyline)
                    }

                    polylineOptions = PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(5.0f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.endCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList)
                    greyPolyline = mMap.addPolyline(polylineOptions)

                    blackPolylineOptions = PolylineOptions()
                    blackPolylineOptions!!.color(Color.GRAY)
                    blackPolylineOptions!!.width(5.0f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.endCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList)
                    blackPolyline = mMap.addPolyline(blackPolylineOptions)

                    //Animator
                    val polylineAnimator = ValueAnimator.ofInt(0,100)
                    polylineAnimator.duration = 2000
                    polylineAnimator.interpolator = LinearInterpolator()
                    polylineAnimator.addUpdateListener { valueAnimator ->
                        val points = greyPolyline!!.points
                        val percentValue = Integer.parseInt(valueAnimator.animatedValue.toString())
                        val size = points.size
                        val newPoints = (size*(percentValue / 100.0f)).toInt()
                        val p = points.subList(0,newPoints)
                        blackPolyline!!.points = p

                    }
                    polylineAnimator.start()

                    //Car moving
                    index = -1
                    next = 1
                    val r = object: Runnable {
                        override fun run() {
                            if (index < polylineList.size - 1)
                            {
                                index++
                                next = index + 1
                                startPosition = polylineList[index]
                                endPosition = polylineList[next]
                            }

                            val valueAnimator = ValueAnimator.ofInt(0,1)
                            valueAnimator.duration = 1500
                            valueAnimator.interpolator = LinearInterpolator()
                            valueAnimator.addUpdateListener { valueAnim ->
                                v = valueAnim.animatedFraction
                                lat = v * endPosition!!.latitude + (1-v) * startPosition!!.latitude
                                lng = v * endPosition!!.longitude + (1-v) * startPosition!!.longitude

                                val newPos = LatLng(lat,lng)
                                marker!!.position = newPos
                                marker.setAnchor(0.5f,0.5f)
                                marker.rotation = Common.getBearing(startPosition!!,newPos)

                                mMap.moveCamera(CameraUpdateFactory.newLatLng(marker.position)) //Fixed

                            }

                            valueAnimator.start()
                            if (index < polylineList.size - 2)
                                handler!!.postDelayed(this,1500)
                        }

                    }

                    handler = Handler()
                    handler!!.postDelayed(r,1500)



                }catch (e:Exception)
                {
                    Log.d("DEBUG",e.message!!)
                }

            },{throwable ->
                Toast.makeText(this@ShippingActivity,""+throwable.message,Toast.LENGTH_SHORT).show()
            }))
    }

    private fun setShippingOrderModel() {
        Paper.init(this)
        val data: String?
        if (TextUtils.isEmpty(Paper.book().read(Common.TRIP_START)))
        {
            data = Paper.book().read<String>(Common.SHIPPING_DATA)
            btn_start_trip.isEnabled = true
        }
        else
        {
            data = Paper.book().read<String>(Common.TRIP_START)
            btn_start_trip.isEnabled = false
        }
        if (!TextUtils.isEmpty(data))
        {
            drawRoutes(data)
            shippingOrderModel = Gson()
                .fromJson<ShippingOrderModel>(data,object:TypeToken<ShippingOrderModel>(){}.type)

            if (shippingOrderModel != null)
            {
                Common.setSpanStringColor("Name: ",
                    shippingOrderModel!!.orderModel!!.userName,
                    txt_name,
                    Color.parseColor("#333639"))

                Common.setSpanStringColor("Address: ",
                    shippingOrderModel!!.orderModel!!.shippingAddress,
                    txt_address,
                    Color.parseColor("#673ab7"))

                Common.setSpanStringColor("No.: ",
                    shippingOrderModel!!.orderModel!!.key,
                    txt_order_number,
                    Color.parseColor("#795548"))

                txt_date!!.text = StringBuilder().append(SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(
                    shippingOrderModel!!.orderModel!!.createDate
                ))

                Glide.with(this)
                    .load(shippingOrderModel!!.orderModel!!.cartItemList!![0].itemImage)
                    .into(img_item_image)
            }
        }
        else
        {
            Toast.makeText(this,"Shipping Order Model is null",Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawRoutes(place: Place) {


        mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            .title(place.name)
            .snippet(place.address)
            .position(place.latLng!!)) //Set destination

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e -> Toast.makeText(this@ShippingActivity,""+e.message,Toast.LENGTH_SHORT).show() }
            .addOnSuccessListener { location ->
                val to = StringBuilder().append(place.latLng!!.latitude)
                    .append(",")
                    .append(place.latLng!!.longitude).toString()
                val from = StringBuilder().append(location.latitude)
                    .append(",")
                    .append(location.longitude)
                    .toString()

                compositeDisposable.add(iGoogleApi!!.getDirections(mode,transitRouting,
                    from,to,
                    getString(R.string.google_maps_key))!!
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ s->
                        try {
                            val jsonObject = JSONObject(s)
                            val jsonArray = jsonObject.getJSONArray("routes")
                            for (i in 0 until jsonArray.length())
                            {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")
                                polylineList = Common.decodePoly(polyline)
                                Log.d("aan", "drawRoutes: poly = $poly")
                            }

                            polylineOptions = PolylineOptions()
                            polylineOptions!!.color(Color.YELLOW)
                            polylineOptions!!.width(12.0f)
                            polylineOptions!!.startCap(SquareCap())
                            polylineOptions!!.endCap(SquareCap())
                            polylineOptions!!.jointType(JointType.ROUND)
                            polylineOptions!!.addAll(polylineList)
                            yellowPolyline = mMap.addPolyline(polylineOptions)




                        }catch (e:Exception)
                        {
                            Log.d("DEBUG",e.message!!)
                        }

                    },{throwable ->
                        Toast.makeText(this@ShippingActivity,""+throwable.message,Toast.LENGTH_SHORT).show()
                    }))
            }
    }

    private fun drawRoutes(data: String?) {
        val shippingOrderModel = Gson()
            .fromJson<ShippingOrderModel>(data,object:TypeToken<ShippingOrderModel>(){}.type)

        mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.box))
            .title(shippingOrderModel.orderModel!!.userName)
            .snippet(shippingOrderModel.orderModel!!.shippingAddress)
            .position(LatLng(shippingOrderModel.orderModel!!.lat, shippingOrderModel.orderModel!!.lng)))

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions

            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e -> Toast.makeText(this@ShippingActivity,""+e.message,Toast.LENGTH_SHORT).show() }
            .addOnSuccessListener { location ->
                val to = StringBuilder().append(shippingOrderModel.orderModel!!.lat)
                    .append(",")
                    .append(shippingOrderModel.orderModel!!.lng).toString()
                        val from = StringBuilder().append(location.latitude)
                            .append(",")
                            .append(location.longitude)
                            .toString()

                compositeDisposable.add(iGoogleApi!!.getDirections(mode,transitRouting,
                from,to,
                getString(R.string.google_maps_key))!!
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ s->
                       try {
                            val jsonObject = JSONObject(s)
                            val jsonArray = jsonObject.getJSONArray("routes")
                            for (i in 0 until jsonArray.length())
                            {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")

                                Log.d("aan", "drawRoutes: myPolyline = $polyline")
                                polylineList = Common.decodePoly(polyline)

                                if (i == 0) {
//                                    polylineList.addAll(Common.decodePoly(polyline))
                                    //red
                                    polylineOptions = PolylineOptions()
                                    polylineOptions!!.color(Color.RED)
                                    polylineOptions!!.width(12.0f)
                                    polylineOptions!!.startCap(SquareCap())
                                    polylineOptions!!.endCap(SquareCap())
                                    polylineOptions!!.jointType(JointType.ROUND)
                                    polylineOptions!!.addAll(polylineList)
                                    redPolyline = mMap.addPolyline(polylineOptions)
                                    Log.d("aan", "drawRoutes: myPolylineList = $polylineList")

                                    Log.d("aan12345", "drawRoutes: ukuran : ${polylineList.size}")
                                    Log.d("aan12345", "drawRoutes: indices : ${polylineList.indices}")
                                }
                                if (i == 1) {
//                                    polylineList.addAll(Common.decodePoly(polyline))
//                                    polylineList = Common.decodePoly(polyline)
                                    //blue
                                    polylineOptions = PolylineOptions()
                                    polylineOptions!!.color(Color.BLUE)
                                    polylineOptions!!.width(8.0f)   // ukuran rute
                                    polylineOptions!!.startCap(SquareCap())
                                    polylineOptions!!.endCap(SquareCap())
                                    polylineOptions!!.jointType(JointType.ROUND)
                                    polylineOptions!!.addAll(polylineList)
                                    mMap.addPolyline(polylineOptions)
                                    Log.d(TAG, "rute ke 2")
                                }
                                if (i == 2) {
//                                    polylineList.addAll(Common.decodePoly(polyline))
//                                    polylineList = Common.decodePoly(polyline)
                                    //green
                                    polylineOptions = PolylineOptions()
                                    polylineOptions!!.color(Color.GREEN)
                                    polylineOptions!!.width(5.0f)
                                    polylineOptions!!.startCap(SquareCap())
                                    polylineOptions!!.endCap(SquareCap())
                                    polylineOptions!!.jointType(JointType.ROUND)
                                    polylineOptions!!.addAll(polylineList)
                                    mMap.addPolyline(polylineOptions)
                                    Log.d(TAG, "rute ke 3")
                                }
                                if (i == 3) {
                                    Toast.makeText(this, "masih ada rute", Toast.LENGTH_LONG).show()
                                    Log.d(TAG, "rute ke 4")

                                    polylineOptions = PolylineOptions()
                                    polylineOptions!!.color(Color.YELLOW)
                                    polylineOptions!!.width(12.0f)
                                    polylineOptions!!.startCap(SquareCap())
                                    polylineOptions!!.endCap(SquareCap())
                                    polylineOptions!!.jointType(JointType.ROUND)
                                    polylineOptions!!.addAll(polylineList)
                                    mMap.addPolyline(polylineOptions)

                                }
                                if (i == 4) {
//                                    Toast.makeText(this, "masih ada rute", Toast.LENGTH_LONG).show()
                                    Log.d(TAG, "rute ke 5")
                                }
                            }


                        }catch (e:Exception)
                        {
                            Log.d("abcd",e.message!!)
                        }

                    },{throwable ->
                        Log.d("abcd",throwable.message!!)
                        Toast.makeText(this@ShippingActivity,""+throwable.message,Toast.LENGTH_SHORT).show()
                    }))
            }
    }

    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 15000 //15sec
        locationRequest.fastestInterval = 10000 //10 sec
        locationRequest.smallestDisplacement = 20f
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        setShippingOrderModel()

        mMap.uiSettings.isZoomControlsEnabled = true
        try {
            val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,R.raw.delivery_light_with_label))
            if (!success)
                Log.d("aan","Failed to load map style")
        }catch (ex:Resources.NotFoundException)
        {
            Log.d("aan","Not found json string for map style")
        }
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        compositeDisposable.clear()
        super.onDestroy()
    }
}