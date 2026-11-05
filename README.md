# Shopping Assistant

A learning project: an AI shopping assistant built from scratch with **Spring Boot + Gemini**, mirroring the
architecture of a production storefront integration. The assistant answers shopping questions by calling
**tools** instead of guessing, and it has **two product sources**:

| Tool | Source | When it is used |
|---|---|---|
| `search_products` | Our own local catalog (15 sample products, in-memory H2 database) | Tried **first**. Fast, free, works offline. |
| `search_web_products` | Google Shopping through [SerpApi](https://serpapi.com) | When the catalog has nothing suitable, or the customer wants a wider range, other brands or comparisons. Uses SerpApi credits. |

Web results are other retailers' listings. The assistant must name the store and never present them as this
store's own prices or offers.

## How a question is answered
```
POST /api/chat {"message": "..."}
  -> AgentService (agent loop, max 5 rounds)
       -> GeminiClient -> Gemini API: "answer, or ask for a tool"
       -> if a tool is requested: ShoppingTools runs it
            -> search_products      -> CatalogService (local database)
            -> search_web_products  -> SerpApiClient  (Google Shopping)
       -> tool result goes back to Gemini -> final text answer
  -> {"reply": "..."}
```
Safety limits: at most 5 agent rounds and 3 web searches per question; identical web searches are cached for
10 minutes to save credits.

## Mapping to the production architecture
| Production (Costco + Google) | This project |
|---|---|
| Adobe Target script + Google SDK widget | Chat widget (planned) |
| Apigee gateway | Gateway layer (planned) |
| Token Issuer API (JWT) | Token service (planned) |
| Google Shopping Agent + LLM | `AgentService` + Gemini |
| Google Retail Search / catalog | Local catalog + SerpApi web search |
| Analytics events | Analytics events (planned) |

## Setup
Requires **Java 21**. Maven is not needed: the Maven Wrapper (`mvnw`) is included.

### 1. API keys (never commit them)
| Variable | Where to get it |
|---|---|
| `GEMINI_API_KEY` | https://aistudio.google.com/apikey |
| `SERPAPI_API_KEY` | https://serpapi.com/manage-api-key (250 free searches) |

Set them either way:
- **IntelliJ:** Run > Edit Configurations > select the Spring Boot configuration > Environment variables:
  `GEMINI_API_KEY=...;SERPAPI_API_KEY=...`. Do **not** tick "Store as project file" (it would put the keys
  into `.run/` in the repo).
- **PowerShell (permanent, per laptop):**
  ```powershell
  [Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "your_key", "User")
  [Environment]::SetEnvironmentVariable("SERPAPI_API_KEY", "your_key", "User")
  ```
  Then open a **new** terminal. A missing key only disables that feature: without `SERPAPI_API_KEY` the
  catalog search still works.

### 2. Settings (`src/main/resources/application.properties`)
`gemini.model`, `gemini.base-url`, `serpapi.base-url`, `serpapi.max-results`. Keys are read from the
environment variables above, never from this file.

### 3. Run
```powershell
.\mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run            # Mac / Linux
```
The app listens on http://localhost:8080.

## Try it
```powershell
# Plain catalog search (no AI)
Invoke-RestMethod "http://localhost:8080/api/products/search?q=tv&onlyOffers=true"

# Chat. ".reply" prints just the answer text
(Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType "application/json" -Body '{"message":"Do you have tvs on offer?"}').reply

# Triggers the web search (the catalog has no espresso machines)
(Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType "application/json" -Body '{"message":"Do you sell espresso machines?"}').reply
```
The app log shows each tool call, e.g. `model called tool search_web_products with {...}`.

## Endpoints
| Method | Path | Description |
|---|---|---|
| GET | `/api/products/search` | Catalog search. Optional params: `q`, `category`, `maxPrice`, `onlyOffers` |
| POST | `/api/chat` | Body `{"message": "..."}` (1 to 2000 characters). Returns `{"reply": "..."}` |

## Project layout
```
src/main/java/com/example/shoppingagent/
  catalog/     Product, repository, CatalogService, seed data, REST endpoint
  chat/        ChatController, AgentService (agent loop), GeminiClient, ShoppingTools
  websearch/   SerpApiClient (Google Shopping), WebProduct
```

## Tests
```
./mvnw test
```
All tests use mock servers: no API keys, network or credits are needed.

## Troubleshooting
| Symptom | Cause / fix |
|---|---|
| `502` from `/api/chat`, log says `Gemini ... HTTP 503 ... high demand` | Gemini is overloaded. Wait a minute and retry, or change `gemini.model`. |
| `503` with "GEMINI_API_KEY is not set" | The app cannot see the variable. Set it, open a new terminal, restart the app. |
| Chat never uses web search | Ask for something the catalog lacks, or "options from other stores". Check `SERPAPI_API_KEY` is set. |
| `400` when calling the chat with `curl.exe` | PowerShell strips the quotes in the JSON body. Use `Invoke-RestMethod` as shown above. |
| `JAVA_HOME` errors | Point `JAVA_HOME` at your JDK folder (no trailing `\` or `\bin`). |

## Known limits
- Catalog search is a plain substring match (`tv` matches, `tvs` may not); the model is told to use short
  singular keywords. Better retrieval is planned.
- Web results are third-party listings and depend on SerpApi's default location.
- No conversation memory yet: each request starts fresh.
- No retry when Gemini is briefly overloaded (see Troubleshooting).

## Roadmap
1. [x] Product catalog and search API (local stand-in for Google Retail Search)
2. [x] Chat endpoint calling the Gemini API
3. [x] Tool calling: Gemini searches the catalog instead of guessing
4. [x] Web product search via SerpApi, alongside the local catalog
5. [ ] Retry and fallback model for Gemini 429/503 errors
6. [ ] Conversation memory (session id, message history)
7. [ ] Chat widget with streaming replies
8. [ ] Token service (session-bound JWT)
9. [ ] `get_feature_info` tool (questions about site features)
10. [ ] Gateway, analytics events, guardrails
11. [ ] Evals and Docker
