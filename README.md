# Online Event Management System

A full-stack web application built with **Java 17, Spring Boot 3, Spring Security, Spring Data JPA, Thymeleaf and H2**.
Event organizers create and manage events, attendees register and buy tickets, and administrators oversee everything.

## Roles & features

| Role | Features |
|------|----------|
| **Admin** | User management (create / edit / enable-disable / delete, roles), event approvals (approve / reject / delete), system settings (site name, open sign-ups, auto-approve events), statistics charts, live system-activity feed |
| **Event Organizer** | Create / edit / delete events, ticket types (price, quantity, sold / available), send updates to registered attendees, charts (registrations vs tickets sold, attendees by city), upcoming-events list |
| **Attendee** | Browse & search events, register / cancel, buy tickets through Razorpay Checkout (Route onboarding required), view purchased tickets, receive organizer updates, registration history, profile management |

## Tech stack
- Backend: Spring Boot, Spring MVC, Spring Security (form login, BCrypt, role-based access), Spring Data JPA / Hibernate, Bean Validation
- Frontend: Thymeleaf server-side templates, custom CSS, Chart.js (CDN)
- Database: H2 file database by default (`./data/eventdb`), PostgreSQL/MySQL drivers available; configure JDBC environment variables

## Run it
Requirements: **JDK 17+** and **Maven 3.8+**

```bash
mvn spring-boot:run
```
Open http://localhost:8080

### Demo accounts (created on first start)
| Role | Email | Password |
|------|-------|----------|
| Admin | admin@eventhub.com | admin123 |
| Organizer | organizer@eventhub.com | organizer123 |
| Attendee | attendee@eventhub.com | attendee123 |

Change these passwords (Admin → Users → Edit) before any real deployment.

## Typical flow to try
1. Log in as **organizer**, create an event and add ticket types. It shows as *PENDING*.
2. Log in as **admin**, open *Event Approvals* and approve it.
3. Log in as **attendee**, open the event, register and buy a ticket.
4. As organizer, open *Attendees* on the event and send an update; the attendee sees it under *Updates*.

## Using MySQL instead of H2
1. Set `DATABASE_URL` to your MySQL JDBC URL and set `DATABASE_USERNAME` / `DATABASE_PASSWORD`.
2. Ensure the MySQL server is reachable and its database user has the required schema permissions.

## Project structure
```
src/main/java/com/eventmgmt
├── config        SecurityConfig, DataInitializer (seed data)
├── controller    Public, Admin, Organizer, Attendee controllers
├── dto           Form objects with validation
├── model         JPA entities (User, Event, Ticket, Registration, TicketPurchase, EventMessage, Setting, ActivityLog)
├── repository    Spring Data repositories
└── service       Business logic (EventService, UserService, SettingService, ...)
src/main/resources
├── templates     Thymeleaf pages (admin/, organizer/, attendee/)
└── static/css    Stylesheet
```

## Notes
- Payment requires Razorpay credentials and approved Razorpay Route linked accounts; it is disabled by default.
- Chart.js is loaded from a CDN, so dashboards need internet access in the browser.


## Razorpay payments and organizer payouts

### UPI checkout

- Razorpay Checkout is configured to show a dedicated **Pay using UPI** block first, followed by other default payment methods supported by your account. This can include UPI apps and an order-specific UPI QR, cards, wallets and net banking depending on Razorpay account settings and customer device. Each checkout is created against a unique server-side Razorpay order and its server-calculated amount; when QR is offered by Razorpay Checkout, use that order-specific QR rather than a static personal QR.
- Enable UPI in the Razorpay Dashboard and test it using Razorpay test mode before going live. Razorpay checkout verifies the signature and captured payment server-side before issuing tickets. A separate direct UPI ID option is also available for organizers who configure their own UPI ID.


The code now includes a Razorpay Standard Checkout integration and a Razorpay Route transfer request. **It does not become live until you supply valid credentials and Razorpay enables Route for your business.** Keep `RAZORPAY_ROUTE_ENABLED=false` until your Razorpay account, linked accounts and transfer flow have been approved and tested. The project must not be used to collect live money before end-to-end verification.

### Direct UPI ID payments (manual verification)

- Organizers set their receiving UPI ID under **Organizer Dashboard → Payment / Route settings**. Attendees can choose **Pay directly by UPI ID**, open a UPI app, pay the exact amount, and submit the transaction reference/UTR.
- A submitted UTR is not proof of payment. The order remains in review and no ticket purchase record is created until the event's organizer verifies the exact incoming credit in their own bank statement or UPI app and explicitly approves it under **Organizer Dashboard → Verify direct UPI payments**.
- Rejected submissions release the reserved inventory. Unsubmitted direct-UPI requests expire after 15 minutes; submitted requests stay pending until the organizer reviews them.
- This is a manual verification workflow, not an automatic bank/UPI API integration. For high-volume or production use, prefer a payment gateway that provides signed server-side payment confirmation and a supported refund/reconciliation process. Never ask attendees for their UPI PIN, OTP, or banking password.

### Environment variables

Create a local `.env` for your own terminal or configure these in your hosting provider's secret/environment settings. Do not commit actual values.

