package com.dohex.hyperrose.ui

import com.dohex.hyperrose.ui.state.ConnectionTransport
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.state.RoseDeviceItem

typealias AppController = DeviceControlStore
typealias UiConnectionState = DeviceConnectionState
typealias ControlTransport = ConnectionTransport
typealias RoseDevice = RoseDeviceItem
