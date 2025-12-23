from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel


class ChatRequest(BaseModel):
    message: str
    user: str | None = None


class ChatResponse(BaseModel):
    reply: str


app = FastAPI(title="Chatbot API")

origins = [
    "http://localhost:5173",  # React frontend
    "http://localhost:8080",  # Spring Boot backend
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.post("/api/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    """
    Very simple rule-based chatbot for demo purposes.
    """
    text = request.message.lower().strip()

    if not text:
        return ChatResponse(reply="Please say something so I can help you.")

    if "hello" in text or "hi" in text:
        name = request.user or "there"
        return ChatResponse(reply=f"Hello, {name}! How can I help you today?")

    if "help" in text:
        return ChatResponse(
            reply="I am a simple demo bot. Try asking about the app, login, or signup."
        )

    if "login" in text or "signup" in text or "sign up" in text:
        return ChatResponse(
            reply="You can create an account on the signup page, then log in to access the chat."
        )

    return ChatResponse(
        reply=f"You said: '{request.message}'. I am a simple demo bot, but I'm listening!"
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)


