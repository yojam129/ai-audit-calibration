ALTER TABLE import_batch
    ADD UNIQUE KEY uk_import_asset_business_template
        (asset_id, business_type, template_version);
