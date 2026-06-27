# Angular UI Plan

The repository now includes a manual Angular workspace scaffold in `frontend/`.

Current state:

- standalone Angular app
- dashboard shell for queue, status, logs, and operator actions
- mock data service as placeholder for Spring Boot API integration

Next backend endpoints to add in Spring Boot:

- `GET /api/dashboard/summary`
- `GET /api/dashboard/queue`
- `POST /api/actions/daily`
- `POST /api/actions/generate`
- `POST /api/actions/publish/threads`
- `POST /api/actions/publish/x`

Local setup once Node/npm are fixed:

```powershell
cd frontend
npm install
npx ng serve
```
