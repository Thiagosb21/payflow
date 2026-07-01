# PayFlow — Documentação Técnica

## Visão Geral

O **PayFlow** é um sistema de processamento de pagamentos construído com Spring Boot. Ele expõe uma API REST para criação e consulta de pagamentos, utiliza Apache Kafka para processar cada transação de forma assíncrona e persiste os dados no PostgreSQL. Toda a evolução do banco de dados é versionada via Flyway.

---

## Stack de Tecnologias

| Tecnologia | Versão | Para que serve no projeto |
|---|---|---|
| Java | 17 | Linguagem principal. Usa records, sealed classes e outros recursos da plataforma moderna da JVM. |
| Spring Boot | 3.3.5 | Framework base. Fornece servidor embutido (Tomcat), injeção de dependências, configuração automática e empacotamento. |
| Spring Web (MVC) | — | Camada HTTP: roteamento REST, serialização/desserialização JSON, tratamento de erros global. |
| Spring Data JPA | — | Abstração de acesso a dados. Gera queries automaticamente a partir de nomes de métodos e gerencia transações via `@Transactional`. |
| Hibernate | — | Implementação de JPA usada internamente pelo Spring Data. Faz o mapeamento objeto-relacional (ORM) entre entidades Java e tabelas PostgreSQL. |
| PostgreSQL | 16 | Banco de dados relacional principal. Armazena pagamentos e o histórico de eventos. Usa tipo `ENUM` nativo e `UUID` como chave primária. |
| Flyway | 10.10.0 | Controle de versão do banco de dados. Executa scripts SQL numerados automaticamente na inicialização da aplicação, garantindo que o schema esteja sempre na versão correta. |
| Apache Kafka | 7.6.0 (Confluent) | Message broker para processamento assíncrono. Cada novo pagamento gera um evento publicado em um tópico Kafka, que é consumido em seguida para simular o processamento no gateway de pagamentos. |
| Zookeeper | 7.6.0 (Confluent) | Serviço de coordenação exigido pelo Kafka para gerenciar brokers, tópicos e offsets dos consumers. |
| Spring Kafka | — | Integração entre Spring e Kafka. Fornece `KafkaTemplate` (producer) e `@KafkaListener` (consumer) com configuração declarativa via `application.yml`. |
| Lombok | 1.18.30 | Reduz código repetitivo. Gera getters, setters, construtores, builders e logs via anotações em tempo de compilação. |
| Bean Validation (Jakarta) | — | Validação de entrada. Anotações como `@NotBlank`, `@Positive` e `@Size` no `PaymentRequest` são avaliadas automaticamente antes do controller processar a requisição. |
| MapStruct | 1.5.5 | Gerador de código para mapeamento entre objetos (DTOs ↔ entidades). Declarado como dependência mas o mapeamento atual é feito manualmente no `PaymentService`. |
| Docker Compose | — | Orquestra os serviços de infraestrutura localmente: PostgreSQL, Zookeeper e Kafka em uma rede isolada (`payflow-network`). |
| Maven | — | Gerenciador de build e dependências. Usa `spring-boot-maven-plugin` para gerar o JAR executável. |

---

## Arquitetura

```
Cliente HTTP
     │
     ▼
┌─────────────────────┐
│  PaymentController  │  REST API  /api/v1/payments
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│   PaymentService    │  Lógica de negócio + orquestração
└──────┬──────────────┘
       │                          │
       ▼                          ▼
┌──────────────┐        ┌──────────────────┐
│  Repositories│        │  PaymentProducer │
│  (JPA/DB)    │        │  (Kafka)         │
└──────┬───────┘        └────────┬─────────┘
       │                         │  tópico: payments
       ▼                         ▼
┌─────────────┐         ┌──────────────────┐
│  PostgreSQL │         │ PaymentConsumer  │
└─────────────┘         │  (Kafka)         │
                        └────────┬─────────┘
                                 │
                                 ▼
                        PaymentService.updateStatus()
                        (atualiza DB + salva novo evento)
```

A aplicação segue uma arquitetura em camadas clássica do Spring Boot:

- **Controller** — recebe a requisição HTTP, valida o input e delega ao service.
- **Service** — contém toda a lógica de negócio: cria o pagamento, persiste no banco, publica no Kafka e trata as transições de status.
- **Repository** — interface com o banco de dados via Spring Data JPA.
- **Kafka Producer/Consumer** — desacopla a criação do pagamento do seu processamento.

---

## Fluxo de Dados

### Criação de um pagamento (`POST /api/v1/payments`)

