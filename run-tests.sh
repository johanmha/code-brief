#!/bin/bash

# Code Brief - Test Runner
#
# Runs all tests or specific test classes

echo "═══════════════════════════════════════"
echo "  Code Brief - Test Runner"
echo "═══════════════════════════════════════"
echo ""

if [ "$1" == "collector" ]; then
    echo "Running NewsCollectorService tests..."
    mvn test -Dtest=NewsCollectorServiceTest
elif [ "$1" == "gemini" ]; then
    echo "Running GeminiService tests..."
    mvn test -Dtest=GeminiServiceTest
elif [ "$1" == "slack" ]; then
    echo "Running SlackService tests..."
    mvn test -Dtest=SlackServiceTest
else
    echo "Running all tests..."
    mvn test
fi

echo ""
echo "═══════════════════════════════════════"
echo "  Done!"
echo "═══════════════════════════════════════"
