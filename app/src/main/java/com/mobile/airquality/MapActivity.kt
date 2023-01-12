package com.mobile.airquality

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.mobile.airquality.databinding.ActivityMapBinding

class MapActivity : AppCompatActivity(), OnMapReadyCallback {
    lateinit var binding: ActivityMapBinding

    private var mMap: GoogleMap? = null
    var currentLat: Double = 0.0    // MainActivity.kt에서 전달된 위도
    var currentLng: Double = 0.0    // MainActivity.kt에서 전달된 경로


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // MainActivity.kt에서 intent로 전달된 값을 가져옴
        currentLat = intent.getDoubleExtra("currentLat", 0.0)
        currentLng = intent.getDoubleExtra("currentLng", 0.0)

        //구글 맵 객체의 생명주기를 관리하는 supportMapFragment 객체를 mapFragment에 저장
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        //mapFragment에 onMapReadyCallback 인터페이스를 등록 -> 지도가 준비되면 onMapReady( ) 함수가 자동으로 실행됨
        mapFragment?.getMapAsync(this)

        //지도 페이지에서 마지막으로 해주어야 할 작업
        //→ 다음 버튼을 눌렀을 때, 다시 메인 액티비티로 위, 경도의 값을 가지고 들어가는 것
        binding.btnCheckHere.setOnClickListener {
            mMap?.let {     // mMap이 null이 아닌 경우 아래 코드 블록 실행
                val intent = Intent()
                intent.putExtra("latitude", it.cameraPosition.target.latitude)
                intent.putExtra("longitude", it.cameraPosition.target.longitude)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap?.let {
            val currentLocation = LatLng(currentLat, currentLng)
            it.setMaxZoomPreference(20.0f) //줌 최대값 설정
            it.setMinZoomPreference(12.0f) //줌 최소값 설정
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
        }
        setMarker()

        binding.fabCurrentLocation.setOnClickListener{
            val locationProvider = LocationProvider(this@MapActivity)

            //위도와 경도 정보를 가져옴
            val latitude = locationProvider.getLocationLatitude()
            val longitude = locationProvider.getLocationLongitude()
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 16f))

            setMarker()
        }
    }

    //마커 설정하는 함수
    private fun setMarker(){
        mMap?.let{
            it.clear() //지도에 있는 마커를 먼저 삭제
            val markerOptions = MarkerOptions()
            markerOptions.position(it.cameraPosition.target) //마커의 위치 설정
            markerOptions.title("마커 위치") //마커의 이름 설정
            val marker = it.addMarker(markerOptions) //지도에 마커를 추가하고, 마커 객체를 리턴
            it.setOnCameraMoveListener {
                marker?.let { marker ->
                    marker.position = it.cameraPosition.target
                }
            }
        }
    }
}
