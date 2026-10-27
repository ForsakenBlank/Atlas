# How Atlas sync works

This is the wire format Atlas speaks over Wi-Fi, written down so other apps (Luminescent already does) can join in. The Kotlin side lives in `shared/src/shared/kotlin/com/forsakenblank/atlas/sync/`.

## Connecting

One device hosts on TCP port **47830** and shows its address and a 6 digit code. The other connects. Each request is its own connection: connect, send one frame, read one frame, close.

A frame is:

```
"ATLS"  |  1 byte type  |  4 byte big endian length  |  body
```

- Type `0` is a sealed JSON message. The body is a 12 byte nonce, then AES-256-GCM ciphertext with the 16 byte tag at the end.
- Type `1` is a plain UTF-8 error message, like `Wrong code`.

The key is `PBKDF2-HMAC-SHA256(code, salt "atlas-sync-1", 120000 rounds, 32 bytes)`. A frame sealed with the wrong code can not be opened, so the host replies `Wrong code` and after ten of those stops hosting.

## Requests

Every request carries `action`, `deviceId`, `peerId` (the same id), `name` and `kind` (`Phone` or `PC`).

| action | sent by | reply |
|---|---|---|
| `hello` | the device connecting | `deviceId`, `name`, `kind`, `app`, `protocol`, and optionally `accepts` |
| `records` | the device connecting | `records`: everything the host has, `baseDigest`: see below |
| `apply` | the device connecting | `{"ok": true}` once the host has written everything |

`apply` carries `syncId` (asking twice with the same one changes nothing), `upserts`, `deletes`, `relinks` and `baseline`.

`accepts` is for a host that only keeps some things, for example `{"kinds": ["item", "log"], "items": ["NOTE", "TRACKER:TIMER"]}`. The connecting device leaves everything else out of the comparison, so it is never mistaken for deleted.

## Records

Every row is `{"kind", "uid", "fields"}`. Kinds are `account`, `item`, `subject`, `slot`, `term`, `log`, `event`, `task`, `countdown`, `entry`, `quick`, `daynote` and `theme`. Fields are the row's own columns with any id pointing at another row swapped for that row's uid (`parentId`, `trackerId`, `subjectId`, `folderId`, `accountId`). An item also carries `note`, `sheet`, `tracker` and `tags` when it has them.

A record's **fingerprint** is the first 22 characters of the unpadded url-safe base64 of `SHA-256(kind + canonical(fields without "created" and "updated"))`. Canonical JSON sorts object keys at every level and writes no spaces. `SyncVectorsTest` pins a few values both sides must agree on.

## Merging

After a sync both devices remember the same **baseline**: every record's key (`kind/uid`) and fingerprint. `baseDigest` is the SHA-256 of the canonical baseline, so the connecting device can check both remember the same thing (if not it compares from scratch).

The connecting device does all the deciding, with its own records, the host's records and the baseline:

- only one side changed something: that side wins
- something new on one side: it goes to the other
- deleted on one side and untouched on the other: it is deleted on both
- changed on both, or changed on one and deleted on the other: the person picks
- made on both before they ever synced (the same tracker name, the inbox): the host's uid is **relinked** to this one and they become one row

Tracker logs and money entries are rows of their own, so time and money from both sides simply add up.
