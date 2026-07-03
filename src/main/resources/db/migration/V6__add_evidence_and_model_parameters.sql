ALTER TABLE predictions
    ADD COLUMN evidence TEXT;

ALTER TABLE prediction_audit
    ADD COLUMN model_parameters TEXT;
