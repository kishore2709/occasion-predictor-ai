INSERT INTO prompt_versions (version, template, description, active)
VALUES ('v1',
        'Predict the gift occasion.

Return only one occasion from:
BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY,
FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN.

Recipient Name: %s
Relation: %s
Product: %s
Category: %s
Order Date: %s
Gift Message: %s',
        'Initial prompt template for occasion prediction',
        TRUE);

INSERT INTO model_versions (model_name, model_version, provider, active)
VALUES ('llama3.1', 'latest', 'OLLAMA', TRUE);
