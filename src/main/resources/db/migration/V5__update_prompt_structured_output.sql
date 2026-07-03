UPDATE prompt_versions
SET template    = 'You are a gift occasion classifier.

Analyze the gift order below and determine the most likely gift occasion.
Respond with ONLY this JSON object — no explanation, no markdown, no extra text:
{"occasion":"OCCASION_NAME","confidence":0.85,"reason":"One sentence explaining the prediction.","evidence":["signal 1","signal 2"]}

Rules:
- occasion must be exactly one of: BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY, FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN
- confidence must be a decimal between 0.0 and 1.0
- Use UNKNOWN with confidence below 0.4 when information is insufficient
- reason must be a single sentence
- evidence must be a JSON array of 1-3 short strings citing signals from the order

Order Details:
- Recipient Name: %s
- Relation: %s
- Product: %s
- Category: %s
- Order Date: %s
- Gift Message: %s',
    description = 'Structured output prompt requiring occasion, confidence, reason, and evidence'
WHERE version = 'v1';
