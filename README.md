# IoTEcomm: Button-Based Ordering with ESP32

A displayless ordering device. Each product has a button and a row of 5 LEDs. Pressing a button adds one unit and lights one more LED. **SUBMIT** sends the order over HTTPS to a backend. The backend publishes it to **Kafka over SSL (mutual TLS)**, and a consumer writes each order as **Markdown committed to a git repository** (no database).

```
 ┌──────────── ESP32 device ─────────────┐
 │ 4 product buttons ─► 20 qty LEDs      │
 │ SUBMIT button   SUCCESS LED  PENDING  │      HTTPS (TLS, CA-pinned)
 │ WiFi ─────────────────────────────────┼───── X-Device-Id / X-Api-Key ──┐
 └───────────────────────────────────────┘                                 │
                                                                           ▼
                        ┌───────────── Spring Boot backend :8443 ───────────────┐
                        │ POST /api/device/orders → validate, price → Producer ─┼──┐
                        │ GET  /api/device/orders/pending  (drives PENDING LED) │  │ Kafka SSL
                        │ /api/admin/orders (list, change status)               │  │ (mTLS)
                        │                                                       │  ▼
                        │ Consumer ◄────────────── topic iot.orders ◄──── Kafka (KRaft)
                        │    │                                                  │
                        │    ▼                                                  │
                        │ GitOrderLedger: orders/<id>.md + ORDERS.md → git commit (+ push)
                        └───────────────────────────────────────────────────────┘
```

Why the ESP32 doesn't talk to Kafka directly: there is no solid Kafka client for the ESP32, and putting Kafka credentials on a device is risky. The device makes one authenticated HTTPS call, and the backend handles Kafka with SSL.

## Repository Layout

```
firmware/                 ESP32 (PlatformIO, Arduino framework)
  include/config.h          pins, SKUs, timings
  include/secrets.example.h copy to secrets.h (WiFi, device key, backend host)
  src/main.cpp              buttons, LEDs (74HC595), HTTPS, pending polling
backend/                  Spring Boot 3 / Java 17
  .../api                   device + admin REST endpoints
  .../kafka                 OrderEvent, producer, consumer
  .../ledger                GitOrderLedger: Markdown + git (JGit)
  src/main/resources/application.yml   products, devices, Kafka SSL, ledger
scripts/gen-certs.sh      creates CA + all certificates + firmware/include/ca_cert.h
scripts/simulate-device.sh  place a test order without hardware
docs/HARDWARE.md          component list (BOM), pin map, wiring
docker-compose.yml        Kafka (SSL) + backend
```

## Quick Start

### 1. Certificates
```bash
./scripts/gen-certs.sh                      # auto-detects LAN IP
./scripts/gen-certs.sh iotecomm.local 192.168.1.50   # or set hostname / IP explicitly
```
This creates `certs/` (Kafka broker, backend Kafka client, backend HTTPS) and `firmware/include/ca_cert.h`.
Re-run it if your computer's LAN IP changes, then re-flash the ESP32.

### 2. Kafka + backend
```bash
docker compose up -d --build
```
Orders land in `./order-ledger/` (a git repository). To push every order to a remote repository:
```bash
LEDGER_REMOTE_URL=https://github.com/<you>/<orders-repo>.git \
LEDGER_USERNAME=<you> LEDGER_TOKEN=<personal-access-token> \
docker compose up -d --build
```
To run the backend outside Docker (Kafka still in Docker on `localhost:19093`):
```bash
docker compose up -d kafka
cd backend && mvn spring-boot:run
```

### 3. Test without hardware
```bash
./scripts/simulate-device.sh localhost:8443 MILK-1L=2 EGGS-12=1
cat order-ledger/ORDERS.md && git -C order-ledger log --oneline
```

### 4. Flash the ESP32
```bash
cp firmware/include/secrets.example.h firmware/include/secrets.h   # edit WiFi, BACKEND_HOST (your LAN IP)
cd firmware && pio run -t upload && pio device monitor
```
Wire it according to **[docs/HARDWARE.md](docs/HARDWARE.md)**, which has the full component list.