| Variable | Purpose |
|---|---|
| `RAZORPAY_KEY_ID` | Razorpay test key ID initially; live key ID only after launch approval |
| `RAZORPAY_KEY_SECRET` | Server-side Razorpay API and checkout signature secret |
| `RAZORPAY_WEBHOOK_SECRET` | Secret configured for the Razorpay webhook endpoint |
| `RAZORPAY_ROUTE_ENABLED` | Must remain `false` until Route is approved and tested; set `true` only when ready |
| `DATABASE_URL` | JDBC database URL; defaults to a local H2 file database |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Database credentials |
| `JPA_DDL_AUTO` | Defaults to `update` for this starter; use managed schema migrations/`validate` for production after creating migrations |
| `PAYMENTS_EXPIRY_CHECK_MS` | Pending checkout cleanup interval, default 60000 ms |

### Organizer onboarding

1. Ask Razorpay to enable Route and onboard your platform and organizers as required by its current policies.
2. Obtain the organizer's Route linked-account ID from the Razorpay dashboard.
3. The organizer enters that ID under **Organizer Dashboard → Payment / Route settings**.
4. An administrator must independently verify the account ID and onboarding in Razorpay before approving it at **Admin Dashboard → Razorpay Route accounts**. Never approve an account merely because an organizer submitted an ID.
5. The code sends the ticket total as a Route transfer to that linked account. The organizer's actual settlement, fees, holds, refunds and timing are controlled by Razorpay and the approved Route configuration.

### Payment flow and safety

- The server calculates the amount from the ticket record; it does not trust a browser-submitted amount.
- The server creates a unique Razorpay order with a Route transfer and a 15-minute expiry. The browser opens Razorpay Checkout for that order; if the checkout offers UPI QR, it is the QR/payment flow associated with that unique order and amount. Configure automatic payment capture in the Razorpay dashboard; the application issues tickets only after Razorpay reports the payment as captured.
- Ticket inventory is reserved while checkout is pending.
- The checkout callback signature is verified on the server, and the payment is fetched from Razorpay to confirm order ID, captured status, amount and currency before issuing a ticket.
- A signed `payment.captured` webhook is also supported at `POST /webhooks/razorpay`. Configure that exact URL in the Razorpay dashboard with the same `RAZORPAY_WEBHOOK_SECRET`.
- The older simulated purchase path is disabled. A purchase record is only created after server verification.
- Payment capture, transfers, refunds, chargebacks and payouts still require real account-level testing; do not assume a successful local build proves money movement works.

### Local run on Windows / VS Code

Requirements: JDK 17+ and Maven 3.8+. Open the `event-management-system` folder in VS Code, then choose **Terminal → New Terminal**. Verify the terminal is in the folder that contains `pom.xml`:

```powershell
dir
java -version
mvn -version
```

Run the test suite and start the website:

```powershell
mvn clean test
mvn spring-boot:run
```

Open `http://localhost:8080` in your browser. Keep the terminal open while using the site; press `Ctrl+C` to stop it. If PowerShell says `mvn` is not recognized, install Maven or configure Maven in PATH. The archive does not include Maven Wrapper scripts.

### Configure Razorpay test credentials in PowerShell

Use your **test-mode** keys from your Razorpay Dashboard. Replace the placeholders; these commands apply only to the current terminal session and are not committed to Git:

```powershell
$env:RAZORPAY_KEY_ID = "rzp_test_your_key_id"
$env:RAZORPAY_KEY_SECRET = "your_test_key_secret"
$env:RAZORPAY_WEBHOOK_SECRET = "your_webhook_secret"
$env:RAZORPAY_ROUTE_ENABLED = "false"
```

This starter currently requires Razorpay Route to be enabled and the organizer to have an independently verified linked account before checkout can be created. Keep Route disabled until Razorpay has approved your platform and Route setup; merely setting the environment variable is not approval. With Route disabled or credentials absent, the website can still run, but paid checkout is intentionally unavailable. Configure the webhook on a public HTTPS endpoint for end-to-end webhook tests; `localhost` alone cannot receive Razorpay webhooks without a secure tunnel.

### Build commands

From the project directory:

```bash
mvn clean test
mvn spring-boot:run
```



```bash
mvn clean test
mvn spring-boot:run
```

Without Razorpay credentials, the site can run but checkout is disabled and cannot create real orders. For payment integration testing, use Razorpay **test-mode** credentials and an approved/test Route setup. Never use live secrets in source code or in a public GitHub repository.

### Deployment

The included `Dockerfile` builds a runnable Spring Boot JAR. Use a host that supports Java 17 or Docker, attach a persistent PostgreSQL database, and configure the environment variables above in the host dashboard. Set `DATABASE_URL` to a JDBC URL such as `jdbc:postgresql://HOST:5432/DBNAME`; set the separate database username and password. Do not rely on the container filesystem for production database persistence. Configure HTTPS and the webhook URL on the public domain.

Before going live, run successful, failed, cancelled and duplicate-checkout tests; verify the webhook signature; confirm captured payments and Route transfers in Razorpay Dashboard; test pending-order expiry and stock limits; and have an accountant/legal adviser review organizer settlements, platform fees, GST, refunds and marketplace obligations.
