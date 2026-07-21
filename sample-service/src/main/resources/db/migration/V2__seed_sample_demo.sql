INSERT INTO reagent_lot
    (id, lot_no, reagent_code, manufacturer, manufacture_date, expires_date, status)
VALUES
    (1, 'LOT-2026-001', 'PCR-MULTI-01', '演示试剂厂商',
     '2026-01-10', '2027-01-09', 'ACTIVE')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO cartridge
    (id, cartridge_no, cartridge_type, reagent_lot_id, expires_at, status)
VALUES
    (1, 'CARD-DEMO-001', 'MULTI_CHANNEL', 1, '2027-01-09 23:59:59.000', 'AVAILABLE')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO sample
    (id, sample_no, organization_id, external_no, specimen_type, status,
     collected_at, created_at)
VALUES
    (1, 'SAMPLE-DEMO-001', 'ORG-DEMO', 'EXT-2026-0001', 'SWAB',
     'DETECTED', '2026-07-18 09:30:00.000', CURRENT_TIMESTAMP(3)),
    (2, 'SAMPLE-DEMO-002', 'ORG-DEMO', 'EXT-2026-0002', 'SERUM',
     'PENDING_REVIEW', '2026-07-18 10:15:00.000', CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO detection_order
    (id, order_no, sample_id, assay_code, status, created_at)
VALUES
    (1, 'ORDER-DEMO-001', 1, 'RESP-PANEL', 'COMPLETED', CURRENT_TIMESTAMP(3)),
    (2, 'ORDER-DEMO-002', 2, 'RESP-PANEL', 'COMPLETED', CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO instrument_run
    (id, run_no, order_id, cartridge_id, instrument_no, status, started_at, ended_at)
VALUES
    (1, 'RUN-DEMO-001', 1, 1, 'INS-001', 'COMPLETED',
     '2026-07-18 09:35:00.000', '2026-07-18 10:05:00.000'),
    (2, 'RUN-DEMO-002', 2, 1, 'INS-001', 'COMPLETED',
     '2026-07-18 10:20:00.000', '2026-07-18 10:50:00.000')
ON DUPLICATE KEY UPDATE status = VALUES(status);
