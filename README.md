# Krypton — Backend HLD

Spring Boot REST API for a personal health & fitness tracking system. Handles daily health metrics ingestion, workout program management, clinical record processing, and predictive trend analysis.

---

## Tech Stack

| Component | Tech | Host |
|-----------|------|------|
| API | Java 21 (Spring Boot 3.x) | Render (free) |
| Build | Gradle (Kotlin DSL) | — |
| Database | Firebase Firestore | Google (free, 1 GB) |
| File Storage | Firebase Storage | Google (free, 5 GB) |
| PDF Extraction | Gemini API (Java SDK) | Google AI Studio (free) |

---

## Implementation Details

| Decision | Choice |
|----------|--------|
| Java | 21 (latest LTS) |
| Framework | Spring Boot 3.x |
| Build Tool | Gradle (Kotlin DSL) |
| Object Construction | Lombok `@Builder` pattern |
| DTO Strategy | Separate request/response DTOs from Firestore document models |
| Validation | Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Min`, etc.) |
| Error Handling | `@RestControllerAdvice` + custom exceptions |
| XML Parsing | StAX (built-in Java stream parser) |
| Async Processing | Spring `@Async` for XML import (returns job ID, client polls) |
| Gemini Integration | Google `google-genai` Java SDK |
| API Docs | None (personal app) |
| Testing | None (initial build) |

### Custom Exceptions

```java
ProgramNotFoundException        // Program ID not found
ImportFailedException            // XML parsing or backfill failure
ClinicalExtractionException      // Gemini extraction failure
InvalidMetricsException          // Validation failure on submitted metrics
DuplicateEntryException          // Daily record already exists for date
```

All handled by a global `@RestControllerAdvice` returning a standard error response:

```json
{
  "status": 404,
  "error": "Program not found",
  "message": "No program exists with ID: abc123",
  "timestamp": "2026-07-01T10:30:00Z"
}
```

### DTO Separation

```
model/          → Firestore document models (stored as-is)
dto/request/    → API request bodies (validated with Jakarta)
dto/response/   → API response bodies (assembled by services)
```

---

## User Profile (Hardcoded)

| Field | Value |
|-------|-------|
| Sex | Male |
| Height | 178 cm |
| DOB | January 27, 1997 |
| Ideal Weight Range | 59–79 kg (BMI 18.5–24.9) |

---

## Data Sources

### Apple Shortcut (automated, daily at 11:55 PM)

Pushes 5 health metrics from Apple Watch via `POST /api/health/daily`:

| Metric | HealthKit Type |
|--------|----------------|
| Sleep | `HKCategoryTypeIdentifierSleepAnalysis` |
| Weight | `HKQuantityTypeIdentifierBodyMass` |
| Blood Glucose | `HKQuantityTypeIdentifierBloodGlucose` |
| Workouts | `Workout` |
| Resting Heart Rate | `HKQuantityTypeIdentifierRestingHeartRate` |

> Blood glucose auto-tagged by timestamp: **before 10 AM = fasting**, **after 10 AM = post-meal (PP)**.

### Manual Check-in (from frontend)

| Item | Type |
|------|------|
| Workout (program day) | boolean |
| Eggs (6) | boolean |
| Quinoa (both meals) | boolean |
| Veg Curry | boolean |
| Soya chunks | boolean |
| Water (hydration goal) | boolean |
| Notes | string |

### XML Backfill (one-time)

- Apple Health `export.xml` (~300 MB, 652K records)
- Stream-parsed (SAX/StAX) — only 5 types extracted, rest skipped
- Aggregated into daily summaries and stored in Firestore

### Clinical Records (periodic)

- PDF lab reports uploaded via API
- Text extracted with Apache PDFBox
- Metrics extracted via Gemini API
- PDF stored in Firebase Storage, metrics stored in Firestore

---

## API Design

Base URL: `https://krypton-api.onrender.com`

All endpoints require `X-API-Key` header.

