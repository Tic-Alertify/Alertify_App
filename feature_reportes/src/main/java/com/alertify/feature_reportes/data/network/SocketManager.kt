package com.alertify.feature_reportes.data.network

import android.util.Log
import com.alertify.feature_reportes.config.ConfigManager
import com.alertify.feature_reportes.data.model.ReportResponse
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

object SocketManager {
    private var mSocket: Socket? = null
    private val gson = Gson()

    private val _newReports = MutableSharedFlow<ReportResponse>(extraBufferCapacity = 1)
    val newReports = _newReports.asSharedFlow()

    fun connect() {
        if (mSocket?.connected() == true) return
        try {
            mSocket = IO.socket(ConfigManager.SOCKET_URL)
            mSocket?.on("new-report") { args ->
                val data = args[0] as JSONObject
                val report = gson.fromJson(data.toString(), ReportResponse::class.java)
                _newReports.tryEmit(report)
                Log.d("SOCKET_MANAGER", "Reporte recibido en vivo: ${report.id}")

            }
            mSocket?.connect()
        } catch (e: Exception) {
            Log.e("SOCKET_ERROR", e.message ?: "Unknown error")
        }
    }

    fun disconnect() {
        mSocket?.disconnect()
        mSocket?.off()
    }
}