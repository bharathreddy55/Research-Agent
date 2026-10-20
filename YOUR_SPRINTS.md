# 📋 Your Action Plan & Verification Sprints

Welcome to the hands-on testing and verification of **ResearchAgent**!
This document tracks the steps you need to perform from your side. As you complete each step, let me know and I will update the checkboxes (`[x]`) one by one.

---

## 🏃 Sprint Checklist (User Tasks)

### 🔑 Sprint A: Environment & Credentials Setup
- [ ] **Step A1: Create `.env` file**
  - Run in terminal: `cp .env.example .env` (or copy `.env.example` to `.env` manually).
- [ ] **Step A2: Add your LLM API Key**
  - Open `.env` and set your `GEMINI_API_KEY` (from [Google AI Studio](https://aistudio.google.com/app/apikey)) or `OPENAI_API_KEY`.
  - *(Optional)* Set `TAVILY_API_KEY` for live search; if omitted, the built-in fallback search scraper will be used automatically.
- [ ] **Step A3: Generate your Login Password Hash**
  - Run in PowerShell:
    ```powershell
    ./scripts/hash-password.ps1
    ```
  - Enter your desired password when prompted. It will print a BCrypt hash starting with `$2a$...`
- [ ] **Step A4: Configure your User in `.env`**
  - In `.env`, set:
    ```dotenv
    AUTH_USERS_JSON='{"admin":"PASTE_YOUR_BCRYPT_HASH_HERE"}'
    AUTH_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
    AUTH_COOKIE_SECURE=false
    ```

---

### 🐳 Sprint B: Launch the Application
- [ ] **Step B1: Start Docker Containers**
  - Run in terminal:
    ```bash
    docker compose up -d
    ```
  - Verify that Postgres, RabbitMQ, Redis, Backend, and Frontend containers are up (`docker compose ps`).
- [ ] **Step B2: Verify Backend Health**
  - Check in browser: [http://localhost:8085/api/auth/csrf](http://localhost:8085/api/auth/csrf)
  - You should receive a valid CSRF JSON token (`{"token": "...", "headerName": "X-CSRF-TOKEN"}`).
- [ ] **Step B3: Open Web Dashboard**
  - Open in browser: [http://localhost:3000](http://localhost:3000)
  - You should see the ResearchAgent dark-themed sign-in portal.

---

### 🧪 Sprint C: Live Testing & Feature Verification
- [ ] **Step C1: Sign In**
  - Enter the username (e.g., `admin`) and the password you hashed in Step A3.
  - You should enter the main ResearchAgent research dashboard.
- [ ] **Step C2: Submit a Research Query**
  - Enter a topic: *"Solid state battery commercialization timelines 2026"*
  - Select Depth: **QUICK** (~2 min)
  - Set Budget Slider: **₹15.00**
  - Click **🚀 Start AI Research Team**
- [ ] **Step C3: Verify Live Real-Time Agent Stream**
  - Watch the pipeline stages illuminate:
    `Planner → Searcher → Reader → Verifier → Writer → Reflection Critic → Citation Validator`
  - Check the real-time thought stream console in the UI receiving SSE messages.
- [ ] **Step C4: Inspect Generated Report & Details**
  - When complete, verify:
    - **Research Brief**: Read the synthesized markdown report with inline citations (`[n]`).
    - **Step Traces**: Inspect token usage and costs for every single agent step.
    - **Discovered Sources**: View the list of candidate web URLs crawled.
    - **Verified Claims**: Check the cross-domain corroboration badge for each claim.
- [ ] **Step C5: Test PDF Export**
  - Click **📥 Download Publication PDF**
  - Open the generated PDF and verify formatting, title cover, and clean typography.
- [ ] **Step C6: Test Run Cancellation**
  - Submit a new research run with depth **DEEP**.
  - While it is searching or reading, click the red **Stop LLM Spend (Cancel)** button.
  - Verify the status immediately transitions to `CANCELLED` and spend stops.

---

### 💻 Sprint D: Standalone CLI Testing (Zero-Docker Mode)
- [ ] **Step D1: Run CLI in terminal**
  - Run directly without Docker using the local in-memory profile:
    ```bash
    java -Dspring.profiles.active=local -jar target/research-agent-1.0.0-SNAPSHOT.jar --query "Quantum key distribution vs lattice cryptography" --depth QUICK
    ```
  - Verify the terminal prints the ANSI agent thought stream and saves the report to `./reports/report-*.md`.
