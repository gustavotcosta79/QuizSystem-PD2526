# QuizSystem - Distributed System with Fault Tolerance

## 📝 Overview
This system is a complete solution for real-time quiz management, developed for the **Distributed Programming** unit (2025/2026). The project focuses on creating a resilient infrastructure capable of maintaining service availability even in the event of critical failures in one of the cluster nodes.

## 🏗️ Technical Architecture
The system was structured modularly to ensure a clear separation of concerns:

* **`_client`**: Handles user interface and communication logic.
* **`_server`**: Manages business logic, SQLite persistence, and synchronization.
* **`_directory_service`**: Coordinates dynamic registration of active servers.
* **`_common`**: Contains shared data models and **DTOs** (Data Transfer Objects) transmitted via serialization.

### Communication & Resilience Mechanisms
* **Primary-Backup & Failover**: The system automatically detects a primary server failure. The client requests a new node from the Directory Service and performs an **automatic re-login**, ensuring a seamless user experience.
* **Multicast Synchronization**: Database updates (SQL queries) are propagated in real-time to backup servers via the multicast address `230.30.30.30:3030`, using a versioning system to guarantee consistency.
* **Heartbeat Service**: Servers maintain their active status by sending UDP heartbeats to the Directory Service and via Multicast.
* **Graceful Shutdown**: Implementation of **Shutdown Hooks** to release network resources and notify the Directory Service for immediate removal, avoiding unnecessary timeouts.

## 🚀 Key Features
* **Instructor Profile**: Create, edit, and delete questions, monitor statistics, and export results to **CSV**
* **Student Profile**: Answer questions via access codes and consult personal performance history.
* **Security**: Authentication secured with **SHA-256 hashing** for passwords and registration codes.
* **Interfaces**: Evolved from a robust CLI to a Graphical User Interface (**GUI**) developed in **JavaFX**.
* **Asynchronous Notifications**: Push system that updates clients in real-time regarding question status changes.

## 🛠️ Tech Stack
* **Language:** Java 21+.
* **UI Framework:** JavaFX.
* **Database:** SQLite with JDBC.
* **Protocols:** TCP (Object Streams/DTOs), UDP (Unicast and Multicast).

---

### 📚 API & RMI Design (Planning)
Beyond the core implementation, the project includes architectural designs for future expansion:
* **RMI Interface**: Remote methods defined for student operations.
* **REST API**: Comprehensive endpoint table for Web/Mobile integration using JSON.

---
*Developed by Gustavo Costa and Duarte Santos as part of the Distributed Programming course (2025/2026).* 
