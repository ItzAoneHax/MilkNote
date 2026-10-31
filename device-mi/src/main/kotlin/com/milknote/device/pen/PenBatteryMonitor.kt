package com.milknote.device.pen

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * 笔电量(DESIGN-V0 §3.3):蓝牙 GATT Battery Service(0x180F / 0x2A19)读取 + 订阅。
 *
 * 策略(免扫描,离线可用):BLUETOOTH_CONNECT 授权后,在**已配对** LE/DUAL 设备里
 * 优先连接触控笔特征名的设备,发现电池服务即读初值并订阅通知;
 * 无笔 / 无蓝牙 / 未授权 / 模拟器 → [UNKNOWN](UI 显示 `--%` 降级)。
 * 识别名单与真名随 P2 真机验证回填(PEN-ADAPTATION §7)。
 */
class PenBatteryMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    companion object {
        const val UNKNOWN = -1
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** 触控笔特征名(候选,待 P2 回填真名) */
        private val PEN_NAME_HINT = Regex("(pen|stylus|focus|pencil|焦点|触控笔)", RegexOption.IGNORE_CASE)
        private const val CONNECT_TIMEOUT_MS = 6_000L
    }

    private val _level = MutableStateFlow(UNKNOWN)
    val level: StateFlow<Int> = _level

    /** 电量来源设备名(诊断留档;null = 未识别) */
    private val _sourceName = MutableStateFlow<String?>(null)
    val sourceName: StateFlow<String?> = _sourceName

    val permissionGranted: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

    private var gatt: BluetoothGatt? = null
    private var probeJob: Job? = null
    private var receiverRegistered = false

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_ON -> refresh()
                        BluetoothAdapter.STATE_OFF -> { closeGatt(); _level.value = UNKNOWN }
                    }
                }
                // 笔磁吸吸附 → ACL 重连:此前未识别到时重试
                BluetoothDevice.ACTION_ACL_CONNECTED -> if (_level.value == UNKNOWN) refresh()
            }
        }
    }

    /** 权限/蓝牙状态可能变化时调用(授权回调、设置页进入、ACL 事件) */
    fun refresh() {
        registerReceiverOnce()
        probeJob?.cancel()
        if (!permissionGranted) {
            _level.value = UNKNOWN
            return
        }
        probeJob = scope.launch(Dispatchers.IO) { probeBonded() }
    }

    fun stop() {
        probeJob?.cancel()
        closeGatt()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(adapterReceiver) }
            receiverRegistered = false
        }
    }

    private fun registerReceiverOnce() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context, adapterReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        receiverRegistered = true
    }

    private fun closeGatt() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _sourceName.value = null
    }

    // ---- 探测:已配对 LE 设备,笔特征名优先 ----

    private fun adapter(): BluetoothAdapter? = runCatching {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }.getOrNull()

    private suspend fun probeBonded() {
        val adapter = adapter() ?: run { _level.value = UNKNOWN; return }
        if (!adapter.isEnabled) { _level.value = UNKNOWN; return }
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
        val penLike = bonded.filter { PEN_NAME_HINT.containsMatchIn(it.name ?: "") }
        val ordered = penLike.ifEmpty {
            // 唯一 LE 配对设备时视为笔(平板常见形态:只有笔走 LE 配对)
            if (bonded.size == 1) bonded else emptyList()
        }
        for (device in ordered) {
            if (connectAndSubscribe(device)) return
        }
        _level.value = UNKNOWN
    }

    private fun onBatteryValue(value: Int) {
        _level.value = value.coerceIn(0, 100)
    }

    private suspend fun connectAndSubscribe(device: BluetoothDevice): Boolean {
        val ready = CompletableDeferred<Boolean>()
        val callbacks = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runCatching { g.discoverServices() }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // 笔休眠/走远:电量转未知,等 ACL 重连再试
                    if (!ready.isCompleted) ready.complete(false)
                    _level.value = UNKNOWN
                    runCatching { g.close() }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                ready.complete(status == BluetoothGatt.GATT_SUCCESS)
            }

            override fun onCharacteristicRead(
                g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int,
            ) {
                if (characteristic.uuid == BATTERY_LEVEL && status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                    onBatteryValue(value[0].toInt())
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("API 33 起走 ByteArray 重载")
            override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < 33) {
                    val v = characteristic.value
                    if (characteristic.uuid == BATTERY_LEVEL && status == BluetoothGatt.GATT_SUCCESS &&
                        v != null && v.isNotEmpty()
                    ) onBatteryValue(v[0].toInt())
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid == BATTERY_LEVEL && value.isNotEmpty()) onBatteryValue(value[0].toInt())
            }

            @Suppress("DEPRECATION")
            @Deprecated("API 33 起走 ByteArray 重载")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < 33) {
                    val v = characteristic.value
                    if (characteristic.uuid == BATTERY_LEVEL && v != null && v.isNotEmpty()) onBatteryValue(v[0].toInt())
                }
            }
        }

        var g: BluetoothGatt? = null
        try {
            g = device.connectGatt(context, false, callbacks, BluetoothDevice.TRANSPORT_LE) ?: return false
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { ready.await() } == true
            if (!ok) {
                runCatching { g.disconnect(); g.close() }
                return false
            }
            val svc = g.getService(BATTERY_SERVICE)
            val ch = svc?.getCharacteristic(BATTERY_LEVEL)
            if (ch == null) {
                runCatching { g.disconnect(); g.close() }
                return false
            }
            // 读初值 + 订阅通知(CCCD)
            runCatching { g.readCharacteristic(ch) }
            runCatching { g.setCharacteristicNotification(ch, true) }
            ch.getDescriptor(CCCD)?.let { cccd ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= 33) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
            }
            closeGatt() // 换新连接前释放旧的
            gatt = g
            _sourceName.value = device.name
            return true
        } catch (_: SecurityException) {
            runCatching { g?.disconnect(); g?.close() }
            return false
        } catch (_: Throwable) {
            runCatching { g?.disconnect(); g?.close() }
            return false
        }
    }
}

/** 诊断面板设备信息留档(PEN-ADAPTATION §3.5:两台平板分别采样记录) */
object DeviceInfo {

    /** HyperOS/MIUI 版本(系统属性反射读取;非小米机型返回空) */
    val hyperOsVersion: String by lazy {
        runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java)
            (get.invoke(null, "ro.miui.ui.version.name") as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { return@lazy "HyperOS $it" }
            (get.invoke(null, "ro.miui.version") as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { return@lazy "MIUI $it" }
            ""
        }.getOrDefault("")
    }

    fun summary(context: Context): String {
        val dm = context.resources.displayMetrics
        val os = hyperOsVersion.ifEmpty { "Android ${Build.VERSION.RELEASE}" }
        return "$os (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL} · ${dm.widthPixels}×${dm.heightPixels}@${dm.densityDpi}dpi"
    }

    /** 笔电量来源(诊断留档用) */
    fun batteryLine(source: String?, level: Int): String =
        if (level < 0) "battery=--%(无 GATT 来源)" else "battery=$level% source=${source ?: "unknown"}"
}
