# Occasion Predictor API

A Spring Boot backend service for predicting gift occasions from order and recipient information.

This project is part of the Gift Reminder AI flow.  
The current phase focuses on exposing a Prediction API that accepts order details and returns possible gift occasions using an LLM through Ollama.

---

## Current Scope

### Phase 1: Prediction API

The application currently supports:

- Spring Boot REST API
- Java records for request/response DTOs
- Spring AI integration with Ollama
- Swagger/OpenAPI documentation
- YAML-based configuration
- Clean package separation
- Basic prediction flow from controller to service

---

## Tech Stack

- Java 24
- Spring Boot
- Spring Web
- Spring AI
- Ollama
- Swagger / OpenAPI using springdoc
- Maven

---

## Project Purpose

The goal of this service is to predict possible gift occasions based on order and recipient data.

Example use cases:

- Predict if an order is related to Christmas
- Predict birthdays, anniversaries, weddings, housewarming, Thanksgiving, etc.
- Generate reasons for why an occasion was predicted
- Later use predictions to create gift reminders automatically

---

## High-Level Flow

```text
Client
  |
  | POST /api/v1/predictions
  v
PredictionController
  |
  v
PredictionService
  |
  v
LLM Client / Ollama
  |
  v
PredictionResponse