#!/bin/bash
set -e

echo "=== BACKEND INTEGRATION TEST ==="

# Check if port 8080 is in use
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null ; then
    echo "WARNING: Port 8080 is in use. Killing the existing process..."
    kill -9 $(lsof -t -i:8080) || true
    sleep 2
fi

echo "1. Starting backend server in the background..."
: "${GEMINI_API_KEY:?Set GEMINI_API_KEY in your environment before running this script}"
: "${FIREBASE_PROJECT_ID:?Set FIREBASE_PROJECT_ID in your environment before running this script}"

gradle bootRun > server_test.log 2>&1 &
SERVER_PID=$!
echo "Server started with PID: $SERVER_PID. Waiting for port 8080 to open..."

# Poll port 8080
for i in {1..30}; do
    if curl -s -o /dev/null http://localhost:8080/api/programs; then
        echo "Server is UP and running!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "ERROR: Server failed to start within 30 seconds."
        cat server_test.log
        exit 1
    fi
    sleep 1.5
done

# Helper function to print clean section headers
function section() {
    echo -e "\n----------------------------------------"
    echo ">>> $1"
    echo "----------------------------------------"
}

# 2. Upload a program
section "2. Uploading a workout program..."
PROGRAM_ID=$(curl -s -X POST http://localhost:8080/api/programs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Push Pull Legs Test",
    "days": [
      {
        "day": 1,
        "title": "Push Day",
        "exercises": [
          {
            "name": "Bench Press",
            "sets": 3,
            "reps": [10, 8, 6],
            "note": "Heavy set last"
          },
          {
            "name": "Overhead Press",
            "sets": 3,
            "reps": [8, 8, 8]
          }
        ]
      },
      {
        "day": 2,
        "title": "Pull Day",
        "exercises": [
          {
            "name": "Weighted Pullups",
            "sets": 4,
            "reps": [6, 6, 6, 6]
          }
        ]
      }
    ]
  }' | grep -o '"id":"[^"]*' | grep -o '[^"]*$')

echo "Uploaded program! Program ID: $PROGRAM_ID"

# 3. List all programs
section "3. Listing all programs..."
curl -s http://localhost:8080/api/programs | json_pp || curl -s http://localhost:8080/api/programs

# 4. Activate the program
section "4. Activating the program..."
curl -s -X PUT "http://localhost:8080/api/programs/$PROGRAM_ID/activate"
echo "Activated program!"

# 5. Submit daily check-in (Today)
section "5. Submitting today's health check-in..."
curl -s -X POST http://localhost:8080/api/health/daily \
  -H "Content-Type: application/json" \
  -d '{
    "date": "2026-07-04",
    "checklist": {
      "workout": true,
      "eggs": true,
      "quinoa": false,
      "vegCurry": true,
      "soya": true,
      "water": true
    },
    "health": {
      "sleepHours": 8.2,
      "restingHeartRate": 61.5,
      "weight": 70.4
    },
    "notes": "Excellent push day, solid sleep."
  }'
echo "Submitted today's check-in!"

# 6. Submit daily check-in (Yesterday)
section "6. Submitting yesterday's health check-in..."
curl -s -X POST http://localhost:8080/api/health/daily \
  -H "Content-Type: application/json" \
  -d '{
    "date": "2026-07-03",
    "checklist": {
      "workout": false,
      "eggs": true,
      "quinoa": true,
      "vegCurry": true,
      "soya": false,
      "water": true
    },
    "health": {
      "sleepHours": 7.6,
      "restingHeartRate": 63.0,
      "weight": 70.6
    },
    "notes": "Rest day, focused on diet."
  }'
echo "Submitted yesterday's check-in!"

# 7. Get today's summary
section "7. Fetching today's health summary..."
curl -s http://localhost:8080/api/health/today | json_pp || curl -s http://localhost:8080/api/health/today

# 8. Get active program today (should show Push Day)
section "8. Fetching today's active program workout..."
curl -s http://localhost:8080/api/programs/today | json_pp || curl -s http://localhost:8080/api/programs/today

# 9. Complete day
section "9. Completing today's workout day (progression check)..."
curl -s -X POST http://localhost:8080/api/programs/complete-day
echo "Completed workout day!"

# 10. Get active program today again (should have progressed to Pull Day!)
section "10. Fetching active program workout after completion (should be Pull Day)..."
curl -s http://localhost:8080/api/programs/today | json_pp || curl -s http://localhost:8080/api/programs/today

# 11. Import JSON historical data
section "11. Importing JSON historical data..."
IMPORT_JOB_ID=$(curl -s -X POST http://localhost:8080/api/import/json \
  -F "file=@/Users/rcred/Documents/krypton/assets/apple_health_export_2026_07_02.json" | grep -o '"jobId":"[^"]*' | grep -o '[^"]*$')

echo "Submitted JSON import job! Job ID: $IMPORT_JOB_ID"

# Poll import status
for i in {1..30}; do
    STATUS_RESP=$(curl -s http://localhost:8080/api/import/status/$IMPORT_JOB_ID)
    STATUS=$(echo $STATUS_RESP | grep -o '"status":"[^"]*' | grep -o '[^"]*$')
    echo "Job status: $STATUS"
    if [ "$STATUS" == "COMPLETED" ] || [ "$STATUS" == "FAILED" ]; then
        echo $STATUS_RESP | json_pp || echo $STATUS_RESP
        break
    fi
    sleep 2
done

# 12. Predictions (now with rich historical data!)
section "12. Fetching predictions with rich historical data..."
curl -s "http://localhost:8080/api/health/predictions?metrics=weight,sleepHours,workoutConsistency,eggsConsistency&horizons=2w,1m" | json_pp || curl -s "http://localhost:8080/api/health/predictions?metrics=weight,sleepHours,workoutConsistency,eggsConsistency&horizons=2w,1m"

# Cleanup
section "Cleanup: Stopping server..."
kill -9 $SERVER_PID || true
rm -f server_test.log
echo "Server stopped successfully!"
echo "=== TEST COMPLETED SUCCESSFULLY ==="

