# QuizSystem - Distributed System with Fault Tolerance

## 📝 Overview
[cite_start]This system is a complete solution for real-time quiz management, developed for the **Distributed Programming** unit (2025/2026)[cite: 5]. [cite_start]The project focuses on creating a resilient infrastructure capable of maintaining service availability even in the event of critical failures in one of the cluster nodes[cite: 71, 159].

## 🏗️ Technical Architecture
[cite_start]The system was structured modularly to ensure a clear separation of concerns[cite: 254, 290]:

* [cite_start]**`_client`**: Handles user interface and communication logic[cite: 170, 172].
* **`_server`**: Manages business logic, SQLite persistence, and synchronization[cite: 10, 13, 257].
* **`_directory_service`**: Coordinates dynamic registration of active servers[cite: 11, 146].
* [cite_start]**`_common`**: Contains shared data models and **DTOs** (Data Transfer Objects) transmitted via serialization[cite: 291, 794, 797].

### Communication & Resilience Mechanisms
* **Primary-Backup & Failover**: The system automatically detects a primary server failure[cite: 122, 654]. The client requests a new node from the Directory Service and performs an **automatic re-login**, ensuring a seamless user experience[cite: 159, 160, 680].
* [cite_start]**Multicast Synchronization**: Database updates (SQL queries) are propagated in real-time to backup servers via the multicast address `230.30.30.30:3030`, using a versioning system to guarantee consistency[cite: 125, 130, 137, 527].
* [cite_start]**Heartbeat Service**: Servers maintain their active status by sending UDP heartbeats to the Directory Service and via Multicast[cite: 125, 341, 527].
* **Graceful Shutdown**: Implementation of **Shutdown Hooks** to release network resources and notify the Directory Service for immediate removal, avoiding unnecessary timeouts[cite: 144, 829, 833].

## 🚀 Key Features
* [cite_start]**Instructor Profile**: Create, edit, and delete questions, monitor statistics, and export results to **CSV**[cite: 18, 21, 31, 605].
* [cite_start]**Student Profile**: Answer questions via access codes and consult personal performance history[cite: 48, 49, 50, 761].
* **Security**: Authentication secured with **SHA-256 hashing** for passwords and registration codes[cite: 16, 462, 464].
* [cite_start]**Interfaces**: Evolved from a robust CLI to a Graphical User Interface (**GUI**) developed in **JavaFX**[cite: 195, 269, 723].
* [cite_start]**Asynchronous Notifications**: Push system that updates clients in real-time regarding question status changes[cite: 62, 143, 621, 628].

## 🛠️ Tech Stack
* **Language:** Java 21+[cite: 12].
* [cite_start]**UI Framework:** JavaFX[cite: 270, 723].
* [cite_start]**Database:** SQLite with JDBC[cite: 13, 174, 463].
* **Protocols:** TCP (Object Streams/DTOs), UDP (Unicast and Multicast)[cite: 74, 86, 125, 797].

---

### 📚 API & RMI Design (Planning)
Beyond the core implementation, the project includes architectural designs for future expansion[cite: 204]:
* [cite_start]**RMI Interface**: Remote methods defined for student operations[cite: 207, 868].
* [cite_start]**REST API**: Comprehensive endpoint table for Web/Mobile integration using JSON[cite: 205, 881, 886].

---
[cite_start]*Developed by Gustavo Costa and Duarte Santos as part of the Distributed Programming course (2025/2026).* [cite: 243]
