package com.mobile.airquality

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.mobile.airquality.databinding.ActivityMainBinding
import com.mobile.airquality.retrofit.AirQualityResponse
import com.mobile.airquality.retrofit.AirQualityService
import com.mobile.airquality.retrofit.RetrofitConnection

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // 런타임 권한 요청시 필요한 요청 코드입니다.
    private val PERMISSIONS_REQUEST_CODE = 100

    // 요청할 권한 목록
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION)

    // 위치 서비스 요청시 필요한 런처입니다.
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    // 위도와 경도를 가져올 때 필요
    lateinit var locationProvider: LocationProvider

    // 위도와 경도를 저장합니다.
    var latitude: Double = 0.0
    var longitude: Double = 0.0

    val startMapActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), object :
            ActivityResultCallback<ActivityResult> {
            override fun onActivityResult(result: ActivityResult?) {
                if (result?.resultCode ?: Activity.RESULT_CANCELED == Activity.RESULT_OK) {
                    latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                    longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                    updateUI()
                }
            }
        })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermissions()
        updateUI()
        setRefreshButton()
        setFab()
    }

    private fun setFab() {
        binding.fab.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            intent.putExtra("currentLat", latitude)
            intent.putExtra("currentLng", longitude)
            startMapActivityResult.launch(intent)
        }
    }

    private fun setRefreshButton() {
        binding.btnRefresh.setOnClickListener {
            updateUI()
        }
    }

    private fun updateUI() {
        locationProvider = LocationProvider(this@MainActivity)

        //위도와 경도 정보를 가져옵니다.
        if (latitude == 0.0 || longitude == 0.0) {
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }
        Log.d("lanlng", "위도: $latitude")
        Log.d("lanlng", "경도: $longitude")

        if (latitude != 0.0 || longitude != 0.0) {

            //1. 현재 위치를 가져오고 UI 업데이트
            //현재 위치를 가져오기
            val address = getCurrentAddress(latitude, longitude) //주소가 null 이 아닐 경우 UI 업데이트
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}" // 예시: 역삼 1동
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}" // 예시 : 대한민국 서울특별시
            }

            //2. 현재 미세먼지 농도 가져오고 UI 업데이트
            getAirQualityData(latitude, longitude)

        } else {
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없었습니다. 새로고침을 눌러주세요.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * @desc 레트로핏 클래스를 이용하여 미세먼지 오염 정보를 가져옵니다.
     * */
    private fun getAirQualityData(latitude: Double, longitude: Double) { // 레트로핏 객체를 이용하면 AirQualityService 인터페이스 구현체를 가져올 수 있습니다.
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        retrofitAPI.getAirQualityData(latitude.toString(), longitude.toString(), BuildConfig.AIR_API_KEY)
            .enqueue(object : Callback<AirQualityResponse> {
                override fun onResponse(
                    call: Call<AirQualityResponse>,
                    response: Response<AirQualityResponse>,
                ) { //정상적인 Response가 왔다면 UI 업데이트
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!", Toast.LENGTH_SHORT).show() //만약 response.body()가 null 이 아니라면 updateAirUI()
                        response.body()?.let { updateAirUI(it) }
                    } else {
                        Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    /**
     * @desc 가져온 데이터 정보를 바탕으로 화면을 업데이트한다.
     * */
    private fun updateAirUI(airQualityData: AirQualityResponse) {
        val pollutionData = airQualityData.data.current.pollution

        //수치 지정 (가운데 숫자)
        binding.tvCount.text = pollutionData.aqius.toString()

        //측정된 날짜 지정
        //"2021-09-04T14:00:00.000Z" 형식을  "2021-09-04 23:00"로 수정
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateFormatter).toString()

        when (pollutionData.aqius) {
            in 0..50 -> {
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }

            in 51..150 -> {
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }

            in 151..200 -> {
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }

            else -> {
                binding.tvTitle.text = "매우 나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }
    }

    /**
     * @desc 위도와 경도를 기준으로 실제 주소를 가져온다.
     * */
    fun getCurrentAddress(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(
            this,
            Locale.getDefault()
        ) // Address 객체는 주소와 관련된 여러 정보를 가지고 있습니다. android.location.Address 패키지 참고.

        val addresses: List<Address>? = try { //Geocoder 객체를 이용하여 위도와 경도로부터 리스트를 가져옵니다.
            geocoder.getFromLocation(latitude, longitude, 7)
        } catch (ioException: IOException) {
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.", Toast.LENGTH_LONG).show()
            return null
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(this, "잘못된 위도, 경도 입니다.", Toast.LENGTH_LONG).show()
            return null
        }

        //에러는 아니지만 주소가 발견되지 않은 경우
        if (addresses == null || addresses.isEmpty()) {
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }

        return addresses[0]
    }

    private fun checkAllPermissions() {
        if (!isLocationServicesAvailable()) { //1. 위치 서비스(GPS)가 켜져있는지 확인합니다.
            showDialogForLocationServiceSetting();
        } else {  //2. 런타임 앱 권한이 모두 허용되어있는지 확인합니다.
            isRunTimePermissionsGranted();
        }
    }

    fun isLocationServicesAvailable(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    fun isRunTimePermissionsGranted() { // 위치 퍼미션을 가지고 있는지 체크합니다.
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) { // 권한이 한 개라도 없다면 퍼미션 요청을 합니다.
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    /**
     * @desc 런타임 권한을 요청하고 권한 요청에 따른 결과를 리턴한다.
     * */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size) {

            // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            var checkResult = true

            // 모든 퍼미션을 허용했는지 체크합니다.
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    checkResult = false
                    break
                }
            }
            if (checkResult) { //위치 값을 가져올 수 있음
                updateUI()
            } else { //퍼미션이 거부되었다면 앱을 종료합니다.
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * @desc LocationManager를 사용하기 위해서 권한을 요청한다.
     * */
    private fun showDialogForLocationServiceSetting() {

        //먼저 ActivityResultLauncher를 설정해줍니다. 이 런처를 이용하여 결과 값을 리턴해야하는 인텐트를 실행할 수 있습니다.
        getGPSPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> //결과 값을 받았을 때 로직을 작성해줍니다.
            if (result.resultCode == Activity.RESULT_OK) { //사용자가 GPS 를 활성화 시켰는지 확인합니다.
                if (isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted()
                } else { //위치 서비스가 허용되지 않았다면 앱을 종료합니다.
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("위치 서비스가 꺼져있습니다. 설정해야 앱을 사용할 수 있습니다.")
        builder.setCancelable(true)
        builder.setPositiveButton("설정", DialogInterface.OnClickListener { dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener { dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스(GPS) 설정 후 사용해주세요.", Toast.LENGTH_SHORT).show()
            finish()
        })
        builder.create().show()
    }
}