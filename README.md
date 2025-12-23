## Full-Stack Chat App (React + Spring Boot + Python)

This project is a simple full-stack application demonstrating:

- React frontend with login/signup, JWT auth, and a chat UI
- Java Spring Boot backend for user management and JWT-based authentication
- Python backend (FastAPI) acting as a chatbot API

### Structure

- `frontend/` – React app (Vite + TypeScript) with functional components and hooks
- `auth-api/` – Spring Boot authentication API with Spring Security + JWT
- `chatbot-api/` – FastAPI-based Python service for chatbot responses

### High-Level Features

- Secure password hashing (BCrypt) stored in the database
- JWT-based login; token sent on subsequent requests
- Protected chat page accessible only when authenticated
- CORS configured so the frontend can talk to both backends