1. O cliente envia um `PaymentRequest` com os dados do pagamento.
2. O `PaymentController` valida o payload via Bean Validation.
3. O `PaymentService.create()` é chamado:
   a. Persiste o pagamento no banco com status `PENDING`.
   b. Salva um `PaymentEvent` do tipo `PAYMENT_CREATED` (`null → PENDING`).
   c. Publica um `PaymentEventMessage` serializado como JSON no tópico Kafka `payments`.
4. A API retorna `201 Created` com o `PaymentResponse`.
5. **Assincronamente**, o `PaymentConsumer` recebe o evento do Kafka:
   a. Chama `updateStatus()` para `PROCESSING` → salva evento `STATUS_UPDATED`.
   b. Simula processamento (90% aprovação).
   c. Chama `updateStatus()` para `APPROVED` ou `FAILED` → salva outro evento.

### Consulta de pagamentos

- `GET /api/v1/payments/{id}` — retorna um pagamento por UUID.
- `GET /api/v1/payments` — retorna todos os pagamentos.
- `GET /api/v1/payments?status=APPROVED` — filtra por status.

---

## Estrutura de Pacotes

```
src/main/java/payflow/
├── PayflowApplication.java          # Ponto de entrada (@SpringBootApplication)
├── config/
│   └── KafkaTopicConfig.java        # Declara o tópico "payments" (3 partições, 1 réplica)
├── controller/
│   └── PaymentController.java       # Endpoints REST
├── dto/
│   ├── PaymentEventMessage.java     # Mensagem trafegada no Kafka (JSON)
│   ├── request/
│   │   └── PaymentRequest.java      # Payload de entrada com validações
│   └── response/
│       └── PaymentResponse.java     # Payload de saída da API
├── entity/
│   ├── Payment.java                 # Entidade JPA da tabela `payments`
│   └── PaymentEvent.java            # Entidade JPA da tabela `payment_events`
├── enums/
│   └── PaymentStatus.java           # PENDING, PROCESSING, APPROVED, FAILED, CANCELLED
├── exception/
│   ├── GlobalExceptionHandler.java  # @RestControllerAdvice: trata 404, 400 e erros gerais
│   └── PaymentNotFoundException.java
├── kafka/
│   ├── consumer/
│   │   └── PaymentConsumer.java     # @KafkaListener: processa eventos do tópico
│   └── producer/
│       └── PaymentProducer.java     # KafkaTemplate: publica eventos no tópico
├── repository/
│   ├── PaymentRepository.java       # findByPayerId, findByStatus
│   └── PaymentEventRepository.java  # findByPaymentIdOrderByOccurredAtAsc
└── service/
    └── PaymentService.java          # Orquestra criação, consulta e atualização de status
```

---

## Banco de Dados

### Migrations Flyway

O Flyway escaneia `classpath:db/migration` na inicialização e executa os scripts SQL em ordem crescente de versão. Uma vez executado, um script nunca é alterado — para mudar o schema, cria-se uma nova migration.

| Arquivo | Descrição |
|---|---|
| `V1__create_payments.sql` | Cria o tipo ENUM `payment_status` e a tabela `payments` com seus índices. |
| `V2__create_payment_events.sql` | Cria a tabela `payment_events` com FK para `payments`. |

---

### Tabela: `payments`

Armazena cada transação de pagamento.

| Coluna | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `UUID` | PK, `DEFAULT gen_random_uuid()` | Identificador único do pagamento. |
| `payer_id` | `VARCHAR(100)` | NOT NULL | Identificador de quem está pagando. |
| `payee_id` | `VARCHAR(100)` | NOT NULL | Identificador de quem recebe o pagamento. |
| `amount` | `NUMERIC(15,2)` | NOT NULL, `CHECK (amount > 0)` | Valor da transação. Máximo de 13 dígitos inteiros e 2 decimais. |
| `currency` | `VARCHAR(3)` | NOT NULL, `DEFAULT 'BRL'` | Código ISO 4217 da moeda (ex: BRL, USD). |
| `status` | `payment_status` | NOT NULL, `DEFAULT 'PENDING'` | Status atual da transação (ENUM nativo do PostgreSQL). |
| `description` | `VARCHAR(255)` | — | Descrição opcional da transação. |
| `created_at` | `TIMESTAMP` | NOT NULL, `DEFAULT NOW()` | Data/hora de criação (imutável). |
| `updated_at` | `TIMESTAMP` | NOT NULL, `DEFAULT NOW()` | Data/hora da última atualização. |

**Índices:**
- `idx_payments_payer` — em `payer_id` (consultas por pagador).
- `idx_payments_status` — em `status` (filtragem por status).
- `idx_payments_created` — em `created_at DESC` (listagem cronológica).

