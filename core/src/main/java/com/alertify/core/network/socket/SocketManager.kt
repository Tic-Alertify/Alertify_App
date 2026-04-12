package com.alertify.core.network.socket

import android.util.Log
import com.alertify.core.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SocketManager - Gestor de conexión Socket.io genérico
 *
 * RESPONSABILIDADES:
 * - Gestionar conectividad a servidor WebSocket
 * - Emitir eventos JSON sin conocer los modelos de features
 * - Permitir suscripción a eventos específicos
 * - Auto-reconnect en caso de pérdida de conexión
 *
 * DESIGN PATTERN:
 * - Singletón: Una única instancia de Socket
 * - SharedFlow: Desacoplamiento de listeners
 * - Generic: Sin dependencia de modelos de features
 *
 * EJEMPLO DE USO:
 * ```
 * class MyRepository @Inject constructor(
 *     private val socketManager: SocketManager
 * ) {
 *     fun listenToEvents(): SharedFlow<JSONObject> {
 *         return socketManager.on("event-name")
 *     }
 * }
 * ```
 */
@Singleton
class SocketManager @Inject constructor() {

    private var mSocket: Socket? = null

    // Map de eventos: eventName -> Flow<JSONObject>
    private val eventListeners = mutableMapOf<String, MutableSharedFlow<JSONObject>>()

    companion object {
        private const val TAG = "SocketManager"
    }

    /**
     * Conecta al servidor Socket.io
     * Safe para llamar múltiples veces (solo conecta si no está conectado)
     */
    fun connect() {
        if (mSocket?.connected() == true) {
            Log.d(TAG, "Socket ya está conectado")
            return
        }

        try {
            Log.d(TAG, "Conectando a Socket en: ${BuildConfig.SOCKET_URL}")

            val opts = IO.Options.builder()
                .setReconnection(true)
                .setReconnectionDelay(1000)
                .setReconnectionDelayMax(5000)
                .setReconnectionAttempts(Integer.MAX_VALUE)
                .build()

            mSocket = IO.socket(BuildConfig.SOCKET_URL, opts).apply {
                // Eventos de conexión
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "✓ Conexión Socket establecida")
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.w(TAG, "✗ Socket desconectado, intentando reconectar...")
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "✗ Error de conexión: ${args.firstOrNull()}")
                }

                // Registra listeners de eventos dinámicamente
                for ((eventName, flow) in eventListeners) {
                    registerEventListener(eventName, flow)
                }

                connect()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error al conectar Socket: ${e.message}", e)
        }
    }

    /**
     * Se suscribe a un evento específico
     * Retorna un Flow de JSONObject que emite cada vez que llega el evento
     */
    fun on(eventName: String): SharedFlow<JSONObject> {
        // Si no existe el listener, crea uno nuevo
        val listener = eventListeners.getOrPut(eventName) {
            MutableSharedFlow<JSONObject>(extraBufferCapacity = 10)
        }

        // Si Socket ya está conectado, registra el listener
        if (mSocket?.connected() == true) {
            registerEventListener(eventName, listener)
        }

        return listener.asSharedFlow()
    }

    /**
     * Emite un evento hacia el servidor
     */
    fun emit(eventName: String, data: JSONObject) {
        if (mSocket?.connected() == true) {
            mSocket?.emit(eventName, data)
            Log.d(TAG, "Evento emitido: $eventName")
        } else {
            Log.w(TAG, "Socket no está conectado, no se puede emitir $eventName")
        }
    }

    /**
     * Desconecta del servidor
     */
    fun disconnect() {
        try {
            mSocket?.disconnect()
            mSocket?.off()
            eventListeners.clear()
            Log.d(TAG, "Socket desconectado")
        } catch (e: Exception) {
            Log.e(TAG, "Error al desconectar: ${e.message}", e)
        }
    }

    /**
     * Registra un listener para un evento específico
     * Uso interno
     */
    private fun registerEventListener(eventName: String, flow: MutableSharedFlow<JSONObject>) {
        mSocket?.on(eventName) { args ->
            try {
                if (args.isNotEmpty()) {
                    val jsonObject = when (args[0]) {
                        is JSONObject -> args[0] as JSONObject
                        is String -> JSONObject(args[0] as String)
                        else -> JSONObject().put("data", args[0].toString())
                    }
                    flow.tryEmit(jsonObject)
                    Log.d(TAG, "Evento escuchado: $eventName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando evento $eventName: ${e.message}")
                flow.tryEmit(JSONObject().put("error", e.message))
            }
        }
    }

}
