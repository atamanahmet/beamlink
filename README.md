# Beamlink - Local File Sharing with Server-Managed Peers

Beamlink is a simple agent-based network for sending files locally. Agents communicate directly and Nexus coordinates the network.

## Prerequisites

- **Java 17+ JVM** - [Amazon Corretto](https://aws.amazon.com/corretto/) recommended

## How it works

**Agent**

- Runs with launcher.bat

- Registers with Nexus and gets an auth token

- Stores auth token locally (file-based, no db)

- Transfers files directly to other agents and nexus over HTTP

- Keeps logs locally and syncs to Nexus when online

- Works offline using last known peer list

- Agent UI uses HTTP-only JWT cookie

**Nexus**

- Runs with launcher.bat

- Tracks agents, peer lists, logs, and push updates

- Receives files like agents

- Admin UI uses HTTP-only JWT cookie

## Installation

**Agent**

- Install [Java 17+ JVM](https://aws.amazon.com/corretto/)

- Download adn extract **beamlink-agent-0.0.1-pre1.zip**

- Run **launcher.bat**

- Open Agent UI in browser

**Nexus**

- Install [Java 17+ JVM](https://aws.amazon.com/corretto/)

- Download and extract **beamlink-nexus-0.0.1-pre1.zip**

- Run **launcher.bat**

- Open Nexus UI in browser

## Configuration

Configuration files are located at `config/application.yaml` for both Agent and Nexus.

**Nexus**

- `server.port` - port Nexus runs on
- `spring.datasource.username` / `password` - database credentials
- `nexus.jwt.secret` - leave `auto` to generate, or replace with your own
- `nexus.cors.static-origins` - set to your Nexus IP and port for UI access
- `nexus.admin.username` / `password` - Admin UI login credentials

**Agent**

- `server.port` - port Agent runs on
- `agent.nexus.url` - set to your Nexus IP and port if you changed
- `agent.name` - initial display name of the agent on the network
- `agent.ui.username` / `password` - Agent UI login credentials
- `agent.ui.jwt-secret` - leave `auto` to generate or replace with your own

## Notes

- Early version (v0.0.1) - expect bugs.

- Windows-only for now

- Built for local networks

- HTTPS recommended if exposed to outside of LAN
