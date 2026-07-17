INSERT INTO team (id, name, contact_channel) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Payments', '#team-payments'),
    ('10000000-0000-0000-0000-000000000002', 'Commerce', '#team-commerce'),
    ('10000000-0000-0000-0000-000000000003', 'Platform', '#team-platform');

INSERT INTO fleet_service (id, name, owner_team_id, tier) VALUES
    ('20000000-0000-0000-0000-000000000001', 'payments-api',
     '10000000-0000-0000-0000-000000000001', 'CRITICAL'),
    ('20000000-0000-0000-0000-000000000002', 'checkout-web',
     '10000000-0000-0000-0000-000000000002', 'CRITICAL'),
    ('20000000-0000-0000-0000-000000000003', 'catalog-api',
     '10000000-0000-0000-0000-000000000002', 'STANDARD');

INSERT INTO service_allowed_action (service_id, action_type) VALUES
    ('20000000-0000-0000-0000-000000000001', 'RESTART_SERVICE'),
    ('20000000-0000-0000-0000-000000000001', 'ROLLBACK_DEPLOYMENT'),
    ('20000000-0000-0000-0000-000000000001', 'SCALE_OUT'),
    ('20000000-0000-0000-0000-000000000002', 'RESTART_SERVICE'),
    ('20000000-0000-0000-0000-000000000002', 'ROLLBACK_DEPLOYMENT'),
    ('20000000-0000-0000-0000-000000000003', 'RESTART_SERVICE'),
    ('20000000-0000-0000-0000-000000000003', 'CLEAR_CACHE');

INSERT INTO runbook (id, title, symptom_description, steps, action_type) VALUES
    ('30000000-0000-0000-0000-000000000001',
     'Rollback a faulty service deployment',
     'Error rate or latency rises immediately after a deployment.',
     'Confirm the deployment correlation; identify the last known-good version; execute a guarded rollback; verify recovery metrics.',
     'ROLLBACK_DEPLOYMENT'),
    ('30000000-0000-0000-0000-000000000002',
     'Restart an unhealthy service instance',
     'A bounded set of instances is unhealthy while dependencies remain healthy.',
     'Confirm restart is allowlisted; restart one instance; verify readiness and error rate before proceeding.',
     'RESTART_SERVICE'),
    ('30000000-0000-0000-0000-000000000003',
     'Scale out a saturated service',
     'Sustained CPU or request concurrency is high without a correlated bad deployment.',
     'Confirm downstream capacity; increase replicas by one bounded step; observe saturation and latency.',
     'SCALE_OUT');
