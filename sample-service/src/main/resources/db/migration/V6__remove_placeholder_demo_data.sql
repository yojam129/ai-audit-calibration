DELETE FROM instrument_run
WHERE run_no IN ('RUN-DEMO-001', 'RUN-DEMO-002');

DELETE FROM detection_order
WHERE order_no IN ('ORDER-DEMO-001', 'ORDER-DEMO-002');

DELETE FROM sample
WHERE sample_no IN ('SAMPLE-DEMO-001', 'SAMPLE-DEMO-002');

DELETE FROM cartridge
WHERE cartridge_no = 'CARD-DEMO-001';

DELETE FROM reagent_lot
WHERE lot_no = 'LOT-2026-001';
