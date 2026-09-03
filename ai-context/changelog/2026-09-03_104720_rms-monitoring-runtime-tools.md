# RMS monitoring runtime network tools

- Time: 2026-09-03_104720
- Base main: 83ab582e691eef8b24d5db30bf14eef855728187
- File: docker/panel.Dockerfile
- Runtime: ops-worker

## Change

- Add iputils-ping to the panel runtime image.
- Add traceroute to the panel runtime image.
- Add build-time command presence guards.
- Redeploy only ops-worker.

## Reason

RMS monitoring executes external Linux commands "ping" and "traceroute".
The production panel image previously installed neither executable.

## Validation contract

- Read-only production preflight before mutation.
- Candidate image contains ping and traceroute.
- Targeted ops-worker recreate returns to healthy state.
- Post-deploy localhost ping and traceroute smoke passes.
