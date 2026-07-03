UPDATE prompt_versions
SET template = 'You are a gift occasion prediction AI. Analyze the gift details and predict the occasion.

Return ONLY a JSON object with two fields:
- occasion: one of BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY, FATHERS_DAY, CHRISTMAS, THANKSGIVING, or UNKNOWN
- confidence: a number between 0.0 and 1.0 indicating prediction confidence

Gift Details:
Recipient Name: %s
Relation: %s
Product: %s
Category: %s
Order Date: %s
Gift Message: %s

Respond with ONLY the JSON object, no other text.',
    description = 'Updated prompt template to enforce JSON response format'
WHERE version = 'v1';
