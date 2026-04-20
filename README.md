# 🚀 SQLOrb

**SQLOrb** is a Java-based SQL query validation tool that analyzes and verifies the syntax of SQL queries using a modular parsing approach.

---

## 🧠 Overview

SQLOrb simulates core concepts of compiler design by breaking down SQL queries into tokens and validating their structure. It supports multiple query types and provides real-time syntax feedback.

---

## ✨ Features

- 🔍 Tokenization using a custom Lexer  
- 🧩 Modular parsing for:
  - DQL (SELECT)
  - DML (INSERT, UPDATE, DELETE)
  - DDL (CREATE, DROP, ALTER)
- ⚙️ Structured validation of SQL syntax  
- 🌐 Backend server for real-time query validation  
- 🏗️ Clean architecture (Lexer → Parser → AST)

---

## 🛠️ Tech Stack

- **Language:** Java  
- **Concepts Used:**  
  - Lexical Analysis  
  - Parsing  
  - Abstract Syntax Tree (AST)  
  - Modular Design  

---

## 📂 Project Structure
src/
└── com/sqlorb/
├── lexer/
├── parser/
├── ast/
├── token/
├── server/
└── Server.java


---

## ▶️ How to Run

### 1. Compile the project

``bash
javac -d bin src/com/sqlorb/**/*.java
(If this doesn't work in PowerShell, use:)
javac -d bin src/com/sqlorb/*.java src/com/sqlorb/*/*.java src/com/sqlorb/*/*/*.java

### 2. Run the server
cd bin
java com.sqlorb.Server

### 3. Access the application
Open your browser and go to:
http://localhost:8080

### 🧪 Example Queries

✅ Valid

SELECT name, age FROM users;

INSERT INTO users (id, name) VALUES (1, 'John');

❌ Invalid

SELECT name age FROM users;

INSERT INTO users VALUES ();

### 🎯 Purpose

This project was built to understand how SQL parsing works internally without relying on external libraries, focusing on core concepts like tokenization and syntax validation.

### 📌 Future Improvements
Improved WHERE clause parsing
Support for more SQL features (JOIN, GROUP BY, HAVING)
Enhanced error messages
UI improvements for better user experience

### 👩‍💻 Author
Shreya Deshmukh