### Daily Metrics

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/health/daily` | Submit daily metrics (Shortcut or manual check-in) |
| `GET` | `/api/health/today` | Today's summary + 7-day sparklines + vs-ideal comparison |
| `GET` | `/api/health/history?from={date}&to={date}` | Historical daily records |

#### POST /api/health/daily — Request

```json
{
  "date": "2026-07-01",
  "checklist": {
    "workout": true,
    "eggs": true,
    "quinoa": false,
    "vegCurry": true,
    "soya": true,
    "water": true
  },
  "health": {
    "sleepHours": 7.2,
    "restingHeartRate": 62,
    "bloodGlucoseFasting": 95,
    "bloodGlucosePP": 120,
    "weight": 74.5
  },
  "notes": "Felt strong today."
}
```

#### GET /api/health/today — Response

```json
{
  "date": "2026-07-01",
  "age": 29,
  "checklist": {
    "workout": true,
    "eggs": true,
    "quinoa": false,
    "vegCurry": true,
    "soya": true,
    "water": true
  },
  "health": {
    "sleepHours": {
      "value": 7.2,
      "avg7d": 6.8,
      "avg30d": 7.0,
      "trend": "stable",
      "sparkline7d": [6.5, 7.0, 6.8, 7.5, 6.9, 7.0, 7.2],
      "ideal": { "min": 7, "max": 9, "source": "NSF" },
      "vsIdeal": "in_range"
    },
    "restingHeartRate": {
      "value": 62,
      "avg7d": 63,
      "avg30d": 65,
      "trend": "improving",
      "sparkline7d": [65, 64, 63, 62, 64, 63, 62],
      "ideal": { "min": 60, "max": 70, "source": "AHA" },
      "vsIdeal": "in_range"
    },
    "weight": {
      "value": 74.5,
      "bmi": 23.5,
      "ideal": { "min": 59, "max": 79, "source": "WHO" },
      "vsIdeal": "in_range"
    },
    "bloodGlucoseFasting": {
      "value": 95,
      "ideal": { "min": 70, "max": 100, "source": "ADA" },
      "vsIdeal": "in_range"
    },
    "bloodGlucosePP": {
      "value": 120,
      "ideal": { "max": 140, "source": "ADA" },
      "vsIdeal": "in_range"
    }
  },
  "workout": {
    "programName": "IronBorn",
    "programDay": 14,
    "title": "Back & Biceps",
    "exercises": [
      { "name": "Alternate Bicep Curls", "sets": 4, "reps": [7, 6, 5, 4], "perArm": true },
      { "name": "Upright Rows", "sets": 4, "reps": [10, 8, 6, 6] },
      { "name": "Deadlifts", "sets": 4, "reps": [10, 8, 8, 5] },
      { "name": "Shrugs", "sets": 4, "reps": [12, 10, 10, 6] },
      { "name": "Bent Over Rows", "sets": 4, "reps": [10, 8, 6, 6] },
      { "name": "Bent Over Flys", "sets": 4, "reps": [6, 6, 5, 4] }
    ]
  }
}
```

### Workout Programs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/programs` | List all programs |
| `POST` | `/api/programs` | Upload new program (JSON) |
| `PUT` | `/api/programs/{id}/activate` | Set as active program |
| `GET` | `/api/programs/today` | Today's workout (active program + current day) |
| `POST` | `/api/programs/complete-day` | Mark current day done, advance to next |

#### Program JSON Schema

```json
{
  "name": "IronBorn",
  "days": [
    {
      "day": 1,
      "title": "Shoulders, Chest & Triceps",
      "exercises": [
        { "name": "Shoulder Press", "sets": 4, "reps": [12, 10, 10, 8] },
        { "name": "Tricep Extensions", "sets": 4, "reps": [5, 4, 4, 3], "perArm": true }
      ]
    }
  ]
}
```

Optional exercise fields: `perArm`, `perLeg`, `perSide`, `duration`, `unit`, `note`

**Skip-safe**: if a day is skipped, the program stays on that day until completed.