## Using the Device

| Action | Result |
|--------|--------|
| Press a product button | Quantity +1 (one more LED). After 5 it wraps to 0. |
| Hold a product button 1.5 s | Clear that product |
| Press SUBMIT | Order sent. SUCCESS LED is solid for 3 s if it succeeded, and blinks if not (see HARDWARE.md). |
| PENDING LED | On while you have an order that is PENDING, CONFIRMED or SHIPPED. Updated every 30 s. |

## Order Ledger (Markdown + git)

Every new order and every status change is one commit:

```
order-ledger/
  ORDERS.md                 index table: order, time, device, items, total, status
  orders/<orderId>.md       front matter (status, totals, timestamps) + items table
```
The backend reloads this folder at startup, so state survives restarts. If a push fails, the commit stays local and goes out with the next successful push.

## API

Device endpoints use the headers `X-Device-Id` and `X-Api-Key`:

| Method | Path | Body / Response |
|--------|------|-----------------|
| POST | `/api/device/orders` | `{"clientOrderId":"abc","items":[{"sku":"MILK-1L","quantity":2}]}` → `202 {"orderId","status":"ACCEPTED","totalCents"}` |
| GET | `/api/device/orders/pending` | `{"pendingCount":1}` |

Admin endpoints use the header `X-Admin-Key`:

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/orders?status=PENDING` | List orders (status filter optional) |
| PATCH | `/api/admin/orders/{id}/status` | `{"status":"DELIVERED"}`. Valid values: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED. |

```bash
curl --cacert certs/ca/ca.crt -H 'X-Admin-Key: change-me-admin-key' https://localhost:8443/api/admin/orders
```

Error codes: `401` for bad credentials, `422` for an unknown SKU or bad quantity, `503` when Kafka is unavailable (the device keeps the cart so it can retry).

## Configuration

| What | Where |
|------|-------|
| Products (SKU, name, price) | `backend/.../application.yml` → `iotecomm.products` **and** `firmware/include/config.h` → `PRODUCT_SKUS` |
| Devices + API keys | `application.yml` → `iotecomm.devices` (`api-key-sha256` = `echo -n KEY \| shasum -a 256`) |
| Admin key | env `ADMIN_API_KEY` |
| Cert password | env `CERT_PASSWORD` (same value for `gen-certs.sh` and the backend) |
| Ledger | env `LEDGER_DIR`, `LEDGER_REMOTE_URL`, `LEDGER_USERNAME`, `LEDGER_TOKEN`, `LEDGER_BRANCH` |

## Reliability and Security

- **TLS everywhere.** ESP32 → backend uses HTTPS verified against your private CA. Backend → Kafka uses SSL with client certificates (`ssl.client.auth=required`) and hostname verification.
- **No duplicate orders on retry.** The device reuses its `clientOrderId` until the cart changes. The backend derives the order id from (device, clientOrderId), and the ledger ignores duplicates.
- **Durable before "success".** The backend waits for the Kafka ack (`acks=all`, idempotent producer) before replying 202.
- **Correct clock.** The ESP32 syncs NTP before making TLS calls.
- **Before production:** change the device key, `ADMIN_API_KEY` and `CERT_PASSWORD`, and keep `certs/` and `secrets.h` out of git (both are already in `.gitignore`).

## Troubleshooting

| Symptom | Check |
|---------|-------|
| SUCCESS blinks 3× slowly every time | Is `BACKEND_HOST` in `secrets.h` the IP/name used in `gen-certs.sh`? Is port 8443 open? Watch the serial monitor. |
| TLS handshake fails on ESP32 | Re-run `gen-certs.sh` with the right IP, then rebuild and flash (ca_cert.h changed). |
| On-board LED keeps blinking | WiFi credentials. The ESP32 only supports 2.4 GHz networks. |
| SUCCESS blinks 8× fast | Device id/key don't match `application.yml`, or a SKU is missing from the products list. |
