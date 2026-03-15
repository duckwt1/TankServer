# TankServer – Online Tank Arena (Server)

TankServer is the backend server for the TankVerse multiplayer game.

It manages player connections, rooms, real-time gameplay synchronization,
and database storage.

The server communicates with game clients using **TCP and UDP sockets**.

---

## Server Responsibilities

The server acts as the central authority for the game:

- Manage player authentication
- Handle game rooms
- Synchronize gameplay
- Process game events
- Store player data in database

---

## Technologies Used

- Java
- Socket Networking (TCP / UDP)
- MySQL
- Multithreading
- Client–Server Architecture

---

## Server Features

### Account Management

- Player login
- Player registration
- Secure account verification

### Room Management

- Create room
- Join room
- Room lobby system
- Start match

### Real-time Game Sync

Server synchronizes:

- Tank position
- Bullet movement
- Collision detection
- Player status

UDP is used for **low-latency gameplay updates**.

---

## Database

The server uses MySQL to store:

- Player accounts
- Tanks
- Inventory
- Match results
- Player statistics

---

## Server Admin Panel

The server includes a monitoring interface that allows administrators to:

- View connected players
- Monitor active rooms
- View server logs
- Manage player accounts

---

## How to Run

### Requirements

- Java JDK 17+
- MySQL

### Steps

1. Clone repository

2. Configure database connection

3. Run the server

4. Start TankVerse client

---

## System Architecture

---

## Author

Pham Ngoc Duc
Tran Quan Viet 
VKU – Vietnam Korea University of ICT