---

### Tabela: `payment_events`

Registra o histórico completo de transições de status de cada pagamento. Funciona como um log de auditoria imutável.

| Coluna | Tipo | Restrições | Descrição |
|---|---|---|---|
| `id` | `UUID` | PK, `DEFAULT gen_random_uuid()` | Identificador único do evento. |
| `payment_id` | `UUID` | NOT NULL, FK → `payments(id)` | Referência ao pagamento relacionado. |
| `event_type` | `VARCHAR(50)` | NOT NULL | Tipo do evento: `PAYMENT_CREATED` ou `STATUS_UPDATED`. |
| `old_status` | `payment_status` | — | Status anterior (NULL na criação do pagamento). |
| `new_status` | `payment_status` | NOT NULL | Status resultante após o evento. |
| `metadata` | `TEXT` | — | Campo livre para dados adicionais em formato texto/JSON. |
| `occurred_at` | `TIMESTAMP` | NOT NULL, `DEFAULT NOW()` | Data/hora em que o evento ocorreu (imutável). |

**Índices:**
- `idx_payment_events_payment_id` — em `payment_id` (busca de histórico de um pagamento).

---

### Tipo ENUM: `payment_status`

Tipo nativo PostgreSQL que restringe os valores válidos de status:

| Valor | Significado |
|---|---|
| `PENDING` | Pagamento criado, aguardando processamento. |
| `PROCESSING` | Evento recebido pelo consumer Kafka, processamento em andamento. |
| `APPROVED` | Transação aprovada pelo gateway simulado. |
| `FAILED` | Transação reprovada pelo gateway simulado. |
| `CANCELLED` | Reservado para cancelamentos futuros. |

---

### Diagrama de Relacionamento

```
payments                     payment_events
─────────────────────        ─────────────────────────
id (PK)          ◄──────────  payment_id (FK)
payer_id                      id (PK)
payee_id                      event_type
amount                        old_status
currency                      new_status
status                        metadata
description                   occurred_at
created_at
updated_at
```

---

## Configuração do Kafka

O tópico `payments` é declarado programaticamente em `KafkaTopicConfig` e criado automaticamente pelo broker na inicialização:

- **Partições:** 3 (permite paralelismo de consumo)
- **Réplicas:** 1 (suficiente para ambiente local)
- **Group ID do consumer:** `payflow-group`
- **Auto offset reset:** `earliest` (processa mensagens desde o início caso não haja offset salvo)
- **Serialização:** `String` em ambas as direções; o `PaymentEventMessage` é serializado/desserializado manualmente via `ObjectMapper` (Jackson).

---

## Tratamento de Erros

O `GlobalExceptionHandler` centraliza as respostas de erro da API:

| Exceção | HTTP | Resposta |
|---|---|---|
| `PaymentNotFoundException` | `404 Not Found` | `{ timestamp, message }` |
| `MethodArgumentNotValidException` | `400 Bad Request` | `{ timestamp, message, fields: { campo: motivo } }` |
| `Exception` (genérica) | `500 Internal Server Error` | `{ timestamp, message }` |

---

## Infraestrutura Local (Docker Compose)

Todos os serviços de infraestrutura sobem com um único comando:

```bash
docker compose up -d
```

| Serviço | Imagem | Porta | Função |
|---|---|---|---|
| `payflow-postgres` | `postgres:16-alpine` | `5432` | Banco de dados principal |
| `payflow-zookeeper` | `confluentinc/cp-zookeeper:7.6.0` | `2181` | Coordenação do Kafka |
| `payflow-kafka` | `confluentinc/cp-kafka:7.6.0` | `9092` | Message broker |

Todos os containers compartilham a rede `payflow-network` (bridge) e possuem healthchecks configurados.

---

## Como Executar

### Pré-requisitos

- Java 17+
- Docker e Docker Compose

### Passos

```bash
# 1. Subir a infraestrutura
docker compose up -d

# 2. Aguardar os healthchecks (postgres e kafka ficarem healthy)

# 3. Executar a aplicação
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

### Exemplos de Requisição

```bash
# Criar um pagamento
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "payerId": "user-123",
    "payeeId": "merchant-456",
    "amount": 150.00,
    "currency": "BRL",
    "description": "Compra loja X"
  }'

# Consultar por ID
curl http://localhost:8080/api/v1/payments/{id}

# Listar todos
curl http://localhost:8080/api/v1/payments

# Filtrar por status
curl "http://localhost:8080/api/v1/payments?status=APPROVED"
```
