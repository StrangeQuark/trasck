# Webhook Signatures

Trasck signs real outbound webhook deliveries when the webhook has a configured secret. Dry-run deliveries do not leave Trasck and do not include signature headers.

## Headers

Each signed delivery includes:

| Header | Description |
| --- | --- |
| `X-Trasck-Event-Type` | Event type configured for the delivery. |
| `X-Trasck-Webhook-Id` | Webhook configuration ID. |
| `X-Trasck-Webhook-Delivery-Id` | Durable delivery row ID. Use this for idempotency. |
| `X-Trasck-Webhook-Timestamp` | Unix epoch seconds when Trasck signed the request. |
| `X-Trasck-Webhook-Signature-Key-Id` | Non-secret ID for the webhook secret used to sign the request. |
| `X-Trasck-Webhook-Signature` | `sha256=` followed by a lowercase hex HMAC-SHA256 digest. |

## Verification

Receivers should verify each delivery before processing it:

1. Reject missing timestamp, key ID, or signature headers.
2. Reject stale timestamps. A five-minute replay window is a good default.
3. Look up the webhook secret by `X-Trasck-Webhook-Signature-Key-Id`.
4. Build the signed payload as `timestamp + "." + raw_request_body`.
5. Compute HMAC-SHA256 with the configured secret and compare `sha256=` plus the hex digest to `X-Trasck-Webhook-Signature` using a constant-time comparison.
6. Deduplicate successful processing by `X-Trasck-Webhook-Delivery-Id`.

Example pseudo-code:

```text
signed_payload = timestamp + "." + raw_body
expected = "sha256=" + hmac_sha256_hex(secret_for_key_id, signed_payload)
valid = constant_time_equals(expected, received_signature)
```

## Secret Rotation

Creating a webhook secret assigns a `secretKeyId` in the webhook response. Updating the webhook with a new `secret` rotates the encrypted signing secret and issues a new `secretKeyId`.

Receivers should support overlap during rotation:

- Store the old and new secret by key ID during rollout.
- Accept either key ID until all queued deliveries using the old key have finished or expired.
- Remove the old key after the delivery retry window has passed.

Trasck never returns the raw webhook secret after write. The API only exposes whether a secret is configured and the current non-secret key ID.