### Predictions

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/predictions?metrics={csv}&horizons={csv}` | Trend projections |

Horizons: `2w`, `1m`, `3m`, `6m`, `1y`

#### Response

```json
{
  "generatedAt": "2026-07-01T00:00:00Z",
  "dataPointsUsed": 180,
  "predictions": {
    "weight": {
      "currentAvg": 74.5,
      "ideal": { "min": 59, "max": 79 },
      "horizons": {
        "2w": { "predicted": 74.2, "low": 73.5, "high": 74.9, "vsIdeal": "in_range" },
        "1m": { "predicted": 73.8, "low": 72.5, "high": 75.1, "vsIdeal": "in_range" },
        "3m": { "predicted": 72.5, "low": 70.0, "high": 75.0, "vsIdeal": "in_range" },
        "6m": { "predicted": 71.0, "low": 67.0, "high": 75.0, "vsIdeal": "in_range" },
        "1y": { "predicted": 70.0, "low": 64.0, "high": 76.0, "vsIdeal": "optimal" }
      },
      "trendDescription": "Dropping ~0.5 kg/month. On track to reach optimal BMI by mid-2027."
    },
    "workoutConsistency": {
      "current30d": 85,
      "horizons": {
        "2w": { "predicted": 87 },
        "1m": { "predicted": 88 },
        "3m": { "predicted": 90 }
      },
      "trendDescription": "Workout consistency improving 2-3% per month."
    }
  }
}
```

#### Prediction Details

**Numeric metrics** (weight, blood glucose, resting HR, sleep): Weighted linear regression with exponential decay — recent data weighted more. Confidence intervals widen with longer horizons.

**Checklist items** (workout, eggs, quinoa, veg curry, soya, water): Rolling consistency % with trend direction.

#### Minimum Data Requirements

| Horizon | Minimum History | Ideal History |
|---------|----------------|---------------|
| 2 weeks | 30 days | 60 days |
| 1 month | 60 days | 90 days |
| 3 months | 90 days | 180 days |
| 6 months | 180 days | 365 days |
| 1 year | 365 days | 730 days |

Insufficient data returns the horizon with `"confidence": "low"`.

### Clinical Records

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/clinical/upload` | Upload PDF, Gemini extracts metrics |
| `GET` | `/api/clinical/latest` | Latest clinical metrics + trends |
| `GET` | `/api/clinical/{id}` | Specific record + PDF download URL |

