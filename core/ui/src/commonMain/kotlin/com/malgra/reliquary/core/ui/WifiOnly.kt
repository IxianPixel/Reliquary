package com.malgra.reliquary.core.ui

/**
 * Whether downloads can wait for Wi-Fi on this platform. Desktop can't tell a
 * metered network from an unmetered one, so screens hide the Wi-Fi only options there.
 */
expect val wifiOnlySupported: Boolean
