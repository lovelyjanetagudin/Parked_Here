package com.tagudin.parkedhere

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var carMarker: Marker? = null
    private var currentLatLng: LatLng? = null

    private val prefsName = "ParkedHerePrefs"
    private val keyLat = "parked_lat"
    private val keyLng = "parked_lng"

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableUserLocation(moveToCurrent = true)
        } else {
            Toast.makeText(
                this,
                "Location permission is required to show your spot and save where you parked.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<MaterialButton>(R.id.btnParkedHere).setOnClickListener {
            saveCurrentParkingLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isMyLocationButtonEnabled = true
        restoreSavedParkingLocation()
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> enableUserLocation(moveToCurrent = true)
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage(
                        "ParkedHere needs your location to display your current position on the map " +
                                "and drop a car marker where you parked."
                    )
                    .setPositiveButton("Allow") { _, _ ->
                        requestLocationPermissions()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> requestLocationPermissions()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun enableUserLocation(moveToCurrent: Boolean) {
        if (!::mMap.isInitialized || !hasLocationPermission()) return
        mMap.isMyLocationEnabled = true

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val resolvedLocation = location ?: run {
                    null
                }
                if (resolvedLocation != null) {
                    val latLng = LatLng(resolvedLocation.latitude, resolvedLocation.longitude)
                    currentLatLng = latLng
                    if (carMarker == null) {
                        placeOrMoveCarMarker(latLng)
                    }
                    if (moveToCurrent) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    }
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                            currentLatLng = latLng
                            if (carMarker == null) {
                                placeOrMoveCarMarker(latLng)
                            }
                            if (moveToCurrent) {
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                            }
                        }
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun saveCurrentParkingLocation() {
        if (!hasLocationPermission()) {
            checkLocationPermission()
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val targetLoc = location
                if (targetLoc != null) {
                    val parkedLatLng = LatLng(targetLoc.latitude, targetLoc.longitude)
                    currentLatLng = parkedLatLng
                    saveLocationToPrefs(parkedLatLng)
                    placeOrMoveCarMarker(parkedLatLng)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(parkedLatLng, 18f))
                    Toast.makeText(this, "Parking spot saved!", Toast.LENGTH_SHORT).show()
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            val parkedLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                            currentLatLng = parkedLatLng
                            saveLocationToPrefs(parkedLatLng)
                            placeOrMoveCarMarker(parkedLatLng)
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(parkedLatLng, 18f))
                            Toast.makeText(this, "Parking spot saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Could not read current location. Click Set Location in Extended Controls first.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    private fun placeOrMoveCarMarker(latLng: LatLng) {
        if (carMarker == null) {
            carMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Parked Car")
                    .icon(vectorToBitmapDescriptor(R.drawable.ic_car))
            )
        } else {
            carMarker?.position = latLng
        }
    }

    private fun saveLocationToPrefs(latLng: LatLng) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(keyLat, latLng.latitude.toString())
            .putString(keyLng, latLng.longitude.toString())
            .apply()
    }

    private fun getSavedLocation(): LatLng? {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val latStr = prefs.getString(keyLat, null)
        val lngStr = prefs.getString(keyLng, null)
        if (latStr != null && lngStr != null) {
            val lat = latStr.toDoubleOrNull()
            val lng = lngStr.toDoubleOrNull()
            if (lat != null && lng != null) {
                return LatLng(lat, lng)
            }
        }
        return null
    }

    private fun restoreSavedParkingLocation() {
        val savedLatLng = getSavedLocation() ?: return
        placeOrMoveCarMarker(savedLatLng)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(savedLatLng, 17f))
    }

    private fun vectorToBitmapDescriptor(vectorResId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, vectorResId)
            ?: return BitmapDescriptorFactory.defaultMarker()
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        drawable.setBounds(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}