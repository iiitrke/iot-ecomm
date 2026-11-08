#pragma once
// Copy this file to secrets.h and fill in your values. secrets.h is git-ignored.

#define WIFI_SSID       "your-wifi-ssid"
#define WIFI_PASSWORD   "your-wifi-password"

// Must match the backend `devices` table (seeded: esp32-kitchen-01 / change-me-device-key).
#define DEVICE_ID       "esp32-kitchen-01"
#define DEVICE_API_KEY  "change-me-device-key"

// Host must be one of the names/IPs in the backend HTTPS certificate (see scripts/gen-certs.sh).
#define BACKEND_HOST    "192.168.1.50"
#define BACKEND_PORT    8443
