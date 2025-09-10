#!/bin/bash

# ComposerAI API Test Script
# This script demonstrates the API endpoints

BASE_URL="http://localhost:8080/api"

echo "=== ComposerAI API Test ==="
echo ""

# Test health endpoint
echo "1. Testing Health Endpoint:"
curl -s "${BASE_URL}/chat/health" | jq '.'
echo ""
echo ""

# Test chat endpoint
echo "2. Testing Chat Endpoint:"
curl -s -X POST "${BASE_URL}/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Show me emails about project updates from last week",
    "maxResults": 3
  }' | jq '.'

echo ""
echo ""

# Test validation
echo "3. Testing Validation (empty message):"
curl -s -X POST "${BASE_URL}/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "",
    "maxResults": 5
  }'

echo ""
echo ""
echo "Test complete!"