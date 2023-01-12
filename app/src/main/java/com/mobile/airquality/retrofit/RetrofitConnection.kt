package com.mobile.airquality.retrofit

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitConnection {
    //객체를 하나만 생성하는 싱글턴 패턴을 적용
    companion object {
        //API 서버의 주소가 BASE_URL이 됩니다.
        private const val BASE_URL = "https://api.airvisual.com/v2/"
        private var INSTANCE: Retrofit? = null

        fun getInstance(): Retrofit {
            if (INSTANCE == null) { //null인 경우에만 생성
                INSTANCE = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                        // 컨버터 팩토리는 서버에서 온 JSON 응답을 우리가 위에서 만든 데이터 클래스 객체로 변환
                    .build()
            }
            return INSTANCE!!
        }
    }
}