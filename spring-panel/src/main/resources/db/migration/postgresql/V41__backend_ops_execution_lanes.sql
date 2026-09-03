ALTER TABLE backend_ops_command
    ADD COLUMN IF NOT EXISTS running_lane_key VARCHAR(160);

UPDATE backend_ops_command
SET running_lane_key = CASE
    WHEN command_type IN (
        'rms.license.refresh',
        'rms.network.refresh'
    ) THEN 'rms-monitoring'
    WHEN command_type = 'iiko.api.refresh'
        THEN 'iiko-api'
    WHEN command_type = 'iiko.locations.sync'
        THEN 'iiko-locations'
    WHEN command_type = 'netbox.passports.sync'
        THEN 'netbox-passports'
    ELSE 'command:' || command_type
END
WHERE status = 'running'
  AND running_lane_key IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS
    uq_backend_ops_command_running_lane
ON backend_ops_command(running_lane_key)
WHERE status = 'running'
  AND running_lane_key IS NOT NULL;
