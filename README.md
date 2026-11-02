# Shopping Assistant

A learning project: build a Costco-style AI shopping assistant from scratch (Spring Boot + Gemini),
mirroring the architecture of the production integration.

## Roadmap
1. [x] Product catalog + search API (stand-in for Google Retail Search)
2. [x] Chat endpoint calling the Gemini API
3. [ ] Tool calling: search_products, get_offers, get_feature_info
4. [ ] Token service (JWT, session-bound)
5. [ ] Chat widget + streaming UI
6. [ ] Gateway, analytics, memory, guardrails
7. [ ] Evals + Docker

## Run
```
mvn spring-boot:run
curl "localhost:8080/api/products/search?q=tv&onlyOffers=true"
```
Requires Java 21 and Maven.

## Chat (Step 2)
Set your key once as an environment variable (never commit it), then:
```
.\mvnw.cmd spring-boot:run
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType "application/json" -Body '{"message":"Hello, what can you help me with?"}'
```
