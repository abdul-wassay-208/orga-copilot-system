# Deployment Guide for Render

This guide explains how to deploy both services (orga and copilot) to Render using Docker.

## Prerequisites

1. A Render account (sign up at https://render.com)
2. Your code pushed to a Git repository (GitHub, GitLab, or Bitbucket)
3. Environment variables ready (API keys, database credentials, etc.)

## Quick Start

### Option 1: Using Render Blueprint (Recommended)

1. Connect your repository to Render
2. Render will detect the `render.yaml` file
3. Follow the prompts to set environment variables
4. Deploy!

### Option 2: Manual Deployment

#### Step 1: Deploy Copilot Service (Python/FastAPI)

1. Go to Render Dashboard → New → Web Service
2. Connect your repository
3. Configure:
   - **Name**: `copilot-chatbot`
   - **Environment**: `Docker`
   - **Dockerfile Path**: `copilot/Dockerfile`
   - **Docker Context**: `copilot`
   - **Plan**: Starter ($7/month) or higher

4. Set Environment Variables:
   ```
   PORT=8000
   ANTHROPIC_API_KEY=your_anthropic_key
   OPENAI_API_KEY=your_openai_key
   PINECONE_API_KEY=your_pinecone_key
   PINECONE_ENVIRONMENT=your_pinecone_env
   PINECONE_INDEX_NAME=your_index_name
   ```

5. Click "Create Web Service"
6. Note the service URL (e.g., `https://copilot-chatbot.onrender.com`)

#### Step 2: Deploy Orga Service (Java/Spring Boot)

1. Go to Render Dashboard → New → Web Service
2. Connect your repository
3. Configure:
   - **Name**: `orga-backend`
   - **Environment**: `Docker`
   - **Dockerfile Path**: `orga/Dockerfile`
   - **Docker Context**: `orga`
   - **Plan**: Starter ($7/month) or higher

4. Set Environment Variables:
   ```
   PORT=8080
   DATABASE_URL=jdbc:postgresql://your-db-host:5432/orga-final?sslmode=require
   DB_USERNAME=your_db_username
   DB_PASSWORD=your_db_password
   JWT_SECRET=your_jwt_secret (use a strong random string)
   JWT_EXPIRATION_MS=86400000
   CHATBOT_BASE_URL=https://copilot-chatbot.onrender.com
   DDL_AUTO=update
   SHOW_SQL=false
   FORMAT_SQL=false
   ```

5. Click "Create Web Service"

#### Step 3: Update Frontend API URLs

Update your frontend (`evoassociates-main`) to point to the deployed backend:

```typescript
// In evoassociates-main/src/lib/api-client.ts
const API_BASE_URL = 'https://orga-backend.onrender.com'; // Your Render URL
```

## Environment Variables Reference

### Copilot Service

| Variable | Description | Required |
|----------|-------------|----------|
| `PORT` | Server port (Render sets this automatically) | Yes |
| `ANTHROPIC_API_KEY` | Anthropic API key for Claude | Yes |
| `OPENAI_API_KEY` | OpenAI API key for embeddings | Yes |
| `PINECONE_API_KEY` | Pinecone API key | Yes |
| `PINECONE_ENVIRONMENT` | Pinecone environment | Yes |
| `PINECONE_INDEX_NAME` | Pinecone index name | Yes |

### Orga Service

| Variable | Description | Required |
|----------|-------------|----------|
| `PORT` | Server port (Render sets this automatically) | Yes |
| `DATABASE_URL` | PostgreSQL connection URL | Yes |
| `DB_USERNAME` | Database username | Yes |
| `DB_PASSWORD` | Database password | Yes |
| `JWT_SECRET` | Secret key for JWT tokens | Yes |
| `JWT_EXPIRATION_MS` | JWT expiration time in milliseconds | No (default: 86400000) |
| `CHATBOT_BASE_URL` | URL of the copilot service | Yes |
| `DDL_AUTO` | Hibernate DDL mode (update/validate/none) | No (default: update) |
| `SHOW_SQL` | Show SQL queries in logs | No (default: false) |
| `FORMAT_SQL` | Format SQL queries in logs | No (default: false) |

## Database Setup

### Option 1: Use Render PostgreSQL

1. Create a PostgreSQL database in Render
2. Copy the connection details
3. Use them in the `DATABASE_URL` environment variable

### Option 2: Use External Database (e.g., Neon)

1. Use your existing database connection string
2. Set `DATABASE_URL` accordingly

## CORS Configuration

The backend (`orga`) already has CORS configured for:
- `http://localhost:5173`
- `http://localhost:3000`
- `http://127.0.0.1:5173`
- `http://127.0.0.1:3000`

For production, you'll need to update `SecurityConfig.java` to include your frontend domain:

```java
config.setAllowedOrigins(List.of(
    "https://your-frontend-domain.com",
    "http://localhost:5173" // Keep for local dev
));
```

## Health Checks

Both services expose health endpoints:

- **Copilot**: `GET /chat/health`
- **Orga**: `GET /actuator/health` (if Spring Actuator is added)

Render will automatically use these for health checks.

## Troubleshooting

### Build Failures

1. Check Docker logs in Render dashboard
2. Verify all environment variables are set
3. Ensure Dockerfile paths are correct

### Connection Issues

1. Verify `CHATBOT_BASE_URL` points to the correct copilot service URL
2. Check database connection string format
3. Ensure CORS is configured for your frontend domain

### Slow Response Times

- Render free tier services spin down after inactivity
- First request after spin-down may be slow
- Consider upgrading to a paid plan for always-on services

## Cost Estimation

- **Starter Plan**: $7/month per service
- **Database**: $7/month (if using Render PostgreSQL)
- **Total**: ~$21/month for both services + database

## Security Notes

1. **Never commit secrets** to your repository
2. Use Render's environment variable encryption
3. Generate strong `JWT_SECRET` values
4. Use SSL/TLS (Render provides this automatically)
5. Update CORS to only allow your production frontend domain

