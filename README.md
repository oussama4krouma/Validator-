# XRechnung Validator

A small web app that validates electronic invoices (XRechnung / ZUGFeRD) using the
[Mustangproject](https://www.mustangproject.org/) validator, version 2.24.0.

- **backend/** — Spring Boot 3.5 REST API (Java 21)
- **frontend/** — Vue 3 + Vite single-page app

## Requirements

- Java 21 (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Node.js 20+ and npm (`node -v`)

## Run the backend

```bash
cd backend
mvn spring-boot:run
```

The API starts on **http://localhost:8080** and exposes one endpoint:

```
POST /upload-xml       (multipart form field "file")
```

It returns JSON:

```json
{
  "fileName": "invoice.xml",
  "status": "VALID",
  "validationResult": "<validation> ... </validation>",
  "error": null
}
```

`status` is one of `VALID`, `INVALID`, or `ERROR`.

## Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**, choose or drag in an `.xml` invoice, and click *Validieren*.

## Notes

- The frontend expects the backend at `http://localhost:8080`. If you change the backend
  port, update `API_URL` in `frontend/src/components/XRechnungValidator.vue` and the allowed
  origin in `backend/.../WebConfig.java`.
- CORS is open only to `http://localhost:5173` (the Vite dev server).
- First backend build downloads the Mustang validator and its dependencies from Maven Central,
  so the initial `mvn` run needs internet access.
