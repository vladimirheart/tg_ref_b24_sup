# 01-211 Phase E8 — edge nginx template-dir bootstrap recovery

Date: 2026-08-26 13:15 +03:00
Task: 01-211

## Runtime evidence

The post-E7 smoke proved that the application topology itself is healthy:

```text
db-migrate: exited 0
ops-worker-1/2: healthy
panel-web-1/2: healthy
panel-direct: healthy
```

The public edge container alone was restarting:

```text
nginx: Restarting (1)
cp: can't create '/etc/nginx/templates/default.conf.template': No such file or directory
```

## Root cause

`docker-compose.production-edge.yml` selects either the TLS or HTTP-only source
template and copies it to the official nginx envsubst input path:

```text
/etc/nginx/templates/default.conf.template
```

The stock image does not guarantee that this directory exists at the point where
the custom selection command runs. The command therefore exits before its
intentional second `/docker-entrypoint.sh` pass can render the template.

## Recovery

- create `/etc/nginx/templates` before either copy operation;
- retain the existing selected-template + official nginx entrypoint/envsubst flow;
- add an ordering source-contract (`mkdir` before both `cp` operations);
- run an isolated HTTP-only nginx template bootstrap probe ending in `nginx -t`;
- validate the composed production model and `git diff --check`.

No backend runtime role behavior changes in E8.

## Status

Task remains `🟡` until the full Docker role/scale smoke is green.
