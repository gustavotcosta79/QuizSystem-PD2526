# PD-25-26-TP-G11
QuizSystem - Distributed System with Fault Tolerance
📝 Overview
This system is a complete solution for real-time quiz management, developed for the Distributed Programming unit (2025/2026). The project focuses on creating a resilient infrastructure capable of maintaining service availability even in the event of critical failures in one of the cluster nodes.
+3

🏗️ Technical Architecture
The system was structured modularly to ensure a clear separation of concerns:
+1

_client: Handles user interface and communication logic.
+1

_server: Manages business logic, SQLite persistence, and synchronization.
+4

_directory_service: Coordinates dynamic registration of active servers.
+2

_common: Contains shared data models and DTOs (Data Transfer Objects) transmitted via serialization.
+2

Communication & Resilience Mechanisms

Primary-Backup & Failover: The system automatically detects a primary server failure. The client requests a new node from the Directory Service and performs an automatic re-login, ensuring a seamless user experience.
+3

Multicast Synchronization: Database updates (SQL queries) are propagated in real-time to backup servers via the multicast address 230.30.30.30:3030, using a versioning system to guarantee consistency.
+3

Heartbeat Service: Servers maintain their active status by sending UDP heartbeats to the Directory Service.
+1

Graceful Shutdown: Implementation of Shutdown Hooks to release network resources and notify the Directory Service for immediate removal, avoiding unnecessary timeouts.
+2

🚀 Key Features
Instructor Profile: Create, edit, and delete questions, monitor statistics, and export results to CSV.
+3

Student Profile: Answer questions via access codes and consult personal performance history.
+2

Security: Authentication secured with SHA-256 hashing for passwords and registration codes.
+2

Interfaces: Evolved from a robust CLI to a Graphical User Interface (GUI) developed in JavaFX.
+2

Asynchronous Notifications: Push system that updates clients in real-time regarding question status changes.
+2

🛠️ Tech Stack
Language: Java 21+.

UI Framework: JavaFX.
+1

Database: SQLite with JDBC.
+2

Protocols: TCP (Object Streams/DTOs), UDP (Unicast and Multicast).
+2

📚 API & RMI Design (Software Planning)

Beyond the core implementation, the project includes architectural designs for future expansion:

RMI Interface: Remote methods defined for student operations.
+1

REST API: Comprehensive endpoint table for Web/Mobile integration using JSON.
