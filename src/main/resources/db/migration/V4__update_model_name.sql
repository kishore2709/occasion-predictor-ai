UPDATE model_versions
SET model_name = 'llama3'
WHERE model_name = 'llama3.1'
  AND provider = 'OLLAMA';
