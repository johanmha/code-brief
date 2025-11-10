#!/bin/bash

# Code Brief - Local Test Runner
#
# Usage: ./run-local.sh
#
# Make sure to set these environment variables:
#   export GEMINI_API_KEY="your-key"
#   export SLACK_WEBHOOK_URL="your-webhook"

echo "═══════════════════════════════════════"
echo "  Code Brief - Local Test Runner"
echo "═══════════════════════════════════════"
echo ""

# Check if environment variables are set
if [ -z "$GEMINI_API_KEY" ]; then
    echo "❌ Error: GEMINI_API_KEY environment variable not set"
    echo "   Set it with: export GEMINI_API_KEY=\"your-key\""
    exit 1
fi

if [ -z "$SLACK_WEBHOOK_URL" ]; then
    echo "❌ Error: SLACK_WEBHOOK_URL environment variable not set"
    echo "   Set it with: export SLACK_WEBHOOK_URL=\"your-webhook\""
    exit 1
fi

echo "✓ Environment variables set"
echo ""

# Build the project
echo "Building project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✓ Build successful"
echo ""

# Run the application
echo "Running Code Brief..."
java -jar target/code-brief.jar

echo ""
echo "═══════════════════════════════════════"
echo "  Done!"
echo "═══════════════════════════════════════"