### Import

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/import/xml` | Upload Apple Health XML for backfill |
| `GET` | `/api/import/status/{jobId}` | Check import progress |

XML parser filters for 5 types only, stream-parses to avoid OOM (Render free tier = 512 MB RAM).

---

## Data Model (Firestore)

### Collection: `daily_metrics`

Document ID: date string (e.g., `2026-07-01`)

```json
{
  "date": "2026-07-01",
  "checklist": {
    "workout": true,
    "eggs": true,
    "quinoa": false,
    "vegCurry": true,
    "soya": true,
    "water": true
  },
  "workout": {
    "programName": "IronBorn",
    "programDay": 14,
    "title": "Back & Biceps",
    "exercises": [ ... ]
  },
  "health": {
    "sleepHours": 7.2,
    "restingHeartRate": 62,
    "bloodGlucoseFasting": 95,
    "bloodGlucosePP": 120,
    "weight": 74.5
  },
  "notes": "Felt strong today.",
  "source": "manual | shortcut | import",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Collection: `programs`

```json
{
  "name": "IronBorn",
  "totalDays": 29,
  "isActive": true,
  "currentDay": 14,
  "days": [ ... ],
  "createdAt": "timestamp"
}
```

### Collection: `clinical_records`

```json
{
  "date": "2026-06-15",
  "reportType": "blood_work",
  "pdfStoragePath": "clinical/2026-06-15_blood_work.pdf",
  "extractedMetrics": {
    "totalCholesterol": { "value": 185, "unit": "mg/dL", "refRange": "< 200", "status": "normal" },
    "ldlCholesterol": { "value": 120, "unit": "mg/dL", "refRange": "< 100", "status": "high" },
    "hba1c": { "value": 5.4, "unit": "%", "refRange": "< 5.7", "status": "normal" }
  },
  "createdAt": "timestamp"
}
```

### Firebase Storage

```
/clinical/
  2026-06-15_blood_work.pdf
  2026-03-10_full_panel.pdf
```

---

## Ideal Benchmarks (Male, 178 cm, Age-Adjusted)

| Metric | Ideal Range | Source |
|--------|-------------|--------|
| Weight / BMI | 59–79 kg (BMI 18.5–24.9) | WHO |
| Resting HR | 60–70 bpm | AHA |
| Sleep | 7–9 hours | NSF |
| Blood Glucose Fasting | 70–100 mg/dL | ADA |
| Blood Glucose PP | < 140 mg/dL | ADA |

Benchmarks recalculate as user ages (derived from DOB).

---

## Security

| Concern | Approach |
|---------|----------|
| API Auth | Static API key in `X-API-Key` header (env var) |
| CORS | Allow only Vercel frontend domain |
| Firebase | Service account key on Render, Firestore security rules |
| XML Upload | Stream-parse (StAX), 500 MB max |
| PDF Upload | 20 MB max, stored in Firebase Storage |
| Gemini API | Key as env var |

---

## Deployment (Render)

- Build: `./gradlew bootJar`
- Start: `java -jar build/libs/krypton.jar`
- Env vars:
  - `FIREBASE_CONFIG` — Firebase service account JSON
  - `API_KEY` — Shortcut auth secret
  - `CORS_ORIGIN` — Vercel frontend URL
  - `GEMINI_API_KEY` — Google AI Studio key

---

## Project Structure

```
krypton/
├── assets/
│   ├── workouts/
│   │   └── ironborn.json
│   └── apple_health_export_2026_07_02.json
├── src/main/java/com/krypton/
│   ├── KryptonApplication.java
│   ├── config/
│   │   ├── FirebaseConfig.java
│   │   ├── AsyncConfig.java
│   │   ├── CorsConfig.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── HealthController.java
│   │   ├── ProgramController.java
│   │   ├── ClinicalController.java
│   │   └── ImportController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── DailyCheckInRequest.java
│   │   │   ├── ProgramUploadRequest.java
│   │   │   └── HealthDataRequest.java
│   │   └── response/
│   │       ├── TodaySummaryResponse.java
│   │       ├── PredictionResponse.java
│   │       ├── ProgramResponse.java
│   │       ├── ClinicalResponse.java
│   │       ├── ImportStatusResponse.java
│   │       └── ErrorResponse.java
│   ├── service/
│   │   ├── HealthService.java
│   │   ├── ProgramService.java
│   │   ├── ClinicalService.java
│   │   ├── PredictionService.java
│   │   ├── ImportService.java
│   │   └── GeminiService.java
│   ├── model/
│   │   ├── DailyMetrics.java
│   │   ├── Checklist.java
│   │   ├── Health.java
│   │   ├── WorkoutLog.java
│   │   ├── Program.java
│   │   ├── ProgramDay.java
│   │   ├── Exercise.java
│   │   ├── ClinicalRecord.java
│   │   ├── ExtractedMetric.java
│   │   └── Prediction.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ProgramNotFoundException.java
│   │   ├── ImportFailedException.java
│   │   ├── ClinicalExtractionException.java
│   │   ├── InvalidMetricsException.java
│   │   └── DuplicateEntryException.java
│   ├── repository/
│   │   ├── DailyMetricsRepository.java
│   │   ├── ProgramRepository.java
│   │   └── ClinicalRecordRepository.java
│   └── util/
│       ├── XmlStreamParser.java
│       ├── TrendCalculator.java
│       └── BenchmarkProvider.java
├── src/main/resources/
│   ├── application.yml
│   └── firebase-service-account.json
├── build.gradle.kts
├── settings.gradle.kts
└── Dockerfile
```

---

## Implementation Order

| Phase | What | Effort |
|-------|------|--------|
| 1 | Spring Boot scaffold + Firebase setup + daily metrics CRUD | 1 day |
| 2 | Program system (upload, activate, track day, complete) | 0.5 day |
| 3 | XML stream parser (5 types only) + import endpoint | 1 day |
| 4 | Prediction engine + benchmarks | 1 day |
| 5 | PDF upload + Gemini extraction + Firebase Storage | 1 day |
| 6 | Deploy to Render | 0.5 day |
| | **Total** | **~5 days** |
