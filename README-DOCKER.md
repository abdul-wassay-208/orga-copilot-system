# Docker Deployment Guide

This repository contains Docker configurations for deploying both services to Render or any Docker-compatible platform.

## Files Created

### Dockerfiles
- `copilot/Dockerfile` - Production-ready Dockerfile for Python FastAPI service
- `orga/Dockerfile` - Production-ready Dockerfile for Java Spring Boot service

### Configuration Files
- `render.yaml` - Render Blueprint configuration for easy deployment
- `DEPLOYMENT.md` - Detailed deployment instructions
- `.dockerignore` files - Exclude unnecessary files from Docker builds

## Quick Start

### Build Locally

**Copilot Service:**
```bash
cd copilot
docker build -t copilot:latest .
docker run -p 8000:8000 \
  -e ANTHROPIC_API_KEY=your_key \
  -e OPENAI_API_KEY=your_key \
  -e PINECONE_API_KEY=your_key \
  -e PINECONE_ENVIRONMENT=your_env \
  -e PINECONE_INDEX_NAME=your_index \
  copilot:latest
```

**Orga Service:**
```bash
cd orga
docker build -t orga:latest .
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/db \
  -e DB_USERNAME=user \
  -e DB_PASSWORD=pass \
  -e JWT_SECRET=your_secret \
  -e CHATBOT_BASE_URL=http://localhost:8000 \
  orga:latest
```

### Deploy to Render

1. Push your code to GitHub/GitLab/Bitbucket
2. Connect repository to Render
3. Use the `render.yaml` blueprint OR manually create services
4. Set environment variables in Render dashboard
5. Deploy!

See `DEPLOYMENT.md` for detailed instructions.

## Environment Variables

All sensitive values should be set as environment variables in your deployment platform, never committed to the repository.

## Notes

- Both Dockerfiles use multi-stage builds for smaller production images
- The Java service runs as a non-root user for security
- Both services automatically use the `PORT` environment variable set by Render
- The copilot service uses Python 3.13 slim image for smaller size
- The orga service uses Eclipse Temurin JRE Alpine for minimal footprint

