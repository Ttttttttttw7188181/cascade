# Cascade privacy policy

Cascade is a local proxy client. It does not run its own analytics, advertising or
tracking servers, and it does not send your configuration or traffic logs to the
developer.

## What stays on the device

- Server profiles, subscriptions, routing rules, the selected server and app settings
  are stored in the app's private storage.
- The optional Shizzi automation token is stored in the same private storage and is
  only sent to the Shizzi app on this device.
- Logs are written locally and are only shared when you explicitly export them.

## Network connections

Cascade connects only to the servers and subscription URLs that you add. Subscription
updates, delay tests and IP lookups request the URLs configured in the app (by default
`generate_204` endpoints and `api.ip.sb`). Those services see your IP address and the
request headers, exactly as they would from a browser.

If you enable channel aggregation, the Xray connection is dialed through the SOCKS5
balancer address that you enter. That balancer is a separate app or service and follows
its own privacy policy.

If you enable hotspot sharing, Cascade sends start/stop commands to the Shizzi app.
Shizzi performs the tethering work and follows its own privacy policy.

## Permissions

- `INTERNET` and network state: connect to proxies and subscriptions.
- `BIND_VPN_SERVICE` / foreground service: run the tunnel.
- `POST_NOTIFICATIONS`: show the foreground-service notification.
- `QUERY_ALL_PACKAGES` and the `<queries>` entries: let the per-app proxy picker list
  apps and let Cascade detect Shizzi/Shizuku. The app does not upload the package list.

## Removing data

Uninstalling Cascade removes its private storage. Exported backups and logs are files
that you created and must be deleted separately.

Last updated: 2026-11-10
