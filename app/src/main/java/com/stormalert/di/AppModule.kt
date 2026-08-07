package com.stormalert.di

import android.content.Context
import com.stormalert.data.analysis.StormMovementAnalyzer
import com.stormalert.data.analysis.StormRiskAnalyzer
import com.stormalert.data.network.RadarApiService
import com.stormalert.data.network.RainViewerApiService
import com.stormalert.data.network.StormApiService
import com.stormalert.data.network.WeatherApiService
import com.stormalert.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    @Named("WeatherRetrofit")
    fun provideWeatherRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("RadarRetrofit")
    fun provideRadarRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(RadarApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("RainViewerRetrofit")
    fun provideRainViewerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(RainViewerApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    @Named("StormRetrofit")
    fun provideStormRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(StormApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideWeatherApiService(@Named("WeatherRetrofit") weatherRetrofit: Retrofit): WeatherApiService {
        return weatherRetrofit.create(WeatherApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideRadarApiService(@Named("RadarRetrofit") radarRetrofit: Retrofit): RadarApiService {
        return radarRetrofit.create(RadarApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideRainViewerApiService(@Named("RainViewerRetrofit") rainViewerRetrofit: Retrofit): RainViewerApiService {
        return rainViewerRetrofit.create(RainViewerApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideStormApiService(@Named("StormRetrofit") stormRetrofit: Retrofit): StormApiService {
        return stormRetrofit.create(StormApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideStormMovementAnalyzer(): StormMovementAnalyzer {
        return StormMovementAnalyzer()
    }
    
    @Provides
    @Singleton
    fun provideStormRiskAnalyzer(): StormRiskAnalyzer {
        return StormRiskAnalyzer()
    }
    
    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context
    ): LocationManager {
        return LocationManager(context)
    }
}
