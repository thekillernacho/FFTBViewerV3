# Twitch Chat Reader - Java Application

## Overview

A modern Spring Boot application for real-time monitoring of Twitch chat messages from specific channels, primarily 'fftbattleground'. It features both console output and a web interface with WebSocket support to display the last 50 chat messages. The application also includes a robust, authenticated Twitch integration, a PostgreSQL-backed playlist system with search, sorting, and pagination for 38,000+ songs, and a real-time song play tracking system that records occurrences from chat announcements. The project emphasizes a component-based architecture, responsive design, and efficient data handling for a seamless user experience.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

**UI/UX Decisions:**
The application features a modern web interface with a Twitch-inspired dark theme and responsive design, adapting to mobile, tablet, and desktop views. It includes real-time display of chat messages and current track information with progress bars. User interaction for search functionality requires explicit action (Enter key or search button) rather than automatic searching.

**Technical Implementations:**
- **Real-time Chat:** Utilizes `twitch4j` for Twitch API integration and WebSocket (SockJS + STOMP) for live message streaming to the frontend.
- **Playlist Management:** A PostgreSQL database stores playlist data, synchronized with an external XML source. Features include search, pagination (with 'First' and 'Last' buttons), and sorting.
- **Song Play Tracking:** Monitors Twitch chat for song announcements, records individual `TrackPlay` events in the database, and dynamically calculates track statistics. Deduplication logic prevents duplicate entries within a song's duration.
- **Current Track Display:** Uses Redis for persistent storage of the current track information, enabling dynamic timing calculations and display on the frontend via an API endpoint and WebSocket updates.
- **Frontend Build:** A Webpack-based TypeScript frontend is built separately and served by the Spring Boot backend. Critical: Frontend changes require a Java rebuild to copy resources.
- **Profile-Based Configuration:** Spring profiles (`dev`, `prod`) control behavior, such as scheduled jobs and database write access, ensuring different functionalities across environments.
- **Error Handling:** Robust error handling includes null safety checks, graceful fallback for missing dependencies, and clear, environment-specific logging.

**Feature Specifications:**
- Real-time chat message display with timestamps (console + web).
- Frontend-managed 50-message display limit for chat.
- Auto-reconnection for Twitch client on connection loss.
- Graceful shutdown mechanism.
- Comprehensive song playlist with search, sort, and pagination.
- Real-time current track display with progress bar and overtime indication.
- Environment-based write protection for Redis keystore (production-only writes).
- Timezone conversion for frontend timestamps to display local time.

**System Design Choices:**
- **Technology Stack:** Java 11 (OpenJDK), Spring Boot 2.7.18 (Web + WebSocket), Maven 3.9.4, `twitch4j`, Thymeleaf (for initial setup), PostgreSQL, Redis.
- **Design Patterns:** Spring Boot's dependency injection, component-based architecture, event-driven chat handling, and configuration management via properties.
- **Data Flow:** Twitch chat events are captured by the backend, processed by event handlers (including song play detection), and broadcasted via WebSockets to the frontend. Playlist data is managed in PostgreSQL, with current track data cached in Redis.

## Recent Changes (December 31, 2025)

- **Fixed Playlist Sync Thread Deadlock:** Implemented dedicated `ThreadPoolTaskScheduler` (4 threads, "PlaylistSync-" prefix) via `ProductionSchedulingConfig` to prevent MessageBroker thread blocking.
- **Optimized Duration Discrepancy Check:** Replaced N+1 query pattern (38,000+ individual queries) with batch query approach using `findSongsWithProblematicDurations()`.
- **Underscore Preservation:** Removed underscore-stripping code from `DurationSyncUtil.java` to preserve song titles like "Astro Boy - EV_ADX_FILE003" correctly.
- **Database Cleanup:** Old "space" version songs deleted and replaced with correct "underscore" versions from XML feed.
- **Total Songs:** 38,836 songs in database including 23 with underscores in titles.

## External Dependencies

- **Core Frameworks:**
    - `spring-boot-starter`
    - `spring-boot-starter-web`
    - `spring-boot-starter-websocket`
    - `spring-boot-starter-thymeleaf`
    - `spring-boot-configuration-processor`
    - `spring-boot-starter-data-jpa` (for PostgreSQL)
    - `spring-boot-starter-data-redis`
- **Twitch Integration:** `twitch4j-chat` (1.19.0)
- **Database:** PostgreSQL
- **Caching/Current Track Storage:** Redis (Redis Cloud integration)
- **Frontend:**
    - React (with TypeScript)
    - Webpack (for bundling)
    - SockJS + STOMP (for WebSocket communication)
- **Build Tools:** Maven 3.9.4