# 🚀 AI Web Developer Platform

An AI-powered full-stack web development platform that can generate, modify, and preview applications in real time using natural language.

This platform is designed to behave like a real AI-assisted development environment instead of a simple code generator.

Built using a scalable microservices architecture with Spring Boot, Spring AI, Kafka, Ollama, WebSockets, PostgreSQL, PGVector, React, and Docker.

---

# ✨ Features

## 💬 AI Chat-Based Development

- Build applications using natural language prompts
- Conversational AI workflow
- Real-time token streaming
- Context-aware code generation

Example:

```txt
Create a dashboard with authentication and analytics
```

---

## 📁 Intelligent File Management

Instead of regenerating the entire application every time:

✅ AI updates only impacted files  
✅ Maintains proper project structure  
✅ Supports scalable codebase management  
✅ File tree structure support  

---

## 🧠 Context-Aware AI

Using embeddings + PGVector:

- Relevant files are retrieved automatically
- AI understands existing project structure
- Reduces hallucinations
- Enables intelligent incremental updates

---

## ⚡ Real-Time Streaming

Using Kafka + WebSockets:

- Token-by-token AI response streaming
- Live progress updates
- Real-time frontend synchronization
- Event-driven communication

---

## 🖥️ Live Preview System

Generated applications can:

- Build dynamically
- Run in isolated environments
- Preview instantly
- Update in real time

---

## 🔄 Event-Driven Architecture

Kafka acts as the communication backbone:

- AI generation events
- File update events
- Build events
- Streaming events

Everything works asynchronously and independently.

---

# 🏗️ System Architecture

```text
                          ┌──────────────────┐
                          │     Frontend     │
                          │   React + Vite   │
                          └────────┬─────────┘
                                   │
                          ┌────────▼─────────┐
                          │   API Gateway    │
                          └────────┬─────────┘
               ┌───────────────────┼───────────────────┐
               │                   │                   │
       ┌───────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
       │  Auth Service  │ │ Project Service │ │  Chat Service   │
       └────────────────┘ └─────────────────┘ └────────┬────────┘
                                                        │
                                                ┌───────▼────────┐
                                                │   AI Service   │
                                                │ Spring AI +    │
                                                │    Ollama      │
                                                └───────┬────────┘
                                                        │
                                   ┌────────────────────▼────────────────────┐
                                   │          Kafka Event Stream             │
                                   └────────────────────┬────────────────────┘
                                                        │
                                ┌───────────────────────▼───────────────────────┐
                                │              Preview Service                  │
                                └───────────────────────┬───────────────────────┘
                                                        │
                                                ┌───────▼────────┐
                                                │      Nginx     │
                                                └────────────────┘
```

---

# ⚙️ Tech Stack

## Backend

- Java
- Spring Boot
- Spring Security
- Spring AI
- Apache Kafka
- WebSockets
- PostgreSQL
- PGVector

---

## 🤖 AI Stack

- Ollama
- LLM-based code generation
- Embedding models
- Context retrieval pipeline
- Incremental AI workflows

---

## 🎨 Frontend

- React
- Vite
- Tailwind CSS
- Zustand

---

## ☁️ DevOps & Infrastructure

- Docker
- Docker Compose
- Nginx
- Kubernetes (planned)

---

# 🧠 AI Workflow

```text
User Prompt
      │
      ▼
Chat Service
      │
      ▼
Context Builder
      │
      ▼
PGVector Semantic Search
      │
      ▼
Relevant File Retrieval
      │
      ▼
Impact Analysis
      │
      ▼
AI Generation (Spring AI + Ollama)
      │
      ▼
Structured File Updates
      │
      ▼
Kafka Event Publishing
      │
      ▼
Preview Service Rebuild
      │
      ▼
WebSocket Streaming
      │
      ▼
Frontend Live Updates
```

---

# 📁 Project Structure

```bash
ai-web-developer/
│
├── api-gateway/
│
├── auth-service/
│
├── project-service/
│
├── chat-service/
│
├── ai-service/
│
├── preview-service/
│
├── frontend/
│
├── docker/
│
├── nginx/
│
└── docs/
```

---

# 🔥 Core Engineering Concepts

## Incremental File Updates

Instead of regenerating the entire project:

- AI updates only impacted files
- Maintains project stability
- Improves scalability
- Enables production-like workflows

---

## Event-Driven Communication

Kafka enables:

- asynchronous processing
- loosely coupled services
- scalable communication
- independent workflows

---

## Real-Time User Experience

Using WebSockets:

- live AI response streaming
- progress updates
- instant UI feedback
- collaborative interaction

---

## Context-Aware Generation

Using PGVector + embeddings:

- semantic code search
- relevant file retrieval
- reduced hallucinations
- improved AI accuracy

---

# 🛠️ Setup Instructions

## 1️⃣ Clone Repository

```bash
git clone https://github.com/your-username/ai-web-developer.git

cd ai-web-developer
```

---

## 2️⃣ Start Infrastructure

```bash
docker-compose up -d
```

This starts:

- PostgreSQL
- PGVector
- Kafka
- Zookeeper
- Ollama

---

## 3️⃣ Run Backend Services

Example:

```bash
cd auth-service

./mvnw spring-boot:run
```

Repeat for:

- api-gateway
- project-service
- chat-service
- ai-service
- preview-service

---

## 4️⃣ Run Frontend

```bash
cd frontend

npm install

npm run dev
```

---

# 🔐 Authentication Flow

```text
User Login
    │
    ▼
Auth Service
    │
    ▼
JWT Token Generated
    │
    ▼
Frontend Stores Token
    │
    ▼
API Gateway Validates Token
    │
    ▼
Request Routed To Services
```

---

# ⚡ Real-Time Streaming Flow

```text
User Prompt
    │
    ▼
AI Service Generates Response
    │
    ▼
Kafka Publishes Events
    │
    ▼
Chat Service Consumes Events
    │
    ▼
WebSocket Streams To Frontend
    │
    ▼
Live UI Updates
```

---

# 🚀 Future Improvements

- Kubernetes deployment
- Multi-model AI support
- Git integration
- Multi-user collaboration
- AI memory optimization
- Agentic workflows
- Version control system
- Autonomous code planning

---

# 📚 Learning Outcomes

This project helped explore:

- AI System Design
- Distributed Systems
- Event-Driven Architecture
- Microservices
- Real-Time Communication
- LLM Orchestration
- Semantic Search
- Scalable Backend Engineering

---

# 🤝 Contributing

Contributions, feedback, and ideas are welcome.

Feel free to fork the repository and open a PR 🚀

---

# 📜 License

MIT License

---

# ⭐ Support

If you found this project useful, consider giving it a star ⭐
